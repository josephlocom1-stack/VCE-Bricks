package com.vcebricks.revision.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "revision_settings")

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val reminderHour: Int = 18,
    val reminderMinute: Int = 0,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val OnboardingComplete = booleanPreferencesKey("onboarding_complete")
        val NotificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val ReminderHour = intPreferencesKey("reminder_hour")
        val ReminderMinute = intPreferencesKey("reminder_minute")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            onboardingComplete = preferences[Keys.OnboardingComplete] ?: false,
            notificationsEnabled = preferences[Keys.NotificationsEnabled] ?: true,
            reminderHour = (preferences[Keys.ReminderHour] ?: 18).coerceIn(0, 23),
            reminderMinute = (preferences[Keys.ReminderMinute] ?: 0).coerceIn(0, 59),
        )
    }

    suspend fun completeOnboarding() {
        context.dataStore.edit { it[Keys.OnboardingComplete] = true }
    }

    suspend fun updateReminder(enabled: Boolean, hour: Int, minute: Int) {
        context.dataStore.edit {
            it[Keys.NotificationsEnabled] = enabled
            it[Keys.ReminderHour] = hour.coerceIn(0, 23)
            it[Keys.ReminderMinute] = minute.coerceIn(0, 59)
        }
    }
}
