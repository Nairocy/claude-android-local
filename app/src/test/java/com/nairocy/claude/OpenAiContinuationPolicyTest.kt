package com.nairocy.claude

import com.nairocy.claude.net.OpenAiClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 长输出被 finish_reason=length 截断后的续写判定。
 *
 * 只测纯函数：不实例化 OpenAiClient（构造会建 OkHttpClient，而 Paparazzi 的测试
 * classpath 缺少 android/net/ssl/InvalidEchDataException，会直接 NoClassDefFoundError）。
 */
class OpenAiContinuationPolicyTest {

    @Test
    fun lengthTriggersContinuation() {
        assertTrue(OpenAiClient.shouldContinue("length", "hello", 0))
        assertTrue(OpenAiClient.shouldContinue("length", "hello", 2))
    }

    @Test
    fun stopsAfterMaxContinuations() {
        // MAX_CONTINUATIONS = 4 → 第 4 次之后不再续写，避免死循环
        assertFalse(OpenAiClient.shouldContinue("length", "hello", 4))
        assertFalse(OpenAiClient.shouldContinue("length", "hello", 9))
    }

    @Test
    fun interruptedStreamTriggersContinuation() {
        // 没有 finish_reason、也没等到 [DONE]：连接被切断 → 必须续写
        assertTrue(OpenAiClient.shouldContinue(null, "hello", 0, sawDone = false))
        // 正常结束（见到 [DONE]）就不该再发一轮
        assertFalse(OpenAiClient.shouldContinue(null, "hello", 0, sawDone = true))
    }

    @Test
    fun emptyCutRetriesOnce() {
        // 思考阶段被切断（一个字都没出来）→ 重试一次，换 none 思考强度
        assertTrue(OpenAiClient.shouldRetryEmpty(null, "", sawDone = false, retried = false))
        // 已经重试过就不再重试（避免烧额度）
        assertFalse(OpenAiClient.shouldRetryEmpty(null, "", sawDone = false, retried = true))
        // 有正文走续写，不走重试
        assertFalse(OpenAiClient.shouldRetryEmpty(null, "hello", sawDone = false, retried = false))
        // 正常结束不重试
        assertFalse(OpenAiClient.shouldRetryEmpty(null, "", sawDone = true, retried = false))
        assertFalse(OpenAiClient.shouldRetryEmpty("length", "", sawDone = false, retried = false))
    }

    @Test
    fun normalStopDoesNotContinue() {
        assertFalse(OpenAiClient.shouldContinue("stop", "hello", 0))
        assertFalse(OpenAiClient.shouldContinue(null, "hello", 0))
    }

    @Test
    fun emptyContentDoesNotContinue() {
        assertFalse(OpenAiClient.shouldContinue("length", "", 0))
        assertFalse(OpenAiClient.shouldContinue("length", "   ", 0))
    }
}
