package com.example.appblocker

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "blocker_prefs")

object BlockerPreferences {

    // Лимит непрерывной сессии в минутах (применяется к TikTok).
    private val KEY_SESSION_LIMIT_MIN = intPreferencesKey("session_limit_minutes")

    // Grace-период в секундах: если пользователь свернул заблокированное
    // приложение на это время или дольше — сессия сбрасывается.
    private val KEY_GRACE_SECONDS = intPreferencesKey("grace_seconds")

    const val DEFAULT_LIMIT_MIN = 15
    const val DEFAULT_GRACE_SEC = 60

    fun sessionLimitFlow(context: Context): Flow<Int> =
        context.dataStore.data.map { it[KEY_SESSION_LIMIT_MIN] ?: DEFAULT_LIMIT_MIN }

    fun graceSecondsFlow(context: Context): Flow<Int> =
        context.dataStore.data.map { it[KEY_GRACE_SECONDS] ?: DEFAULT_GRACE_SEC }

    suspend fun setSessionLimit(context: Context, minutes: Int) {
        context.dataStore.edit { it[KEY_SESSION_LIMIT_MIN] = minutes.coerceIn(1, 240) }
    }

    suspend fun setGraceSeconds(context: Context, seconds: Int) {
        context.dataStore.edit { it[KEY_GRACE_SECONDS] = seconds.coerceIn(10, 600) }
    }
}

