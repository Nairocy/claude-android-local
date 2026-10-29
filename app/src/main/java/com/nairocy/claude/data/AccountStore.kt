package com.nairocy.claude.data

import android.content.Context

/** 本地版唯一账户 id：数据目录、prefs 前缀都用它。 */
const val LOCAL_UID = "local"

/**
 * 本机账户：没有服务端，[uid] 恒为 `local`，资料全部存在 SharedPreferences。
 * 保留 [token] 字段只是为了少改调用点，本地版不会发给任何服务器。
 */
data class Account(
    val token: String = LOCAL_UID,
    val uid: String = LOCAL_UID,
    val email: String = "",
    val displayName: String = "",
    val preferredName: String = "",
    val preferences: String = "",
    val avatarVer: Int = 0,
) {
    val isLoggedIn: Boolean get() = uid.isNotBlank()

    /** 头像默认态的首字母：称呼 → 全名 → 邮箱。 */
    fun initial(): String {
        val src = preferredName.trim().ifBlank { displayName.trim() }.ifBlank { email.trim() }
        return src.take(1).uppercase().ifBlank { "?" }
    }

    /** 界面上怎么称呼这个人。 */
    fun name(): String = preferredName.trim().ifBlank { displayName.trim() }
        .ifBlank { email.substringBefore('@') }

    fun withAvatar(ver: Int): Account = copy(avatarVer = ver)
}

class AccountStore(context: Context) {

    private val ctx = context.applicationContext
    private val sp = ctx.getSharedPreferences("account", Context.MODE_PRIVATE)

    init {
        // 本地版没有登录流程：首次启动就写好本机账户，保证数据目录/资料页有归属。
        if (uid.isBlank()) {
            sp.edit().putString("uid", LOCAL_UID).putString("token", LOCAL_UID).apply()
        }
    }

    var token: String
        get() = sp.getString("token", "").orEmpty()
        set(v) = sp.edit().putString("token", v).apply()

    var uid: String
        get() = sp.getString("uid", "").orEmpty()
        set(v) = sp.edit().putString("uid", v).apply()

    var email: String
        get() = sp.getString("email", "").orEmpty()
        set(v) = sp.edit().putString("email", v).apply()

    var displayName: String
        get() = sp.getString("displayName", "").orEmpty()
        set(v) = sp.edit().putString("displayName", v).apply()

    var preferredName: String
        get() = sp.getString("preferredName", "").orEmpty()
        set(v) = sp.edit().putString("preferredName", v).apply()

    var preferences: String
        get() = sp.getString("preferences", "").orEmpty()
        set(v) = sp.edit().putString("preferences", v).apply()

    var avatarVer: Int
        get() = sp.getInt("avatarVer", 0)
        set(v) = sp.edit().putInt("avatarVer", v).apply()

    /** 本地 avatar.jpg 对应的服务端版本；和服务端 avatarVer 不一致就重新拉图。 */
    var avatarSyncedVer: Int
        get() = sp.getInt("avatarSyncedVer", -1)
        set(v) = sp.edit().putInt("avatarSyncedVer", v).apply()

    fun snapshot(): Account = Account(
        token = token,
        uid = uid,
        email = email,
        displayName = displayName,
        preferredName = preferredName,
        preferences = preferences,
        avatarVer = avatarVer,
    )

    fun save(account: Account) {
        sp.edit()
            .putString("token", account.token)
            .putString("uid", account.uid)
            .putString("email", account.email)
            .putString("displayName", account.displayName)
            .putString("preferredName", account.preferredName)
            .putString("preferences", account.preferences)
            .putInt("avatarVer", account.avatarVer)
            .apply()
    }

    /** 本地版没有登录态可清：只把资料恢复成默认值，本机账户 id 与数据目录保留。 */
    fun clear() {
        val keepUid = uid.ifBlank { LOCAL_UID }
        sp.edit().clear().apply()
        sp.edit().putString("uid", keepUid).putString("token", keepUid).apply()
    }
}
