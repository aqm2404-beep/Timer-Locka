# TimerLock — Galaxy Store Listing Draft

## App title
TimerLock

## Price
USD 0.99 standard price

## Suggested category
Utilities / Productivity

## Short description
Secure countdown timer that locks the device when time expires.

## Full description
TimerLock is a focused countdown utility designed for situations where a timer must keep running until the configured duration is complete.

Set the countdown, start the timer, and TimerLock keeps the active timer state persistent. When the countdown reaches zero, TimerLock enters an expired state and can request the Android lock screen when Device Admin access has been enabled.

Key features:
- Secure countdown with persistent timer state
- Optional Android screen lock when time expires
- Device Admin access is requested only for the screen-lock function
- Timer state survives app relaunch and device reboot
- Admin-controlled reset and configuration
- Local system log for timer and security events
- No account required
- No advertising
- No analytics or cloud tracking
- No internet permission

Important: Android screen locking requires Device Admin access and an Android PIN, pattern, or password configured on the device. TimerLock clearly asks the user before Device Admin is activated.

TimerLock does not replace Android's own lock-screen security. The Android device credential and the TimerLock administrator password remain separate security layers.

## Review team note
TimerLock uses Android DevicePolicyManager `lockNow()` through an explicitly activated Device Admin receiver with the `force-lock` policy. The user is shown Android's standard Device Admin confirmation UI before activation. The app can be used as a timer without sending any data to external servers.

## Data Safety draft
Data collected: None
Data shared with third parties: None

Local-only data stored on the device:
- timer state and configuration
- administrator password hash and salt
- local TimerLock event log

The app does not request the INTERNET permission and does not transmit these values off-device.

## Suggested search terms
countdown, timer, lock timer, screen lock timer, focus timer, secure timer, kiosk timer
