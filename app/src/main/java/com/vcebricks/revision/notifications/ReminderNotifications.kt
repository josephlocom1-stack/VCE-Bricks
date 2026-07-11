package com.vcebricks.revision.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vcebricks.revision.MainActivity
import com.vcebricks.revision.R
import com.vcebricks.revision.RevisionApplication
import com.vcebricks.revision.data.SettingsStore
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

private const val CHANNEL_ID = "revision_reminders"
private const val NOTIFICATION_ID = 1001
private const val UNIQUE_WORK_NAME = "daily_revision_reminder"

class ReminderScheduler(
    private val context: Context,
    private val settingsStore: SettingsStore,
) {
    suspend fun ensureScheduled() {
        val settings = settingsStore.settings.first()
        if (!settings.notificationsEnabled) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }

        val request = PeriodicWorkRequestBuilder<ReviewReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(
                delayUntilNext(settings.reminderHour, settings.reminderMinute).toMinutes().coerceAtLeast(1),
                TimeUnit.MINUTES,
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    suspend fun replaceSchedule() {
        val settings = settingsStore.settings.first()
        if (!settings.notificationsEnabled) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }
        val request = PeriodicWorkRequestBuilder<ReviewReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(
                delayUntilNext(settings.reminderHour, settings.reminderMinute).toMinutes().coerceAtLeast(1),
                TimeUnit.MINUTES,
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request,
        )
    }

    private fun delayUntilNext(hour: Int, minute: Int): Duration {
        val now = ZonedDateTime.now()
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}

class ReviewReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as RevisionApplication
        val settings = application.container.settingsStore.settings.first()
        if (!settings.notificationsEnabled) return Result.success()

        val due = application.container.repository.getDueTopics(java.time.LocalDate.now())
        if (due.isEmpty()) {
            NotificationManagerCompat.from(applicationContext).cancel(NOTIFICATION_ID)
            return Result.success()
        }

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        createChannel()
        val openApp = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (due.size == 1) {
            "Time to revise ${due.first().subject}: ${due.first().topic}"
        } else {
            "${due.size} topics are ready to revise"
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Open Revision Reminder to start with the most overdue topic.")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    private fun createChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = applicationContext.getString(R.string.notification_channel_description)
            },
        )
    }
}
