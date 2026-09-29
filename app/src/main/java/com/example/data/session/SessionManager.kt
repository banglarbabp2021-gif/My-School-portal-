package com.example.data.session

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSessionState(
    val role: String = "NONE", // "NONE", "ADMIN", "TEACHER", "STUDENT"
    val userId: String = "",
    val schoolId: String = "",
    val schoolName: String = "",
    val userName: String = "",
    val userClass: String = "",
    val userSection: String = "",
    val userRoll: String = ""
) {
    val isLoggedIn: Boolean get() = role != "NONE" && userId.isNotBlank()
    val className: String get() = userClass
    val userRole: String get() = role
}

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_session_prefs", Context.MODE_PRIVATE)

    private val _sessionFlow = MutableStateFlow(loadSession())
    val sessionFlow: StateFlow<UserSessionState> = _sessionFlow.asStateFlow()

    private fun loadSession(): UserSessionState {
        return UserSessionState(
            role = prefs.getString("role", "NONE") ?: "NONE",
            userId = prefs.getString("userId", "") ?: "",
            schoolId = prefs.getString("schoolId", "") ?: "",
            schoolName = prefs.getString("schoolName", "") ?: "",
            userName = prefs.getString("userName", "") ?: "",
            userClass = prefs.getString("userClass", "") ?: "",
            userSection = prefs.getString("userSection", "") ?: "",
            userRoll = prefs.getString("userRoll", "") ?: ""
        )
    }

    fun saveSession(state: UserSessionState) {
        prefs.edit()
            .putString("role", state.role)
            .putString("userId", state.userId)
            .putString("schoolId", state.schoolId)
            .putString("schoolName", state.schoolName)
            .putString("userName", state.userName)
            .putString("userClass", state.userClass)
            .putString("userSection", state.userSection)
            .putString("userRoll", state.userRoll)
            .apply()
        _sessionFlow.value = state
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        _sessionFlow.value = UserSessionState()
    }
}
