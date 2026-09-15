# TimerLock 4.0.1 reliability fix

TimerLock now requires Android exact-alarm access before normal app use on Android 12+.

Why: precise expiry is core functionality. Without Alarms & reminders access, Android may defer an inexact background alarm, especially under Doze or aggressive OEM battery management.

Expected flow:
1. Open TimerLock.
2. Enable Alarms & reminders when prompted.
3. Start a timer.
4. Leave TimerLock and use other apps normally.
5. At 00:00, TimerLock requests the Android device lock through Device Admin.
