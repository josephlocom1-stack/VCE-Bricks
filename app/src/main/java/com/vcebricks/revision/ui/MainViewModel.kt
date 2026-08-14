package com.vcebricks.revision.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vcebricks.revision.data.AppSettings
import com.vcebricks.revision.data.RevisionRepository
import com.vcebricks.revision.data.RevisionTopicEntity
import com.vcebricks.revision.data.SettingsStore
import com.vcebricks.revision.domain.ReviewOutcome
import com.vcebricks.revision.notifications.ReminderScheduler
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val today: LocalDate = LocalDate.now(),
    val overdue: List<RevisionTopicEntity> = emptyList(),
    val dueToday: List<RevisionTopicEntity> = emptyList(),
    val upcoming: List<RevisionTopicEntity> = emptyList(),
    val allTopics: List<RevisionTopicEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

class MainViewModel(
    private val repository: RevisionRepository,
    private val settingsStore: SettingsStore,
    private val reminderScheduler: ReminderScheduler,
    private val refreshWidgets: () -> Unit = {},
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val today = MutableStateFlow(LocalDate.now(clock))

    val uiState: StateFlow<MainUiState> = combine(
        repository.observeActiveTopics(),
        repository.observeAllTopics(),
        settingsStore.settings,
        today,
    ) { active, all, settings, currentDate ->
        val overdue = active.filter { it.nextReviewDateEpochDay < currentDate.toEpochDay() }
        val dueToday = active.filter { it.nextReviewDateEpochDay == currentDate.toEpochDay() }
        val upcoming = active.filter { it.nextReviewDateEpochDay > currentDate.toEpochDay() }
        MainUiState(
            today = currentDate,
            overdue = overdue,
            dueToday = dueToday,
            upcoming = upcoming,
            allTopics = all,
            settings = settings,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MainUiState(today = today.value),
    )

    fun refreshToday() {
        today.value = LocalDate.now(clock)
        refreshWidgets()
    }

    fun addTopic(
        subject: String,
        topic: String,
        note: String,
        studyDate: LocalDate,
        testDate: LocalDate?,
        onResult: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { repository.addTopic(subject, topic, note, studyDate, testDate) }
                .onSuccess {
                    refreshWidgets()
                    onResult(true)
                }
                .onFailure { onResult(false) }
        }
    }

    fun updateTopic(
        id: Long,
        subject: String,
        topic: String,
        note: String,
        testDate: LocalDate?,
        onResult: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { repository.updateTopic(id, subject, topic, note, testDate) }
                .onSuccess {
                    refreshWidgets()
                    onResult(true)
                }
                .onFailure { onResult(false) }
        }
    }

    fun completeReview(id: Long, outcome: ReviewOutcome, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val completionDate = LocalDate.now(clock)
            today.value = completionDate
            val completed = repository.completeReview(id, outcome, completionDate)
            if (completed) refreshWidgets()
            onResult(completed)
        }
    }

    fun archive(id: Long, archived: Boolean) = viewModelScope.launch {
        repository.setArchived(id, archived)
        refreshWidgets()
    }

    fun setTestCompleted(id: Long, completed: Boolean) = viewModelScope.launch {
        repository.setTestCompleted(id, completed)
        refreshWidgets()
    }

    fun delete(id: Long) = viewModelScope.launch {
        repository.deleteTopic(id)
        refreshWidgets()
    }

    fun completeOnboarding() = viewModelScope.launch {
        settingsStore.completeOnboarding()
        reminderScheduler.replaceSchedule()
    }

    fun updateReminder(enabled: Boolean, hour: Int, minute: Int) = viewModelScope.launch {
        settingsStore.updateReminder(enabled, hour, minute)
        reminderScheduler.replaceSchedule()
    }

    class Factory(
        private val repository: RevisionRepository,
        private val settingsStore: SettingsStore,
        private val reminderScheduler: ReminderScheduler,
        private val refreshWidgets: () -> Unit = {},
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(repository, settingsStore, reminderScheduler, refreshWidgets) as T
    }
}
