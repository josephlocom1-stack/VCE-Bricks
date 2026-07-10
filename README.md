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

## Build

Open the repository in Android Studio with JDK 17, or use Gradle 8.9:

```bash
gradle testDebugUnitTest lintDebug assembleDebug
```

The project intentionally has no login, network requirement, advertising, subscription, social features or gamification.
