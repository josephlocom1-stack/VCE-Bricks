# Revision Reminder — Android Device Test Checklist

Use this checklist on a physical Android phone before merging the MVP pull request.

Record the phone model, Android version, test date, tester, and APK commit SHA.

## 1. Installation and launch

- [ ] Download and install the debug APK successfully.
- [ ] The app opens without crashing.
- [ ] The onboarding screen is readable at normal font size.
- [ ] The app still opens after being force-stopped.

## 2. Notification permission

Test both paths, reinstalling or resetting app permissions where necessary.

- [ ] Denying notification permission does not crash the app.
- [ ] The app remains fully usable after permission denial.
- [ ] Allowing notification permission succeeds.
- [ ] Changing reminder settings does not create duplicate notifications.

## 3. Topic creation and persistence

- [ ] Empty subject or topic values cannot be saved.
- [ ] A valid topic can be added.
- [ ] The topic appears in the Topics screen.
- [ ] The first review date is one local calendar day after the study date.
- [ ] The topic remains after closing and reopening the app.
- [ ] Rapidly tapping Save does not create duplicates.

## 4. Today and overdue behaviour

For practical testing, create topics using past study dates.

- [ ] A due topic appears under Due today.
- [ ] An overdue topic remains visible.
- [ ] Overdue topics appear before topics due today.
- [ ] The oldest overdue date appears first.
- [ ] An archived topic disappears from due lists.

## 5. Active-recall review flow

- [ ] Opening a due review shows the recall-before-notes instruction.
- [ ] A review is not completed before an outcome is chosen.
- [ ] Forgot schedules the next review for tomorrow.
- [ ] Partly recalled uses the shortened interval.
- [ ] Recalled well advances to the next interval.
- [ ] A late review calculates from the actual completion date.
- [ ] Rapidly tapping an outcome does not create duplicate review attempts.

## 6. Topic management

- [ ] Subject, topic, and note can be edited.
- [ ] Archiving removes the topic from active review lists.
- [ ] Restoring makes the topic active again.
- [ ] Delete requires confirmation.
- [ ] A deleted topic does not return after restarting the app.

## 7. Notification delivery

Because WorkManager timing is approximate, allow a reasonable delivery window.

- [ ] A notification appears when at least one active topic is due.
- [ ] The notification summarises multiple due topics instead of spamming one alert per topic.
- [ ] Tapping the notification opens the app.
- [ ] No notification appears when nothing is due.
- [ ] Editing, archiving, or deleting a topic is reflected in the next notification.
- [ ] A reminder still works after the app has been swiped away.
- [ ] A reminder still works after a device restart, if practical to test.

## 8. Display and accessibility

- [ ] Light mode is readable.
- [ ] Dark mode is readable.
- [ ] Large system font does not cut off essential controls.
- [ ] Buttons have comfortable tap targets.
- [ ] Overdue status is understandable without relying only on colour.

## 9. Offline operation

- [ ] Turn on airplane mode.
- [ ] Add, review, edit, archive, and delete topics successfully.
- [ ] Restart the app while offline and confirm saved data remains.

## Exit criteria

Do not merge until:

- every critical item passes;
- any failure has reproducible steps;
- crashes, lost data, duplicate reviews, and duplicate notifications are fixed;
- notification timing limitations are documented rather than presented as exact alarms.

## Test record

| Field | Value |
|---|---|
| Device | |
| Android version | |
| App commit SHA | |
| Test date | |
| Tester | |
| Failed checks | |
| Notes | |
