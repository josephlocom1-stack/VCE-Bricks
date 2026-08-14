package com.vcebricks.revision.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.vcebricks.revision.MainActivity
import com.vcebricks.revision.R
import com.vcebricks.revision.RevisionApplication
import com.vcebricks.revision.data.RevisionTopicEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DueReviewsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        RevisionWidgetUpdater.updateAll(context)
    }

    override fun onEnabled(context: Context) {
        RevisionWidgetUpdater.updateAll(context)
    }
}

class NextReviewWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        RevisionWidgetUpdater.updateAll(context)
    }

    override fun onEnabled(context: Context) {
        RevisionWidgetUpdater.updateAll(context)
    }
}

class NextTestWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        RevisionWidgetUpdater.updateAll(context)
    }

    override fun onEnabled(context: Context) {
        RevisionWidgetUpdater.updateAll(context)
    }
}

object RevisionWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateAll(context: Context) {
        val application = context.applicationContext as? RevisionApplication ?: return
        scope.launch {
            val topics = application.container.repository.getActiveTopicsSnapshot()
            val today = LocalDate.now()
            updateDueWidget(context, topics, today)
            updateNextReviewWidget(context, topics, today)
            updateNextTestWidget(context, topics, today)
        }
    }

    private fun updateDueWidget(context: Context, topics: List<RevisionTopicEntity>, today: LocalDate) {
        val due = topics.filter { it.nextReviewDateEpochDay <= today.toEpochDay() }
        val overdue = due.count { it.nextReviewDateEpochDay < today.toEpochDay() }
        val views = RemoteViews(context.packageName, R.layout.widget_due_reviews).apply {
            setTextViewText(R.id.widget_due_count, due.size.toString())
            setTextViewText(
                R.id.widget_due_message,
                when {
                    due.isEmpty() -> "You are caught up"
                    overdue > 0 -> "$overdue overdue · ${due.size} ready"
                    due.size == 1 -> "1 topic ready today"
                    else -> "${due.size} topics ready today"
                },
            )
            setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        }
        updateProvider(context, DueReviewsWidgetProvider::class.java, views)
    }

    private fun updateNextReviewWidget(context: Context, topics: List<RevisionTopicEntity>, today: LocalDate) {
        val next = topics.minWithOrNull(
            compareBy<RevisionTopicEntity> { it.nextReviewDateEpochDay }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.subject }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.topic },
        )
        val views = RemoteViews(context.packageName, R.layout.widget_next_review).apply {
            if (next == null) {
                setTextViewText(R.id.widget_next_subject, "No active topics")
                setTextViewText(R.id.widget_next_topic, "Add something you studied")
                setTextViewText(R.id.widget_next_when, "Open Revision Reminder")
            } else {
                val dueDate = LocalDate.ofEpochDay(next.nextReviewDateEpochDay)
                val days = ChronoUnit.DAYS.between(today, dueDate)
                setTextViewText(R.id.widget_next_subject, next.subject)
                setTextViewText(R.id.widget_next_topic, next.topic)
                setTextViewText(
                    R.id.widget_next_when,
                    when {
                        days < -1 -> "${-days} days overdue"
                        days == -1L -> "1 day overdue"
                        days == 0L -> "Due today"
                        days == 1L -> "Due tomorrow"
                        else -> "Due in $days days"
                    },
                )
            }
            setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        }
        updateProvider(context, NextReviewWidgetProvider::class.java, views)
    }

    private fun updateNextTestWidget(context: Context, topics: List<RevisionTopicEntity>, today: LocalDate) {
        val next = topics
            .filter { it.testDateEpochDay != null }
            .minByOrNull { it.testDateEpochDay!! }
        val views = RemoteViews(context.packageName, R.layout.widget_next_test).apply {
            if (next == null) {
                setTextViewText(R.id.widget_test_subject, "No test date set")
                setTextViewText(R.id.widget_test_topic, "Add a test date to a topic")
                setTextViewText(R.id.widget_test_when, "Keep revising until test day")
            } else {
                val testDate = LocalDate.ofEpochDay(next.testDateEpochDay!!)
                val days = ChronoUnit.DAYS.between(today, testDate)
                setTextViewText(R.id.widget_test_subject, next.subject)
                setTextViewText(R.id.widget_test_topic, next.topic)
                setTextViewText(
                    R.id.widget_test_when,
                    when {
                        days < 0 -> "Test date passed · mark test taken"
                        days == 0L -> "Test today"
                        days == 1L -> "Test tomorrow"
                        else -> "$days days until test"
                    },
                )
            }
            setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        }
        updateProvider(context, NextTestWidgetProvider::class.java, views)
    }

    private fun updateProvider(context: Context, provider: Class<*>, views: RemoteViews) {
        val manager = AppWidgetManager.getInstance(context)
        manager.getAppWidgetIds(ComponentName(context, provider)).forEach { id ->
            manager.updateAppWidget(id, views)
        }
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        3001,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
