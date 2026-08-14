package com.vcebricks.revision

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vcebricks.revision.data.RevisionDatabase
import com.vcebricks.revision.data.RevisionRepository
import com.vcebricks.revision.data.SettingsStore
import com.vcebricks.revision.domain.ReviewScheduler
import com.vcebricks.revision.notifications.ReminderScheduler
import com.vcebricks.revision.widgets.RevisionWidgetUpdater
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
            RevisionWidgetUpdater.updateAll(this@RevisionApplication)
        }
    }
}

class AppContainer(application: Application) {
    val database: RevisionDatabase = Room.databaseBuilder(
        application,
        RevisionDatabase::class.java,
        "revision-reminder.db",
    ).addMigrations(MIGRATION_1_2).build()

    val scheduler = ReviewScheduler()
    val repository = RevisionRepository(database, scheduler)
    val settingsStore = SettingsStore(application)
    val reminderScheduler = ReminderScheduler(application, settingsStore)
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE revision_topics ADD COLUMN testDateEpochDay INTEGER")
        database.execSQL("ALTER TABLE revision_topics ADD COLUMN testCompletedAtEpochMillis INTEGER")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_revision_topics_testDateEpochDay ON revision_topics(testDateEpochDay)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_revision_topics_testCompletedAtEpochMillis ON revision_topics(testCompletedAtEpochMillis)")
    }
}
