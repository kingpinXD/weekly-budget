#!/bin/sh
# Seed sample data into the Weekly Totals app on the emulator/device.
# Runs via: adb shell "run-as com.example.weeklytotals sh /data/local/tmp/seed-sample-data.sh"

PKG_DIR="/data/data/com.example.weeklytotals"
DB="$PKG_DIR/databases/weekly_totals.db"
PREFS_DIR="$PKG_DIR/shared_prefs"
PREFS_FILE="$PREFS_DIR/weekly_totals_prefs.xml"

# Create dirs if needed
mkdir -p "$PKG_DIR/databases"
mkdir -p "$PREFS_DIR"

# Write budget prefs (300.00 CAD)
printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n    <long name="budget" value="4648928676283416576" />\n    <boolean name="is_budget_set" value="true" />\n</map>\n' > "$PREFS_FILE"

# Create tables (matches Room schema v2)
sqlite3 "$DB" "CREATE TABLE IF NOT EXISTS transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, weekStartDate TEXT NOT NULL, category TEXT NOT NULL, amount REAL NOT NULL, isAdjustment INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL);"
sqlite3 "$DB" "CREATE TABLE IF NOT EXISTS categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, displayName TEXT NOT NULL, color TEXT NOT NULL, isSystem INTEGER NOT NULL DEFAULT 0);"
sqlite3 "$DB" "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT);"

# Set Room identity hash and DB version so Room recognizes this as a valid v2 database
sqlite3 "$DB" "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '2cc27ab9403cdfb8321b4aad264395c6');"
sqlite3 "$DB" "PRAGMA user_version = 2;"

# Clear existing data
sqlite3 "$DB" "DELETE FROM transactions;"
sqlite3 "$DB" "DELETE FROM categories;"

# Seed default categories
sqlite3 "$DB" "INSERT INTO categories (name, displayName, color, isSystem) VALUES ('GROCERY', 'Grocery', '#4CAF50', 0);"
sqlite3 "$DB" "INSERT INTO categories (name, displayName, color, isSystem) VALUES ('GAS', 'Gas', '#2196F3', 0);"
sqlite3 "$DB" "INSERT INTO categories (name, displayName, color, isSystem) VALUES ('ENTERTAINMENT', 'Entertainment', '#FF9800', 0);"
sqlite3 "$DB" "INSERT INTO categories (name, displayName, color, isSystem) VALUES ('TRAVEL', 'Travel', '#9C27B0', 0);"
sqlite3 "$DB" "INSERT INTO categories (name, displayName, color, isSystem) VALUES ('ADJUSTMENT', 'Adjustment', '#FF5722', 1);"

# Seed sample transactions
sqlite3 "$DB" "INSERT INTO transactions (weekStartDate, category, amount, isAdjustment, createdAt) VALUES ('2026-02-07', 'GROCERY', 45.50, 0, 1738900000000);"
sqlite3 "$DB" "INSERT INTO transactions (weekStartDate, category, amount, isAdjustment, createdAt) VALUES ('2026-02-07', 'GROCERY', 23.75, 0, 1738910000000);"
sqlite3 "$DB" "INSERT INTO transactions (weekStartDate, category, amount, isAdjustment, createdAt) VALUES ('2026-02-07', 'GAS', 60.00, 0, 1738920000000);"
sqlite3 "$DB" "INSERT INTO transactions (weekStartDate, category, amount, isAdjustment, createdAt) VALUES ('2026-02-07', 'ENTERTAINMENT', 15.99, 0, 1738930000000);"
sqlite3 "$DB" "INSERT INTO transactions (weekStartDate, category, amount, isAdjustment, createdAt) VALUES ('2026-02-07', 'TRAVEL', 120.00, 0, 1738940000000);"
sqlite3 "$DB" "INSERT INTO transactions (weekStartDate, category, amount, isAdjustment, createdAt) VALUES ('2026-01-31', 'GROCERY', 55.00, 0, 1738300000000);"
sqlite3 "$DB" "INSERT INTO transactions (weekStartDate, category, amount, isAdjustment, createdAt) VALUES ('2026-01-31', 'GAS', 45.00, 0, 1738310000000);"
sqlite3 "$DB" "INSERT INTO transactions (weekStartDate, category, amount, isAdjustment, createdAt) VALUES ('2026-01-31', 'ENTERTAINMENT', 30.00, 0, 1738320000000);"
sqlite3 "$DB" "INSERT INTO transactions (weekStartDate, category, amount, isAdjustment, createdAt) VALUES ('2026-01-24', 'GROCERY', 80.00, 0, 1737700000000);"
sqlite3 "$DB" "INSERT INTO transactions (weekStartDate, category, amount, isAdjustment, createdAt) VALUES ('2026-01-24', 'TRAVEL', 200.00, 0, 1737710000000);"
sqlite3 "$DB" "INSERT INTO transactions (weekStartDate, category, amount, isAdjustment, createdAt) VALUES ('2025-12-27', 'GROCERY', 90.00, 0, 1735300000000);"
sqlite3 "$DB" "INSERT INTO transactions (weekStartDate, category, amount, isAdjustment, createdAt) VALUES ('2025-12-27', 'ENTERTAINMENT', 50.00, 0, 1735310000000);"

echo "Sample data seeded successfully"
