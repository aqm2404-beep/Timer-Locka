# TimerLock

TimerLock is a secure Android countdown/kiosk prototype.

## Current version

**v3.0.0** (`versionCode 3`)

Key behavior:
- Countdown state persists independently from the UI.
- When the timer expires, TimerLock persists `EXPIRED` first and then requests the Android screen lock.
- Android Device Admin access and a secure Android PIN/pattern/password are required before a locked countdown can start.
- `Admin > System Lock Access > Test Screen Lock` can verify `lockNow()` before using a timer.
- Opening Admin requires one TimerLock admin-password authentication. Admin actions do not immediately request the same TimerLock password again within that admin session.
- Android lock-screen credentials and the TimerLock admin password are intentionally separate credentials.
- Reboot must not silently reset an expired timer to IDLE.

## Android package

`com.timerlock.secure`

## Build

GitHub Actions builds the debug APK from `main` using Android SDK 35, JDK 17 and Gradle 8.9.

> This is a debug/test build. Strong kiosk enforcement requires appropriate Android Device Owner / managed-device provisioning. Normal Android mode cannot guarantee blocking force-stop, uninstall, Settings, shutdown, or OEM-specific escape paths.
