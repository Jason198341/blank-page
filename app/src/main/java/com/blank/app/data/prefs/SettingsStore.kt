package com.blank.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("blank_settings")

data class Settings(
    /** 알림이 뜨는 시각 (D0 은 등록 직후 즉시라 여기 해당 없음) */
    val notifyHour: Int = 8,
    val notifyMinute: Int = 0,
    /** 하루에 보여줄 인출 상한. 넘치면 이월된다. */
    val dailyCap: Int = 7,
    /** 놓친 회차를 만료시킬 것인가. 기본은 이월(false). */
    val expireMissed: Boolean = false,
    /** 재현 화면에서 힌트 1회를 허용할 것인가 */
    val allowHint: Boolean = true,
    /** 동시에 살아 있을 수 있는 항목 수. 넘으면 신규 등록을 막는다. */
    val activeLimit: Int = 30
)

class SettingsStore(private val context: Context) {

    val flow: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun get(): Settings = context.dataStore.data.first().toSettings()

    suspend fun setNotifyTime(hour: Int, minute: Int) = context.dataStore.edit {
        it[NOTIFY_HOUR] = hour; it[NOTIFY_MINUTE] = minute
    }

    suspend fun setDailyCap(v: Int) = context.dataStore.edit { it[DAILY_CAP] = v }
    suspend fun setExpireMissed(v: Boolean) = context.dataStore.edit { it[EXPIRE_MISSED] = v }
    suspend fun setAllowHint(v: Boolean) = context.dataStore.edit { it[ALLOW_HINT] = v }
    suspend fun setActiveLimit(v: Int) = context.dataStore.edit { it[ACTIVE_LIMIT] = v }

    private fun Preferences.toSettings() = Settings(
        notifyHour = this[NOTIFY_HOUR] ?: 8,
        notifyMinute = this[NOTIFY_MINUTE] ?: 0,
        dailyCap = this[DAILY_CAP] ?: 7,
        expireMissed = this[EXPIRE_MISSED] ?: false,
        allowHint = this[ALLOW_HINT] ?: true,
        activeLimit = this[ACTIVE_LIMIT] ?: 30
    )

    private companion object {
        val NOTIFY_HOUR = intPreferencesKey("notify_hour")
        val NOTIFY_MINUTE = intPreferencesKey("notify_minute")
        val DAILY_CAP = intPreferencesKey("daily_cap")
        val EXPIRE_MISSED = booleanPreferencesKey("expire_missed")
        val ALLOW_HINT = booleanPreferencesKey("allow_hint")
        val ACTIVE_LIMIT = intPreferencesKey("active_limit")
    }
}
