package com.nairocy.claude.data

/**
 * 订阅档位。
 *
 * - [id] 与服务端 `/api/entitlements` 返回的 `plan` 字段同名（小写）；
 * - [label] 只用于界面徽标文案（Free / Pro / Max）；
 * - [rank] 单调递增，用来判「至少某档」（见 [EntitlementStore.isUsable]）。
 *
 * 未知/空的 plan id 一律降级成 [FREE]：宁可少给权益，也不要把付费权益错发出去。
 */
enum class Plan(val id: String, val label: String, val rank: Int) {
    FREE("free", "Free", 0),
    PRO("pro", "Pro", 1),
    MAX("max", "Max", 2);

    companion object {
        fun from(id: String?): Plan = entries.firstOrNull { it.id == id } ?: FREE
    }
}
