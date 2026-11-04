# Nova Launcher Pro

A native Android launcher starter written in Kotlin. It includes a searchable app drawer, a home-screen Dynamic Island that can display notification content/actions after explicit notification-listener opt-in, wallpaper hand-off, and app shortcuts/context actions.

## Dynamic Island behavior and permission boundary

- **Inside Nova:** the expandable island is part of the launcher home screen and can display the latest notification title/text and up to three actions.
- **While another app is foreground:** this project does **not** draw a custom island over that app. Android's system notification UI remains responsible for notifications outside Nova. No `SYSTEM_ALERT_WINDOW` permission, overlay service, or Display Over Other Apps access is requested or used.
- The user must turn on **Nova Dynamic Island** under Android **Settings → Notifications → Device & app notifications / Notification access** for the island to read other apps' notifications. Wording and location vary by Android/device version. Nova does not transmit notification content.
- Android 13+ also asks for `POST_NOTIFICATIONS`; that runtime permission is for Android notification posting and is not the same as notification-listener access. This project doesn't post notifications itself.

## Features

- Registers as a Home launcher; choose Nova as the default Home app in Android settings.
- Lists installed launchable apps, de-duplicates packages, and filters by app name.
- Tap an app to launch it. Long-press for Open, App info, or a locally saved folder label.
- Expand/collapse the Dynamic Island; notification-provided actions are invoked through their supplied PendingIntents.
- Live 24-hour clock, wallpaper picker hand-off, and shortcuts to notification access and Android Home settings.
- Uses Android's package visibility `<queries>` declaration for launcher activities.

This is a functional starter launcher, not a full replacement for every OEM launcher capability. Folder labels are local metadata and do not yet create multi-app folder widgets. No custom island is shown on top of other apps without overlay/system privileges.

## Requirements and setup

- Android Studio with Android SDK Platform 35 installed.
- JDK 17 (Android Studio's bundled JBR is suitable).
- A device/emulator running Android 6.0 (API 23) or newer.

1. Open this repository as a project in Android Studio.
2. Allow Gradle sync and install any prompted Android SDK components.
3. Run the `app` configuration on a device/emulator.
4. Press Home and select **Nova Launcher** as the default Home app (or use Nova settings → Android Home settings).
5. To display notifications in the island, open Nova's ⚙ settings → **Enable notification access**, then enable **Nova Dynamic Island** in Android Settings. Grant POST_NOTIFICATIONS if prompted on Android 13+; it is not required for reading notifications.

## Build a debug APK

From the repository root with JDK 17 and Android SDK configured:

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. If this checkout does not have the Gradle wrapper scripts/JAR, use Android Studio's **Build → Build Bundle(s) / APK(s) → Build APK(s)**, or install Gradle 8.9+ and run:

```bash
gradle :app:assembleDebug
```

To create a signed release APK, configure a signing key in Android Studio's **Build → Generate Signed Bundle / APK** wizard. Do not commit signing keys or passwords.

## Permissions

The manifest declares `POST_NOTIFICATIONS` (requested at runtime on Android 13+) and the notification-listener service is protected by Android's `BIND_NOTIFICATION_LISTENER_SERVICE` binding permission. Notification listener access is an explicit user-controlled special access setting, not a runtime dialog permission. No storage, contacts, location, phone, or overlay permissions are needed. Wallpaper selection is delegated to Android's wallpaper picker.
