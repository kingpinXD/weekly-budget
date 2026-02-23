# Weekly Totals - Architecture & Implementation Guide

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 1.9.22 |
| Build | Gradle 8.5, Kotlin DSL, KSP |
| Android | compileSdk 34, minSdk 24, targetSdk 34, Java 17 |
| Architecture | MVVM (ViewModel + LiveData) |
| Local Database | Room 2.6.1 (SQLite) |
| Backend | Firebase Realtime Database + Firebase Auth |
| Auth | Google Sign-In (whitelisted emails) |
| UI | AndroidX, Material Design 3, custom views |
| Testing | JUnit 4, Robolectric, kotlinx-coroutines-test |

---

## Package Structure

```
com.example.weeklytotals/
├── WeeklyTotalsApp.kt              # Application class (Firebase persistence)
├── LoginActivity.kt                # Google Sign-In + email whitelist
├── BudgetSetupActivity.kt          # First-launch budget & category setup
├── MainActivity.kt                 # Main weekly tracker screen
├── MainViewModel.kt                # Business logic, week rollover
├── TransactionAdapter.kt           # RecyclerView adapter for transactions
├── BudgetGaugeView.kt              # Custom circular budget gauge
├── PieChartView.kt                 # Custom pie chart for history
├── HistoryActivity.kt              # Monthly/yearly spending breakdown
├── SettingsActivity.kt             # Budget, auto-transactions, reset
├── ManageCategoriesActivity.kt     # CRUD for spending categories
├── MonitoredAppsActivity.kt        # Select apps for notification monitoring
├── TransactionPromptActivity.kt    # Auto-detected transaction confirmation
├── data/
│   ├── AppDatabase.kt              # Room database setup, migrations
│   ├── Transaction.kt              # Transaction entity
│   ├── TransactionDao.kt           # Transaction queries
│   ├── CategoryEntity.kt           # Category entity
│   ├── CategoryDao.kt              # Category queries
│   ├── CategoryTotal.kt            # Grouped query result
│   ├── BudgetPreferences.kt        # SharedPreferences wrapper
│   ├── WeekCalculator.kt           # Saturday-based week calculations
│   └── FirebaseSyncManager.kt      # Bidirectional Firebase sync
├── sms/
│   ├── SmsBroadcastReceiver.kt     # Intercepts incoming SMS
│   └── SmsTransactionDetector.kt   # Regex-based amount extraction
└── notification/
    └── TransactionNotificationListenerService.kt  # App notification monitoring
```

---

## Data Model

### Room Entities

**Transaction** (`transactions` table)

| Column | Type | Notes |
|--------|------|-------|
| id | Long | Auto-generated PK |
| weekStartDate | String | `yyyy-MM-dd` of the Saturday starting the week |
| category | String | References `CategoryEntity.name` |
| amount | Double | Spend amount |
| isAdjustment | Boolean | True for carry-over overage entries |
| createdAt | Long | Epoch millis, also used as Firebase key |

**CategoryEntity** (`categories` table)

| Column | Type | Notes |
|--------|------|-------|
| id | Long | Auto-generated PK |
| name | String | Unique key, e.g. `GAS`, `DINING_OUT` |
| displayName | String | Human-readable, e.g. `Dining Out` |
| color | String | Hex color, e.g. `#FF9800` |
| isSystem | Boolean | True only for `ADJUSTMENT` |

### SharedPreferences (`BudgetPreferences`)

| Key | Type | Purpose |
|-----|------|---------|
| budget | Double | Current weekly budget |
| pending_budget | Double | Budget to apply next Saturday |
| is_budget_set | Boolean | Whether setup is complete |
| auto_transactions_enabled | Boolean | SMS/notification detection toggle |
| total_savings | Double | Accumulated under-budget surplus |
| last_savings_processed_week | String | Prevents double-processing savings |
| monitored_app_packages | StringSet | Apps whose notifications are monitored |

### Firebase Realtime Database

```
weekly_totals/
├── transactions/{createdAt}/
│   ├── weekStartDate, category, amount, isAdjustment, createdAt
├── categories/{name}/
│   ├── name, displayName, color, isSystem
└── budget/
    ├── amount, isSet
```

---

## User Flows

### 1. First-Time Setup

```
LoginActivity (Google Sign-In)
  → Firebase Auth with ID token
  → Email whitelist check (only 2 allowed emails)
  → BudgetSetupActivity (enter weekly budget)
  → Default categories seeded into Room + Firebase
  → MainActivity
```

Only whitelisted emails are permitted. If a non-whitelisted email signs in, the user is signed out immediately.

### 2. Weekly Transaction Tracking (Main Screen)

The main screen shows the current week (Saturday to Friday) with:
- A circular **BudgetGaugeView** showing spent vs. budget (blue → orange → red)
- A category spinner + amount input for adding transactions
- A RecyclerView listing all transactions for the current week

```
User enters amount, selects category, taps Add
  → MainViewModel.addTransaction()
  → TransactionDao.insert()
  → FirebaseSyncManager.pushTransaction()
  → LiveData updates RecyclerView + gauge automatically
```

Tapping a transaction opens an edit dialog (amount + category).
Long-pressing a transaction opens a delete confirmation dialog.
Adjustment entries can also be edited (amount only) and deleted.

### 3. Week Rollover (Automatic)

Runs once at app launch via `MainViewModel.checkWeekRollover()`:

```
1. Apply any pending budget change (from Settings)
2. Calculate previous week's total spending
3. If previous week OVER budget:
     → Insert ADJUSTMENT transaction for the overage into current week
     → Uses atomic insertAdjustmentIfNotExists() to prevent duplicates
4. If previous week UNDER budget:
     → Add surplus to cumulative savings
5. Mark this week as processed (prevents double-counting)
```

The adjustment appears as an italicized orange entry labeled "Adjustment (over budget)".

### 4. SMS Auto-Detection

When enabled, incoming bank SMS messages are intercepted and parsed for transaction amounts.

```
Bank SMS received
  → SmsBroadcastReceiver.onReceive()
  → SmsTransactionDetector.detect(messageBody)
       Skips: credit/refund/deposit keywords
       Matches: purchase/debit/charged keywords + dollar amount
       Supports: $123.45, CAD 123.45, 123,45$ (French), Amount: 123.45
  → TransactionPromptActivity (floating dialog)
       Shows raw message, pre-filled amount, category picker
  → User confirms → Transaction inserted into current week
```

### 5. App Notification Monitoring

Similar to SMS detection, but reads notifications from selected apps (e.g., banking apps).

```
Notification posted from a monitored app
  → TransactionNotificationListenerService.onNotificationPosted()
  → Extracts title + text from notification
  → SmsTransactionDetector.detect() (same regex engine)
  → TransactionPromptActivity if amount detected
```

Requires the user to grant "Notification access" permission in Android Settings. Monitored apps are selected in MonitoredAppsActivity.

### 6. Multi-Device Sync

All data syncs in real-time via Firebase Realtime Database. `FirebaseSyncManager` handles bidirectional sync with suppression flags to prevent infinite loops.

```
Device A adds transaction
  → Room insert + Firebase push
  → Device B's ValueEventListener fires
  → syncTransactionsFromFirebase() inserts/updates locally
  → LiveData updates Device B's UI
```

On startup, `pushAllLocalData()` uploads everything local to Firebase, and listeners are registered for transactions, categories, and budget. The sync logic:
- Matches transactions by `createdAt` timestamp
- Inserts missing remote entries, updates mismatched fields
- Deletes local entries not found in Firebase (skipped if Firebase is empty)
- Special handling for adjustments: at most one per week, aligned by `createdAt`

### 7. History & Analytics

`HistoryActivity` shows spending breakdown by category with a pie chart.

- **Month mode**: Select any month in the current year
- **Year mode**: Select from all years with data

Adjustment transactions are excluded from history calculations. Categories are shown with their color dot, display name, and total amount.

### 8. Budget Update

```
SettingsActivity → Enter new budget → Save
  → Stored as pending_budget
  → Synced to Firebase
  → Applied at next Saturday (checkWeekRollover → applyPendingBudget)
```

Budget changes take effect at the start of the next week, not immediately.

### 9. Category Management

`ManageCategoriesActivity` allows creating, editing, and deleting custom categories. Each category has a name, display name, and color (10 preset colors). The system `ADJUSTMENT` category cannot be modified. Changes sync to Firebase for multi-device consistency.

### 10. Reset All Data

From Settings, a full reset:
- Clears all transactions and categories from Room
- Clears SharedPreferences (budget, savings, etc.)
- Clears Firebase (propagates deletion to all synced devices)
- Redirects to BudgetSetupActivity

---

## Key Implementation Details

### Week Definition
A week runs **Saturday to Friday**. `WeekCalculator` uses `java.time.LocalDate` with `TemporalAdjusters.previous(DayOfWeek.SATURDAY)` to compute week boundaries.

### Adjustment Deduplication
A race condition exists between `checkWeekRollover()` and Firebase sync at startup — both run as concurrent coroutines and can independently insert an adjustment. This is handled by:
1. `TransactionDao.insertAdjustmentIfNotExists()` — a `@Transaction`-annotated method that atomically checks and inserts
2. Firebase sync checks `getAdjustmentForWeek()` before inserting a remote adjustment and updates the existing local one instead of creating a duplicate

### Firebase Key Strategy
Transactions use `createdAt` (epoch millis) as the Firebase node key. This allows matching local and remote entries without exposing Room's auto-generated IDs.

### Offline Support
Firebase Realtime Database persistence is enabled in `WeeklyTotalsApp.onCreate()`. Room provides the local source of truth, with Firebase syncing changes when connectivity is available.

---

## Build & Test

```bash
make build     # Build APK and copy to ~/Downloads/WeeklyTotals.apk
make test      # Run unit tests
make install   # Install APK via adb
make run       # Build, install, and launch
make clean     # Clean build artifacts
```

### Test Files

| Test | Purpose |
|------|---------|
| `AdjustmentTest.kt` | Adjustment deduplication, update, delete (Room + Robolectric) |
| `WeekCalculatorTest.kt` | Saturday-based week boundary calculations |
| `SmsTransactionDetectorTest.kt` | Regex parsing for bank SMS formats |
| `MainActivityTest.kt` | Basic sanity checks |
