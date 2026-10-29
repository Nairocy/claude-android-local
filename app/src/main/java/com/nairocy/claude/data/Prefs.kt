package com.nairocy.claude.data

import android.content.Context

/** 轻量偏好（颜色模式等），与模型配置分开存。 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    /** 网页搜索开关（走 DeepSeek /v1/tools/search） */
    var webSearch: Boolean
        get() = sp.getBoolean("webSearch", true)
        set(v) {
            sp.edit().putBoolean("webSearch", v).apply()
        }

    /** 记忆开关（files/memory.md 存在时注入系统提示） */
    var memory: Boolean
        get() = sp.getBoolean("memory", false)
        set(v) {
            sp.edit().putBoolean("memory", v).apply()
        }

    /**
     * 快速输出（功能页开关，默认关）。
     * 关 = 流式严格按示例的固定速率逐字浮出；开 = 后端吐得快时自动提速追平（最多落后约 1.5 秒）。
     */
    var fastOutput: Boolean
        get() = sp.getBoolean("fastOutput", false)
        set(v) {
            sp.edit().putBoolean("fastOutput", v).apply()
        }

    /**
     * 触感反馈总开关。默认开。
     * 读取走 [isHapticEnabled] 是因为 [Haptics] 没有 Context 时也要能快速判断。
     */
    var haptic: Boolean
        get() = sp.getBoolean("haptic", true)
        set(v) {
            sp.edit().putBoolean("haptic", v).apply()
        }

    /** Code 页当前选中的工作区 id（换工作区后记着，下次打开还在那儿）。 */
    var codeWorkspaceId: String
        get() = sp.getString("codeWorkspaceId", "") ?: ""
        set(v) {
            sp.edit().putString("codeWorkspaceId", v).apply()
        }

    /** 模型档位：fable / opus / sonnet / haiku（映射到 reasoning_effort） */
    var modelTier: String
        get() = sp.getString("modelTier", "sonnet") ?: "sonnet"
        set(v) {
            sp.edit().putString("modelTier", v).apply()
        }

    /** 功能页开关：目前只有 webSearch / memory 有真实效果，其余先落盘（UI 对齐官方）。 */
    var artifacts: Boolean
        get() = sp.getBoolean("artifacts", true)
        set(v) { sp.edit().putBoolean("artifacts", v).apply() }

    var inChatViz: Boolean
        get() = sp.getBoolean("inChatViz", true)
        set(v) { sp.edit().putBoolean("inChatViz", v).apply() }

    var codeExec: Boolean
        get() = sp.getBoolean("codeExec", true)
        set(v) { sp.edit().putBoolean("codeExec", v).apply() }

    var modelFallback: Boolean
        get() = sp.getBoolean("modelFallback", true)
        set(v) { sp.edit().putBoolean("modelFallback", v).apply() }

    var memoryAutogen: Boolean
        get() = sp.getBoolean("memoryAutogen", true)
        set(v) { sp.edit().putBoolean("memoryAutogen", v).apply() }

    var memorySensitive: Boolean
        get() = sp.getBoolean("memorySensitive", true)
        set(v) { sp.edit().putBoolean("memorySensitive", v).apply() }

    /** auto | onDemand | always */
    var toolAccess: String
        get() = sp.getString("toolAccess", "auto") ?: "auto"
        set(v) { sp.edit().putString("toolAccess", v).apply() }

    /** 字体样式：default（Anthropic 字体）| system（系统字体） */
    var fontMode: String
        get() = sp.getString("fontMode", "default") ?: "default"
        set(v) { sp.edit().putString("fontMode", v).apply() }

    /** 通知：回复完成 / 产品更新 */
    var notifyReplies: Boolean
        get() = sp.getBoolean("notifyReplies", true)
        set(v) { sp.edit().putBoolean("notifyReplies", v).apply() }

    var notifyProduct: Boolean
        get() = sp.getBoolean("notifyProduct", false)
        set(v) { sp.edit().putBoolean("notifyProduct", v).apply() }

    /**
     * 是否已经主动问过通知权限（Android 13+）。
     * 单独记账：老用户升级上来也要被问一次，不能挂在 permAsked（位置/日历）上。
     */
    var notifyAsked: Boolean
        get() = sp.getBoolean("notifyAsked", false)
        set(v) { sp.edit().putBoolean("notifyAsked", v).apply() }

    /** 时间与专注：休息提醒（"-" / "1 hr" … "12 hr"；"-" / "15 min" …） */
    var breakHours: String
        get() = sp.getString("breakHours", "-") ?: "-"
        set(v) { sp.edit().putString("breakHours", v).apply() }

    var breakMinutes: String
        get() = sp.getString("breakMinutes", "-") ?: "-"
        set(v) { sp.edit().putString("breakMinutes", v).apply() }

    /** 专注时段开启的星期，逗号分隔的 0..6（0=周日），默认空 */
    var quietDays: String
        get() = sp.getString("quietDays", "") ?: ""
        set(v) { sp.edit().putString("quietDays", v).apply() }

    /** 隐私：允许用对话训练和改进模型（默认关，与官方截图一致） */
    var trainOnChats: Boolean
        get() = sp.getBoolean("trainOnChats", false)
        set(v) { sp.edit().putBoolean("trainOnChats", v).apply() }

    /** system | light | dark */
    var themeMode: String
        get() = sp.getString("themeMode", "system") ?: "system"
        set(v) {
            sp.edit().putString("themeMode", v).apply()
        }

    /* ── 设备上下文（位置 / 日程）── */

    /** 首次使用是否已经请求过位置+日历权限（只主动问一次） */
    var permAsked: Boolean
        get() = sp.getBoolean("permAsked", false)
        set(v) { sp.edit().putBoolean("permAsked", v).apply() }

    /** 最近一次成功定位的文案，如「杭州市 浙江省 中国」 */
    var locText: String
        get() = sp.getString("locText", "").orEmpty()
        set(v) { sp.edit().putString("locText", v).apply() }

    var locAt: Long
        get() = sp.getLong("locAt", 0L)
        set(v) { sp.edit().putLong("locAt", v).apply() }

    /** 今天的日程文案（多行），配 calDay 判新旧 */
    var calText: String
        get() = sp.getString("calText", "").orEmpty()
        set(v) { sp.edit().putString("calText", v).apply() }

    /** calText 对应的日期 yyyy-MM-dd */
    var calDay: String
        get() = sp.getString("calDay", "").orEmpty()
        set(v) { sp.edit().putString("calDay", v).apply() }

    fun clearLocation() {
        sp.edit().remove("locText").remove("locAt").apply()
    }

    fun clearCalendar() {
        sp.edit().remove("calText").remove("calDay").apply()
    }

    companion object {
        /** 给 [Haptics] 用：不实例化 Prefs 也能快速判断触感开关。 */
        fun isHapticEnabled(context: Context): Boolean =
            context.applicationContext
                .getSharedPreferences("prefs", Context.MODE_PRIVATE)
                .getBoolean("haptic", true)
    }
}
