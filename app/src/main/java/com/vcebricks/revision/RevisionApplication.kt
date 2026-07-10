package com.vcebricks.revision

import android.app.Application
import androidx.room.Room
import com.vcebricks.revision.data.RevisionDatabase
import com.vcebricks.revision.data.RevisionRepository
import com.vcebricks.revision.data.SettingsStore
import com.vcebricks.revision.domain.ReviewScheduler
import com.vcebricks.revision.notifications.ReminderScheduler

class RevisionApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.reminderScheduler.ensureScheduled()
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
