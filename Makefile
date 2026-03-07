export ANDROID_HOME := $(HOME)/android-sdk

PKG := com.example.weeklytotals

.PHONY: build test install install-phone check-phone clean emulator run run-sample-data

build:
	./gradlew assembleDebug
	rm -f $(HOME)/Downloads/WeeklyTotals.apk
	cp app/build/outputs/apk/debug/app-debug.apk $(HOME)/Downloads/WeeklyTotals.apk
	@echo "APK copied to ~/Downloads/WeeklyTotals.apk"

test:
	./gradlew testDebugUnitTest

install:
	adb install app/build/outputs/apk/debug/app-debug.apk

check-phone:
	@tmpf=$$(mktemp); \
	adb devices -l 2>/dev/null | tail -n +2 | grep -v '^$$' | grep 'model:' | \
		sed 's/.*model://;s/ .*//' | sort -u > "$$tmpf"; \
	while read -r mdl; do \
		line=$$(adb devices -l </dev/null 2>/dev/null | grep "model:$$mdl " | head -1); \
		serial=$$(echo "$$line" | sed 's/  *device .*//'); \
		model=$$(adb -s "$$serial" shell getprop ro.product.model </dev/null 2>/dev/null | tr -d '\r'); \
		brand=$$(adb -s "$$serial" shell getprop ro.product.brand </dev/null 2>/dev/null | tr -d '\r'); \
		version=$$(adb -s "$$serial" shell dumpsys package $(PKG) </dev/null 2>/dev/null | grep versionName | head -1 | awk -F= '{print $$2}' | tr -d '\r'); \
		echo "$$brand $$model ($$serial) — installed: v$$version"; \
	done < "$$tmpf"; \
	rm -f "$$tmpf"

install-phone: build
	@tmpf=$$(mktemp); \
	adb devices -l 2>/dev/null | tail -n +2 | grep -v '^$$' | grep 'model:' | \
		sed 's/.*model://;s/ .*//' | sort -u > "$$tmpf"; \
	while read -r mdl; do \
		line=$$(adb devices -l </dev/null 2>/dev/null | grep "model:$$mdl " | head -1); \
		serial=$$(echo "$$line" | sed 's/  *device .*//'); \
		model=$$(adb -s "$$serial" shell getprop ro.product.model </dev/null 2>/dev/null | tr -d '\r'); \
		echo "Installing on $$model ($$serial)..."; \
		adb -s "$$serial" install -r app/build/outputs/apk/debug/app-debug.apk </dev/null && \
			echo "  done" || \
			echo "  FAILED"; \
	done < "$$tmpf"; \
	rm -f "$$tmpf"

VENV := .venv/bin/python3
FB := $(VENV) scripts/firebase_tool.py

# Firebase tool shortcuts
fb-tree:
	$(FB) tree

fb-list:
	$(FB) list-txn --week $(WEEK)

fb-savings:
	$(FB) get-savings

fb-split:
	$(FB) list-split

clean:
	./gradlew clean

emulator:
	$(ANDROID_HOME)/emulator/emulator -avd Pixel_API_34 &

run: build
	adb wait-for-device
	adb install app/build/outputs/apk/debug/app-debug.apk
	adb shell am start -n $(PKG)/.LoginActivity

run-sample-data: build
	adb wait-for-device
	adb install app/build/outputs/apk/debug/app-debug.apk
	@echo "=== Stopping app ==="
	adb shell am force-stop $(PKG)
	@echo "=== Clearing DB and budget prefs (preserving auth) ==="
	adb shell "run-as $(PKG) rm -f databases/weekly_totals.db databases/weekly_totals.db-wal databases/weekly_totals.db-shm"
	adb shell "run-as $(PKG) rm -f shared_prefs/weekly_totals_prefs.xml"
	@echo "=== Pushing seed script ==="
	adb push scripts/seed-sample-data.sh /data/local/tmp/seed-sample-data.sh
	@echo "=== Running seed script ==="
	adb shell "run-as $(PKG) sh /data/local/tmp/seed-sample-data.sh"
	@echo "=== Launching app ==="
	adb shell am start -n $(PKG)/.LoginActivity
	@echo "=== Sample data loaded ==="
	@echo "Budget: 650.00 CAD"
	@echo "Current week (Feb 7): Grocery 69.25, Gas 60.00, Entertainment 15.99, Travel 120.00"
	@echo "Last week (Jan 31): Grocery 55.00, Gas 45.00, Entertainment 30.00"
	@echo "Jan 24: Grocery 80.00, Travel 200.00"
	@echo "Dec 27 2025: Grocery 90.00, Entertainment 50.00"
