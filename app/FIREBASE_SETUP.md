# Firebase Setup Instructions

Follow these steps to connect the app to Firebase.

## 1. Create a Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click "Add project"
3. Name it (e.g., "WeeklyTotals") and follow the prompts
4. Google Analytics is optional -- you can disable it

## 2. Add Android App to Firebase

1. In your Firebase project, click "Add app" and select Android
2. Enter package name: `com.example.weeklytotals`
3. Enter app nickname: "Weekly Totals"
4. Enter your debug SHA-1 fingerprint (see below)
5. Click "Register app"

## 3. Download google-services.json

1. After registering, download the `google-services.json` file
2. Place it in the `app/` directory (i.e., `app/google-services.json`)
3. The app will not build without this file

## 4. Get Your SHA-1 Fingerprint

Run this command from the project root:

```bash
./gradlew signingReport
```

Or manually with keytool:

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

Copy the SHA-1 value and add it to your Firebase Android app settings.

## 5. Enable Google Sign-In Provider

1. In Firebase Console, go to Authentication > Sign-in method
2. Click "Google" provider
3. Enable it
4. Set a support email address
5. Save

## 6. Enable Realtime Database (if needed)

1. In Firebase Console, go to Realtime Database
2. Click "Create Database"
3. Choose a location
4. Start in test mode (or set up rules as needed)

## Summary

After completing these steps, the app will:
- Show a Google Sign-In screen on first launch
- Only allow whitelisted emails to access the app
- Persist the login session so users only sign in once
