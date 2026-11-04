# Nova Launcher Pro

A native Android launcher starter written in Kotlin. It includes a searchable app drawer, local app-folder grouping, an in-launcher Dynamic Island, notification actions after explicit notification-listener opt-in, wallpaper hand-off, and app shortcuts.

## Dynamic Island behavior and permission boundary

- **Inside Nova:** the expandable island displays the latest notification title/text and up to three notification-provided actions.
- **While another app is foreground:** this project does **not** draw a custom island over that app. Android's system notification UI remains responsible for notifications outside Nova. No `SYSTEM_ALERT_WINDOW` permission, overlay service, or Display Over Other Apps access is requested or used.
- To read other apps' notifications, explicitly enable **Nova Dynamic Island** under Android **Settings → Notifications → Notification access** (wording/location varies by device). Nova does not transmit notification content.
- Android 13+ may request `POST_NOTIFICATIONS`. This is separate from notification-listener access; Nova does not post notifications, and the permission is not required for reading them.

## Features

- Registers as a Home launcher; choose Nova as the default Home app in Android settings.
- Searches launchable apps. Tap to launch; long-press for Open, App info, or folder management.
- Assign multiple apps the same folder name from the long-press menu. Folder chips filter the app list, and labels persist locally.
- Expand/collapse the Dynamic Island; notification-provided actions are invoked through their supplied PendingIntents.
- Live clock, wallpaper picker hand-off, and shortcuts to notification access and Android Home settings.
- Uses Android's package visibility `<queries>` declaration for launcher activities.

This is a functional starter launcher, not a replacement for every OEM launcher capability. Folder groups are local app-list filters, not draggable home-screen folder widgets. No custom island is shown over other apps without overlay/system privileges.

## Requirements and setup

- Android Studio with Android SDK Platform 35 installed.
- JDK 17 (Android Studio's bundled JBR is suitable).
- A device/emulator running Android 6.0 (API 23) or newer.

1. Open this repository as a project in Android Studio.
2. Allow Gradle sync and install any prompted Android SDK components.
3. Run the `app` configuration on a device/emulator.
4. Press Home and select **Nova Launcher** as the default Home app (or use Nova settings → Android Home settings).
5. To show notification details/actions in Nova, open ⚙ → **Enable notification access**, then enable **Nova Dynamic Island** in Android Settings. Grant POST_NOTIFICATIONS if prompted on Android 13+; it is not required for reading notifications.
6. To organize apps, long-press an app → **Set or change folder**. Assign the same name to other apps, then choose that folder's chip above the app grid.

## Build a debug APK

From the repository root with JDK 17 and Android SDK configured:

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. If this checkout does not include the Gradle wrapper scripts/JAR, use Android Studio's **Build → Build Bundle(s) / APK(s) → Build APK(s)**, or install a compatible Gradle version and run:

```bash
gradle :app:assembleDebug
```

To create a signed release APK, use Android Studio's **Build → Generate Signed Bundle / APK** wizard. Do not commit signing keys or passwords.

## Permissions

The manifest declares `POST_NOTIFICATIONS` (requested at runtime on Android 13+). The notification-listener service is protected by Android's `BIND_NOTIFICATION_LISTENER_SERVICE` binding permission and can read notification content only after the user enables notification access in system settings. This special access is not a runtime dialog permission. No storage, contacts, location, phone, or overlay permissions are needed. Wallpaper selection is delegated to Android's wallpaper picker.
