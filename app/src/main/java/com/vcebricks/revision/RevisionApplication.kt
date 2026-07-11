package com.vcebricks.revision

import android.app.Application
import androidx.room.Room
import com.vcebricks.revision.data.RevisionDatabase
import com.vcebricks.revision.data.RevisionRepository
import com.vcebricks.revision.data.SettingsStore
import com.vcebricks.revision.domain.ReviewScheduler
import com.vcebricks.revision.notifications.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RevisionApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            container.reminderScheduler.ensureScheduled()
        }
    }
}

class AppContainer(application: Application) {
    val database: RevisionDatabase = Room.databaseBuilder(
        application,
        RevisionDatabase::class.java,
        "revision-reminder.db",
    ).build()

    val scheduler = ReviewScheduler()
    val repository = RevisionRepository(database, scheduler)
    val settingsStore = SettingsStore(application)
    val reminderScheduler = ReminderScheduler(application, settingsStore)
}
