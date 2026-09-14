# TimerLock

TimerLock is a free Android countdown timer with password-protected controls and optional Android system screen locking when the timer reaches zero.

**Official website:** https://timerlock-free.vercel.app/

**Download:** https://timerlock-free.vercel.app/

## Current version

**v4.0.0 Preview** (`versionCode 4`)

Key behavior:
- Countdown state persists independently from the UI.
- When the timer expires, TimerLock persists `EXPIRED` first and then requests the Android screen lock.
- Android Device Admin access and a secure Android PIN/pattern/password are required before a locked countdown can start.
- `Admin > System Lock Access > Test Screen Lock` can verify `lockNow()` before using a timer.
- Opening Admin requires one TimerLock admin-password authentication. Admin actions do not immediately request the same TimerLock password again within that admin session.
- Android lock-screen credentials and the TimerLock admin password are intentionally separate credentials.
- Reboot must not silently reset an expired timer to IDLE.
- No ads, no account required, and the current Android build works offline.

## Android package

`com.timerlock.secure`

## Public release

The current public test release is available through the official website and GitHub Releases.

> This is a preview/debug-signed test build. Strong kiosk enforcement requires appropriate Android Device Owner / managed-device provisioning. Normal Android mode cannot guarantee blocking force-stop, uninstall, Settings, shutdown, or OEM-specific escape paths.
