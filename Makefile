export ANDROID_HOME := $(HOME)/android-sdk

PKG := com.example.weeklytotals

.PHONY: build test install clean emulator run run-sample-data

build:
	./gradlew assembleDebug
	rm -f $(HOME)/Downloads/WeeklyTotals.apk
	cp app/build/outputs/apk/debug/app-debug.apk $(HOME)/Downloads/WeeklyTotals.apk
	@echo "APK copied to ~/Downloads/WeeklyTotals.apk"

test:
	./gradlew testDebugUnitTest

install:
	adb install app/build/outputs/apk/debug/app-debug.apk

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
