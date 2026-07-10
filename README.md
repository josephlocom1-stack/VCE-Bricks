# VCE Bricks — Revision Reminder

A focused Android app that tells students what to revise today. Users record a subject and topic, the app schedules an active-recall review, and overdue topics stay at the top until completed.

## Current MVP

- Offline Room persistence
- Overdue-first Today screen
- Topic creation, editing, archiving and deletion
- Active-recall review flow
- Adaptive intervals: 1, 3, 7, 14, 30, 60 and 120 days
- One pending review per topic
- Daily WorkManager reminder that queries current database state
- Notification-permission denial handled safely
- Light/dark system theme

## Scheduling

- New topic: review in 1 day
- Forgot: move back one stage and review tomorrow
- Partly recalled: keep the stage and use 60% of its interval, minimum 2 days
- Recalled well: advance one stage
- Every next date is calculated from the actual completion date

## Build locally

Open the repository in Android Studio with JDK 17, or use Gradle 8.9:

```bash
gradle testDebugUnitTest lintDebug assembleDebug
```

The debug APK is produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Download a CI-built APK

1. Open the repository's **Actions** tab on GitHub.
2. Open the latest successful **Android CI** run for the feature branch or pull request.
3. Scroll to **Artifacts**.
4. Download `revision-reminder-debug-apk`.
5. Extract the ZIP and transfer `app-debug.apk` to an Android phone.
6. Allow installation from the browser or file manager when Android asks.

The CI artifact is retained for 14 days. It is a debug build for testing, not a Play Store release.

## Test on a physical phone

Follow [`DEVICE_TEST_CHECKLIST.md`](DEVICE_TEST_CHECKLIST.md). Keep the pull request in draft until the critical installation, persistence, active-recall, and notification tests pass.

Automated compilation cannot prove exact behaviour on every Android device. In particular, WorkManager reminders are battery-conscious and approximate rather than exact alarms.

The project intentionally has no login, network requirement, advertising, subscription, social features or gamification.
