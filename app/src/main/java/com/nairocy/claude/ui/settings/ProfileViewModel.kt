package com.nairocy.claude.ui.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nairocy.claude.data.Account
import com.nairocy.claude.data.AvatarStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 个人资料页的本地实现：没有网络，保存只写 SharedPreferences / 本地头像文件。
 *
 * 方法签名保留 `token` 参数只为少改调用点（本地版恒为 `local`），不会发给任何服务器。
 * 每次成功后把最新 [Account] 放进 [savedAccount]，由 Activity 落盘 + 刷界面。
 */
class ProfileViewModel : ViewModel() {

    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var savedAccount by mutableStateOf<Account?>(null)
        private set

    fun consumeSaved() {
        savedAccount = null
    }

    fun clearError() {
        error = null
    }

    fun saveProfile(token: String, current: Account, displayName: String, preferredName: String) {
        error = null
        savedAccount = current.copy(
            displayName = displayName.trim(),
            preferredName = preferredName.trim(),
        )
    }

    fun savePreferences(token: String, current: Account, text: String) {
        error = null
        savedAccount = current.copy(preferences = text.trim())
    }

    fun uploadAvatar(context: Context, token: String, current: Account, uri: Uri) {
        if (saving) return
        saving = true
        error = null
        val app = context.applicationContext
        viewModelScope.launch {
            val jpeg = withContext(Dispatchers.IO) { AvatarStore.encode(app, uri) }
            if (jpeg == null) {
                error = "图片读取失败，换一张试试"
                saving = false
                return@launch
            }
            withContext(Dispatchers.IO) { AvatarStore.saveLocal(app, jpeg) }
            savedAccount = current.withAvatar(current.avatarVer + 1)
            saving = false
        }
    }

    fun resetAvatar(context: Context, token: String, current: Account) {
        if (saving) return
        saving = true
        error = null
        val app = context.applicationContext
        viewModelScope.launch {
            withContext(Dispatchers.IO) { AvatarStore.clearLocal(app) }
            savedAccount = current.withAvatar(0)
            saving = false
        }
    }
}
