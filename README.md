# Weekly Totals

A simple Android app for tracking weekly spending against a budget. Designed for two users sharing a household budget.

## Features

- **Weekly budget tracking** — Set a weekly budget and log transactions by category
- **Budget gauge** — Visual circular gauge shows how much is left for the week
- **Categories** — Default categories: Gas, Travel, Dining Out, Ordering In, Amazon, Misc Purchases, Refund. Add, edit, or delete custom categories with color coding.
- **Refunds** — Refund category reduces weekly spending total, increasing remaining budget (even beyond the weekly limit)
- **Transaction details** — Optional description/comment field when adding transactions, displayed in smaller text below the category in the current week view
- **Two-user sync** — Firebase Realtime Database syncs transactions, categories, and budget across two devices in real time
- **Google Sign-In** — Whitelisted email authentication (no passwords to remember)
- **Spending history** — Pie chart breakdowns by month or year
- **SMS transaction detection** — Optional auto-detect bank SMS notifications and prompt to add the transaction
- **Reset** — Clear all data locally and across synced devices from Settings

## Setup

1. Clone the repo
2. Place your `google-services.json` from Firebase Console into `app/`
3. Create a Firebase Realtime Database and set rules:
   ```json
   {
     "rules": {
       "weekly_totals": {
         ".read": "auth != null",
         ".write": "auth != null"
       }
     }
   }
   ```
4. Update the allowed emails in `LoginActivity.kt`
5. `make build` — builds the APK and copies it to `~/Downloads/WeeklyTotals.apk`

## Build Commands

| Command | Description |
|---------|-------------|
| `make build` | Build debug APK and copy to ~/Downloads |
| `make test` | Run unit tests |
| `make clean` | Clean build artifacts |
| `make install` | Install APK on connected device |
| `make run` | Build, install, and launch on device |

## Device Versions

| Device | Model | Serial | Current Version | Last Updated |
|--------|-------|--------|-----------------|--------------|
| Samsung Flip | SM-F741W | R5CX70D8MEH | v1.1.0 | 2026-03-01 |
| Google Pixel 9 | Pixel 9 | 48131FDAQ003LD | v1.1.0 | 2026-03-01 |

## Tech Stack

- Kotlin
- Android SDK 34 (min SDK 24)
- Room (local database)
- Firebase Realtime Database (sync)
- Firebase Auth + Google Sign-In
- Material Design Components
