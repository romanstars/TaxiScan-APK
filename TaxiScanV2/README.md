# TaxiScan 2.0 — Android Studio project

Native Android starter project based on the supplied TaxiScan screens (dark interface, taxi yellow, radar green). The project is designed as a local-first taxi offer assistant.

## Features in this project

- Home screen with radar status, offer history, quick links, and a manual fare calculator
- Android Accessibility Service that, after the driver explicitly enables it, reads visible text only in detected Bolt, Uklon, and Uber app windows
- Local parser for visible fare and route distance, plus a profitable / unprofitable estimate using driver commission and cost-per-kilometer settings
- Floating overlay with the estimate when Android's “display over other apps” permission is granted
- Filters for minimum fare, net ₴/km, estimated ₴/hour, maximum pickup distance, and minimum passenger rating
- Driver settings for monitored services and sound alerts
- Permission readiness page for Accessibility, overlay, and battery optimization
- 24-hour trial screen and Premium screen mockup
- Local event journal and per-service counts, stored in app-private SharedPreferences
- GitHub Actions workflow that builds and uploads a debug APK artifact on push or manual run

## Build locally

1. Open this folder in Android Studio with JDK 17.
2. Let Gradle sync and install Android SDK Platform 35 if prompted.
3. Run **Build → Build APK(s)**, or run `gradlew.bat assembleDebug` on Windows / `./gradlew assembleDebug` on macOS or Linux.

Package: `com.taxiscan.app` · Min Android: 7.0 (API 24) · Target SDK: 35.

## Important setup notes

- The driver must enable TaxiScan under Android **Settings → Accessibility** and grant **Display over other apps** before the live panel can appear. The app displays a disclosure before opening Accessibility settings.
- Screen text is analyzed locally; this project does not send screen contents to a server. Supported apps are recognized by package name containing `bolt`/`mtakso`, `uklon`, or `uber`.
- Offer layouts vary by app version and locale. The parser uses visible text patterns for currency and kilometers; verify it with real offers on the target phone before relying on calculations.
- Google Play Billing is not wired to a Play Console product yet. The Premium page is a UI prototype and its purchase buttons explain that setup is still needed.
- This source has not been built or tested on a device in this environment. The bundled GitHub Actions workflow can create a debug APK after the project is pushed to GitHub.
