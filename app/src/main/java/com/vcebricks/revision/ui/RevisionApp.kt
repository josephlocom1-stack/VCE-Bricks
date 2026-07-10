package com.vcebricks.revision.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vcebricks.revision.data.RevisionTopicEntity
import com.vcebricks.revision.domain.ReviewOutcome
import java.time.LocalDate
import java.time.format.DateTimeParseException

private enum class MainTab { TODAY, TOPICS }

@Composable
fun RevisionApp(
    viewModel: MainViewModel,
    requestNotificationPermission: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MaterialTheme {
        if (!state.settings.onboardingComplete) {
            OnboardingScreen {
                requestNotificationPermission()
                viewModel.completeOnboarding()
            }
        } else {
            MainShell(state, viewModel, requestNotificationPermission)
        }
    }
}

@Composable
private fun OnboardingScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Revision Reminder", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("Record what you study. The app brings it back when it is time to practise remembering it.")
        Spacer(Modifier.height(12.dp))
        Text("When a review is due, recall the important ideas before opening your notes, then rate how well you remembered.")
        Spacer(Modifier.height(28.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Set up reminders") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    state: MainUiState,
    viewModel: MainViewModel,
    requestNotificationPermission: () -> Unit,
) {
    var tab by remember { mutableStateOf(MainTab.TODAY) }
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var reviewTopic by remember { mutableStateOf<RevisionTopicEntity?>(null) }
    var editTopic by remember { mutableStateOf<RevisionTopicEntity?>(null) }
    var deleteTopic by remember { mutableStateOf<RevisionTopicEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tab == MainTab.TODAY) "Today" else "Topics") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == MainTab.TODAY,
                    onClick = { tab = MainTab.TODAY },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Today") },
                )
                NavigationBarItem(
                    selected = tab == MainTab.TOPICS,
                    onClick = { tab = MainTab.TOPICS },
                    icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                    label = { Text("Topics") },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add studied topic")
            }
        },
    ) { padding ->
        when (tab) {
            MainTab.TODAY -> TodayScreen(
                state = state,
                modifier = Modifier.padding(padding),
                onReview = { reviewTopic = it },
            )
            MainTab.TOPICS -> TopicsScreen(
                topics = state.allTopics,
                modifier = Modifier.padding(padding),
                onEdit = { editTopic = it },
                onArchive = { viewModel.archive(it.id, !it.isArchived) },
                onDelete = { deleteTopic = it },
            )
        }
    }

    if (showAdd) {
        TopicEditorDialog(
            title = "Add studied topic",
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { subject, topic, note, date ->
                viewModel.addTopic(subject, topic, note, date) { if (it) showAdd = false }
            },
        )
    }
    editTopic?.let { item ->
        TopicEditorDialog(
            title = "Edit topic",
            initial = item,
            onDismiss = { editTopic = null },
            onSave = { subject, topic, note, _ ->
                viewModel.updateTopic(item.id, subject, topic, note) { if (it) editTopic = null }
            },
        )
    }
    reviewTopic?.let { item ->
        ReviewDialog(
            topic = item,
            onDismiss = { reviewTopic = null },
            onOutcome = { outcome ->
                viewModel.completeReview(item.id, outcome) { if (it) reviewTopic = null }
            },
        )
    }
    deleteTopic?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTopic = null },
            title = { Text("Delete ${item.topic}?") },
            text = { Text("This permanently removes the topic and its complete review history.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(item.id)
                    deleteTopic = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTopic = null }) { Text("Cancel") } },
        )
    }
    if (showSettings) {
        SettingsDialog(
            enabled = state.settings.notificationsEnabled,
            initialHour = state.settings.reminderHour,
            initialMinute = state.settings.reminderMinute,
            onDismiss = { showSettings = false },
            onSave = { enabled, hour, minute ->
                if (enabled) requestNotificationPermission()
                viewModel.updateReminder(enabled, hour, minute)
                showSettings = false
            },
        )
    }
}

@Composable
private fun TodayScreen(state: MainUiState, modifier: Modifier, onReview: (RevisionTopicEntity) -> Unit) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(state.today.toString(), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
        }
        if (state.overdue.isEmpty() && state.dueToday.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("You are caught up for today.", fontWeight = FontWeight.Bold)
                        val next = state.upcoming.firstOrNull()
                        if (next != null) Text("Next review: ${LocalDate.ofEpochDay(next.nextReviewDateEpochDay)}")
                    }
                }
            }
        }
        if (state.overdue.isNotEmpty()) {
            item { SectionTitle("Overdue") }
            items(state.overdue, key = { it.id }) { TopicCard(it, state.today, true) { onReview(it) } }
        }
        if (state.dueToday.isNotEmpty()) {
            item { SectionTitle("Due today") }
            items(state.dueToday, key = { it.id }) { TopicCard(it, state.today, true) { onReview(it) } }
        }
        if (state.upcoming.isNotEmpty()) {
            item { SectionTitle("Upcoming") }
            items(state.upcoming.take(7), key = { it.id }) { TopicCard(it, state.today, false, null) }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun TopicsScreen(
    topics: List<RevisionTopicEntity>,
    modifier: Modifier,
    onEdit: (RevisionTopicEntity) -> Unit,
    onArchive: (RevisionTopicEntity) -> Unit,
    onDelete: (RevisionTopicEntity) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val filtered = topics.filter {
        search.isBlank() || it.subject.contains(search, true) || it.topic.contains(search, true)
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search subjects or topics") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        if (filtered.isEmpty()) item { Text("No matching topics.", modifier = Modifier.padding(16.dp)) }
        items(filtered, key = { it.id }) { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(item.subject, style = MaterialTheme.typography.labelLarge)
                    Text(item.topic, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (item.isArchived) "Archived" else "Next: ${LocalDate.ofEpochDay(item.nextReviewDateEpochDay)}")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { onEdit(item) }) { Icon(Icons.Default.Edit, "Edit") }
                        IconButton(onClick = { onArchive(item) }) { Icon(Icons.Default.Archive, if (item.isArchived) "Restore" else "Archive") }
                        IconButton(onClick = { onDelete(item) }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun TopicCard(
    item: RevisionTopicEntity,
    today: LocalDate,
    reviewEnabled: Boolean,
    onReview: (() -> Unit)?,
) {
    val dueDate = LocalDate.ofEpochDay(item.nextReviewDateEpochDay)
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.subject, style = MaterialTheme.typography.labelLarge)
                Text(item.topic, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val daysLate = java.time.temporal.ChronoUnit.DAYS.between(dueDate, today)
                Text(if (daysLate > 0) "$daysLate days overdue" else "Due $dueDate")
            }
            if (reviewEnabled && onReview != null) Button(onClick = onReview) { Text("Review") }
        }
    }
}

@Composable
private fun TopicEditorDialog(
    title: String,
    initial: RevisionTopicEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, LocalDate) -> Unit,
) {
    var subject by remember(initial?.id) { mutableStateOf(initial?.subject.orEmpty()) }
    var topic by remember(initial?.id) { mutableStateOf(initial?.topic.orEmpty()) }
    var note by remember(initial?.id) { mutableStateOf(initial?.note.orEmpty()) }
    var dateText by remember(initial?.id) {
        mutableStateOf(initial?.let { LocalDate.ofEpochDay(it.studyDateEpochDay).toString() } ?: LocalDate.now().toString())
    }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(subject, { subject = it }, label = { Text("Subject") }, singleLine = true)
                OutlinedTextField(topic, { topic = it }, label = { Text("Topic") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Optional short note") }, maxLines = 3)
                if (initial == null) {
                    OutlinedTextField(
                        dateText,
                        { dateText = it },
                        label = { Text("Date studied (YYYY-MM-DD)") },
                        singleLine = true,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val date = try { LocalDate.parse(dateText) } catch (_: DateTimeParseException) { null }
                when {
                    subject.isBlank() -> error = "Enter a subject."
                    topic.isBlank() -> error = "Enter a topic."
                    date == null -> error = "Use a valid date in YYYY-MM-DD format."
                    else -> onSave(subject, topic, note, date)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ReviewDialog(
    topic: RevisionTopicEntity,
    onDismiss: () -> Unit,
    onOutcome: (ReviewOutcome) -> Unit,
) {
    var checked by remember(topic.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${topic.subject}: ${topic.topic}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!checked) {
                    Text("Without opening your notes, say or write everything important you can remember. Then check your notes and correct what you missed.")
                    topic.note.takeIf { it.isNotBlank() }?.let { Text("Reminder: $it", style = MaterialTheme.typography.bodySmall) }
                } else {
                    Text("How well did you recall the important information before checking your notes?")
                    OutcomeButton("Forgot", "Very little or nothing important", ReviewOutcome.FORGOT, onOutcome)
                    OutcomeButton("Partly recalled", "Some important ideas, with meaningful gaps", ReviewOutcome.PARTLY_RECALLED, onOutcome)
                    OutcomeButton("Recalled well", "The important information was recalled", ReviewOutcome.RECALLED_WELL, onOutcome)
                }
            }
        },
        confirmButton = {
            if (!checked) Button(onClick = { checked = true }) { Text("I have checked my recall") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun OutcomeButton(label: String, description: String, outcome: ReviewOutcome, onOutcome: (ReviewOutcome) -> Unit) {
    OutlinedButton(onClick = { onOutcome(outcome) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingsDialog(
    enabled: Boolean,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onSave: (Boolean, Int, Int) -> Unit,
) {
    var notifications by remember { mutableStateOf(enabled) }
    var hour by remember { mutableIntStateOf(initialHour) }
    var minute by remember { mutableIntStateOf(initialMinute) }
    var hourText by remember { mutableStateOf(hour.toString()) }
    var minuteText by remember { mutableStateOf(minute.toString().padStart(2, '0')) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Daily reminders")
                    Switch(checked = notifications, onCheckedChange = { notifications = it })
                }
                OutlinedTextField(
                    hourText,
                    { hourText = it.filter(Char::isDigit).take(2) },
                    label = { Text("Hour (0–23)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    minuteText,
                    { minuteText = it.filter(Char::isDigit).take(2) },
                    label = { Text("Minute (0–59)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Text("Reminders are delivered approximately at the preferred time and only when something is due.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                hour = hourText.toIntOrNull()?.coerceIn(0, 23) ?: 18
                minute = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: 0
                onSave(notifications, hour, minute)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
