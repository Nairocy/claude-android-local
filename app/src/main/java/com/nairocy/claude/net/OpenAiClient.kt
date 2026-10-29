package com.nairocy.claude.net

import com.nairocy.claude.data.ChatMessage
import com.nairocy.claude.data.ModelConfig
import com.nairocy.claude.data.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** 发请求用的一条消息：文本 + 可选的图片 data URL（多模态）。 */
data class WireMessage(
    val role: Role,
    val text: String,
    val imageDataUrls: List<String> = emptyList(),
)

sealed interface StreamEvent {
    data class Delta(val text: String) : StreamEvent
    data object Done : StreamEvent
    data class Failed(val message: String) : StreamEvent

    /** 流被中断 / 撞到长度上限，客户端正在自动续写（UI 可提示「正在接着写…」）。 */
    data class Continuing(val reason: String) : StreamEvent
}

/** OpenAI function-calling 的工具描述。 */
data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

/**
 * OpenAI 兼容客户端。一个 baseUrl + apiKey + model 就能接 DeepSeek / OpenRouter /
 * Ollama / 自建网关。支持 function calling（工具执行由调用方提供回调）。
 */
class OpenAiClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)   // SSE 长连接，不设读超时
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun endpoint(baseUrl: String, path: String): String =
        baseUrl.trimEnd('/').removeSuffix("/chat/completions") + path

    private fun messageJson(m: WireMessage): JsonObject = buildJsonObject {
        put("role", if (m.role == Role.USER) "user" else "assistant")
        if (m.imageDataUrls.isEmpty()) {
            put("content", m.text)
        } else {
            // 多模态：content 是数组，文本块 + 若干 image_url 块
            put("content", buildJsonArray {
                if (m.text.isNotBlank()) {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", m.text)
                    })
                }
                m.imageDataUrls.forEach { url ->
                    add(buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject { put("url", url) })
                    })
                }
            })
        }
    }

    private fun initialMessages(
        config: ModelConfig,
        history: List<WireMessage>,
        extraSystem: String,
    ): MutableList<JsonObject> {
        val list = mutableListOf<JsonObject>()
        val system = listOf(config.systemPrompt, extraSystem)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        if (system.isNotBlank()) {
            list += buildJsonObject {
                put("role", "system")
                put("content", system)
            }
        }
        history.forEach { list += messageJson(it) }
        return list
    }

    private fun toolsJson(tools: List<ToolSpec>): JsonArray = buildJsonArray {
        tools.forEach { t ->
            add(buildJsonObject {
                put("type", "function")
                put("function", buildJsonObject {
                    put("name", t.name)
                    put("description", t.description)
                    put("parameters", t.parameters)
                })
            })
        }
    }

    private fun requestBody(
        config: ModelConfig,
        messages: List<JsonObject>,
        tools: List<ToolSpec>,
        reasoningEffort: String,
    ): String = buildJsonObject {
        put("model", config.model)
        put("stream", true)
        put("temperature", config.temperature)
        put("messages", JsonArray(messages))
        // ⚠️ 必须显式给 max_tokens：DeepSeek 未指定时默认只有 8K（无思考）/ 64K（思考），
        // 长代码、长报告会在中途被 finish_reason=length 截断（上限 384K，不写不扣费）。
        put("max_tokens", MAX_OUTPUT_TOKENS)
        // DeepSeek 思考强度：low / high / max（xhigh 为兼容值，映射到 high）
        if (reasoningEffort.isNotBlank()) put("reasoning_effort", reasoningEffort)
        if (tools.isNotEmpty()) put("tools", toolsJson(tools))
    }.toString()

    private class ToolAcc {
        var id: String = ""
        var name: String = ""
        val args = StringBuilder()
    }

    /**
     * 流式对话；Delta 逐段吐字，最后一定有一个 Done 或 Failed。
     * 带 tools 时走 function calling：模型要工具就回执行回调、把结果塞回对话再来一轮（最多 4 轮）。
     */
    fun stream(
        config: ModelConfig,
        history: List<WireMessage>,
        extraSystem: String = "",
        tools: List<ToolSpec> = emptyList(),
        onToolCall: (suspend (name: String, arguments: String) -> String)? = null,
        reasoningEffort: String = "high",
        /** 工具循环上限：对话页默认 4，Code 页传 12（ls→read→write→grep… 一轮里会连着调很多次）。 */
        maxToolRounds: Int = MAX_TOOL_ROUNDS,
    ): Flow<StreamEvent> = flow {
        if (config.apiKey.isBlank()) {
            emit(StreamEvent.Failed("还没有填 API Key"))
            return@flow
        }
        val keyIssue = keyIssue(config.apiKey)
        if (keyIssue != null) {
            emit(StreamEvent.Failed(keyIssue))
            return@flow
        }

        val messages = initialMessages(config, history, extraSystem)
        var round = 0
        var continuation = 0
        var retriedEmpty = false

        while (true) {
            // 续写/重试轮用更短的思考：
            // - 续写轮（continuation > 0）：low，计划在上一轮已经想过，这轮只要把剩下的正文写出来；
            // - 空正文重试（retriedEmpty）：none，完全不想直接写 —— 代理的上游在 ~200s 处会切断长流，
            //   重思考的请求经常连一个字正文都没出就被砍，只有「不想」才来得及落在窗口内。
            val roundEffort = when {
                retriedEmpty -> RETRY_EFFORT
                continuation > 0 -> CONTINUE_EFFORT
                else -> reasoningEffort
            }
            val request = try {
                Request.Builder()
                    .url(endpoint(config.baseUrl, "/chat/completions"))
                    .header("Authorization", "Bearer ${config.apiKey.trim()}")
                    .header("Accept", "text/event-stream")
                    .post(requestBody(config, messages, tools, roundEffort).toRequestBody(JSON))
                    .build()
            } catch (e: Exception) {
                emit(StreamEvent.Failed("请求构建失败：" + (e.message ?: "未知错误")))
                return@flow
            }

            val response = try {
                client.newCall(request).execute()
            } catch (e: Exception) {
                emit(StreamEvent.Failed(e.message ?: "连接失败"))
                return@flow
            }

            var failed = false
            var finishReason: String? = null
            var sawDone = false
            val content = StringBuilder()
            val calls = mutableMapOf<Int, ToolAcc>()

            response.use { resp ->
                if (!resp.isSuccessful) {
                    val body = runCatching { resp.body?.string() }.getOrNull().orEmpty()
                    emit(StreamEvent.Failed(httpFailure(resp.code, body)))
                    failed = true
                    return@use
                }
                val source = resp.body?.source()
                if (source == null) {
                    emit(StreamEvent.Failed("响应为空"))
                    failed = true
                    return@use
                }
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank() || line.startsWith(":")) continue
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") {
                        sawDone = true
                        break
                    }
                    val obj = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                    val choices = obj["choices"]?.jsonArray ?: continue
                    if (choices.isEmpty()) continue
                    val choice = choices[0].jsonObject
                    // finish_reason 可能单独一帧（delta 为空对象），先读它再处理 delta，
                    // 否则「被 max_tokens 截断」这个信号会整帧被 continue 丢掉。
                    choice["finish_reason"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                        ?.let { finishReason = it }
                    val delta = choice["delta"]?.jsonObject ?: continue
                    delta["content"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let {
                        content.append(it)
                        emit(StreamEvent.Delta(it))
                    }
                    delta["tool_calls"]?.jsonArray?.forEach { el ->
                        val o = el.jsonObject
                        val idx = o["index"]?.jsonPrimitive?.intOrNull ?: 0
                        val acc = calls.getOrPut(idx) { ToolAcc() }
                        o["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { acc.id = it }
                        o["function"]?.jsonObject?.let { f ->
                            f["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { acc.name = it }
                            f["arguments"]?.jsonPrimitive?.contentOrNull?.let { acc.args.append(it) }
                        }
                    }
                }
            }
            if (failed) return@flow

            val handler = onToolCall
            // 模型要工具：把 assistant(tool_calls) + 每个 tool 结果塞回对话，再请求一轮
            if (calls.isNotEmpty() && handler != null && round < maxToolRounds) {
                val ordered = calls.toSortedMap().values.filter { it.name.isNotBlank() }
                if (ordered.isNotEmpty()) {
                    messages += buildJsonObject {
                        put("role", "assistant")
                        if (content.isNotBlank()) put("content", content.toString()) else put("content", JsonNull)
                        put("tool_calls", buildJsonArray {
                            ordered.forEach { c ->
                                add(buildJsonObject {
                                    put("id", c.id.ifBlank { "call_${System.nanoTime()}" })
                                    put("type", "function")
                                    put("function", buildJsonObject {
                                        put("name", c.name)
                                        put("arguments", c.args.toString().ifBlank { "{}" })
                                    })
                                })
                            }
                        })
                    }
                    ordered.forEach { c ->
                        val result = runCatching { handler(c.name, c.args.toString()) }
                            .getOrElse { "工具执行失败：${it.message ?: "未知错误"}" }
                        messages += buildJsonObject {
                            put("role", "tool")
                            put("tool_call_id", c.id.ifBlank { "call_${System.nanoTime()}" })
                            put("content", result.ifBlank { "（没有结果）" })
                        }
                    }
                    round++
                    continue
                }
            }

            // 被输出上限顶断（长代码 / 长文档），或者连接被上游/代理切断（没有 finish_reason、
            // 也没见到 [DONE]）：把已生成的部分当 assistant 续写。UI 侧只会看到文本接着往下长，
            // 不会停在半截代码上。
            // ⚠️ 云端代理的长流会在几分钟处被切断（实测约 40–50KB 后静默 EOF），
            // 所以「没有 finish_reason」也必须当成截断处理，否则长代码永远差一截。
            if (shouldContinue(finishReason, content, continuation, sawDone)) {
                emit(StreamEvent.Continuing(if (finishReason == "length") "length" else "interrupted"))
                messages += buildJsonObject {
                    put("role", "assistant")
                    put("content", content.toString())
                }
                messages += buildJsonObject {
                    put("role", "user")
                    put("content", CONTINUE_PROMPT)
                }
                continuation++
                continue
            }
            // 一个字都没出来就断了（思考阶段被切断）：加一条「直接写」的提示，原样重试一次，
            // 这一轮 effort 走 RETRY_EFFORT=none，别让界面空白。
            val cutBeforeAnyText = shouldRetryEmpty(finishReason, content, sawDone, retriedEmpty)
            if (cutBeforeAnyText) {
                retriedEmpty = true
                emit(StreamEvent.Continuing("retry"))
                messages += buildJsonObject {
                    put("role", "user")
                    put("content", RETRY_NUDGE)
                }
                continue
            }
            if (finishReason == "length") {
                emit(StreamEvent.Failed("输出太长，自动续写 $MAX_CONTINUATIONS 次后仍到上限"))
            } else if (cutBeforeAnyText) {
                emit(StreamEvent.Failed("模型思考阶段连接被中断，请重试"))
            } else if (finishReason == null && !sawDone && content.isNotBlank()) {
                emit(StreamEvent.Failed("连接中断，内容可能不完整（已续写 $continuation 次）"))
            }
            break
        }
        emit(StreamEvent.Done)
    }.flowOn(Dispatchers.IO)

    /** 保存配置时的连通性测试：非流式、只要 1 个 token。 */
    suspend fun test(config: ModelConfig): Result<String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("API Key 为空"))
        val keyIssue = keyIssue(config.apiKey)
        if (keyIssue != null) return@withContext Result.failure(IllegalStateException(keyIssue))
        val body = buildJsonObject {
            put("model", config.model)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "user"); put("content", "ping") })
            })
            put("max_tokens", 1)
            put("stream", false)
        }
        // 构建与执行都要兜住：非法 key / 非法 URL 会抛异常，不能让进程崩
        runCatching {
            val request = Request.Builder()
                .url(endpoint(config.baseUrl, "/chat/completions"))
                .header("Authorization", "Bearer ${config.apiKey.trim()}")
                .post(body.toString().toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}: " + runCatching { resp.body?.string() }.getOrNull().orEmpty().take(160))
                "连通正常 · HTTP ${resp.code}"
            }
        }
    }

    /**
     * 校验 key 是否能放进 HTTP header：只允许可见 ASCII（33..126）。
     * 中文输入法会把 key 变成联想词，OkHttp 会直接抛 IllegalArgumentException 崩掉整个 App。
     */

    /** 非流式一次问答：改写记忆这种短任务用，返回纯文本。 */
    suspend fun complete(config: ModelConfig, system: String, user: String): Result<String> =
        withContext(Dispatchers.IO) {
            if (config.apiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("API Key 为空"))
            }
            keyIssue(config.apiKey)?.let { return@withContext Result.failure(IllegalStateException(it)) }
            val body = buildJsonObject {
                put("model", config.model)
                put("messages", buildJsonArray {
                    add(buildJsonObject { put("role", "system"); put("content", system) })
                    add(buildJsonObject { put("role", "user"); put("content", user) })
                })
                put("temperature", 0.3)
                put("stream", false)
            }
            runCatching {
                withTimeout(60_000) {
                    val request = Request.Builder()
                        .url(endpoint(config.baseUrl, "/chat/completions"))
                        .header("Authorization", "Bearer ${config.apiKey.trim()}")
                        .post(body.toString().toRequestBody(JSON))
                        .build()
                    client.newCall(request).execute().use { resp ->
                        val text = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) error("HTTP ${resp.code}: " + text.take(160))
                        val obj = json.parseToJsonElement(text).jsonObject
                        obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                            ?: error("响应里没有 content")
                    }
                }
            }
        }

    private fun keyIssue(key: String): String? {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return "还没有填 API Key"
        if (trimmed.any { it.code !in 33..126 }) {
            return "API Key 含非法字符（多为输入法联想导致），请重新输入纯英文/数字"
        }
        return null
    }

    /**
     * 非 2xx 的提示文案。
     *
     * 云端代理固定回 `{"error":{"type":"...","message":"..."}}`：认识的 type 翻成人话
     * （用户看得懂才有意义），不认识的仍保留 HTTP 码 + body 原文，方便排查。
     */
    private fun httpFailure(code: Int, body: String): String {
        val type = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject
                ?.get("type")?.jsonPrimitive?.contentOrNull
        }.getOrNull().orEmpty()
        val friendly = when (type) {
            "quota_exceeded" ->
                if (body.contains("resets_at")) "今日额度已用完，明天 0 点重置" else "今日额度已用完"
            "effort_not_allowed" -> "该模型需要 Pro 或 Max 套餐"
            "unauthorized" -> "登录状态已失效，请重新登录"
            else -> null
        }
        if (friendly != null) return friendly
        val hint = if (body.isBlank()) "" else " · " + body.take(180)
        return "HTTP $code$hint"
    }

    companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val MAX_TOOL_ROUNDS = 4

        /**
         * 上一轮一个字正文都没收到就断了（思考阶段被切断）时，是否原样重试一次。
         * 重试轮会用 `none` 思考强度 + 一条「直接写」的提示，否则重思考请求在长流被切断前
         * 根本来不及输出正文。只重试一次，避免无限循环烧额度。
         */
        internal fun shouldRetryEmpty(
            finishReason: String?,
            content: CharSequence,
            sawDone: Boolean,
            retried: Boolean,
        ): Boolean = !retried && content.isBlank() && finishReason == null && !sawDone

        /**
         * 这一轮是否需要自动续写。
         *
         * 两种情况都算「被截断」：
         * 1. `finish_reason == "length"` —— 撞到输出上限；
         * 2. `finish_reason == null` 且没见到 `[DONE]` —— 长连接被上游/代理静默切断
         *    （实测云端代理的长流会在几分钟处 EOF，不给任何结束帧）。
         *
         * 纯函数放在 companion：单测直接调，不用 new OpenAiClient（构造会初始化 OkHttp，
         * 在 Paparazzi 的测试 classpath 下会缺 android/net/ssl/InvalidEchDataException）。
         */
        internal fun shouldContinue(
            finishReason: String?,
            content: CharSequence,
            continuation: Int,
            sawDone: Boolean = true,
        ): Boolean {
            if (content.isBlank() || continuation >= MAX_CONTINUATIONS) return false
            if (finishReason == "length") return true
            return finishReason == null && !sawDone
        }

        /**
         * 单次生成上限。DeepSeek 的默认值是 8K（不思考）/ 64K（思考），
         * 长代码很容易撞上默认值被截断；显式给 64K（不写不扣费）。
         */
        const val MAX_OUTPUT_TOKENS = 65_536

        /**
         * 被截断后最多自动续写几轮（防死循环）。
         * 代理的长流每次能跑出 40–50KB 正文，4 轮足够覆盖 200KB 级的单文件。
         */
        const val MAX_CONTINUATIONS = 4

        /** 续写轮用的思考强度：只要把剩下的正文写出来，不需要再长时间思考。 */
        const val CONTINUE_EFFORT = "low"

        /**
         * 空正文重试轮的思考强度：完全不想（DeepSeek 接受 `none`），立刻开始写正文。
         * 实测 `none` 是 0 思考字数、亚秒级出首字；重思考请求在代理 ~200s 的窗口里根本来不及出正文。
         * 仅用于「上一轮一个字都没收到」的补救轮，不影响用户选的模型档位。
         */
        const val RETRY_EFFORT = "none"

        /** 重试时追加的提示：模型常无视它继续长篇思考，但配合 none 能进一步压短首字时间。 */
        const val RETRY_NUDGE =
            "上一次回复在思考阶段就被中断了（连接超时）。请立即开始输出正文：不要写长篇计划、" +
                "不要复述需求，先直接给出完整结果。"

        const val CONTINUE_PROMPT =
            "你的上一条回复被中断了（输出到达长度上限，或连接被切断）。请从断开处继续输出剩余内容，" +
                "不要重复已经写过的部分，也不要添加任何解释或开场白。"
    }
}
