# TimerLock — Galaxy Store Submission Checklist

## 1. Seller account
- Create or use a Samsung Account.
- Register in Galaxy Store Seller Portal.
- Confirm Country/Region is Malaysia before completing Seller Portal registration.
- Apply for Commercial Seller Status. This is required for distributing both free and paid Android apps.
- Use financial information that matches the seller identity. Samsung documentation notes PayPal is often the easiest option for financial information.

## 2. App registration
- Add New App in Seller Portal.
- App title: TimerLock
- Package: com.timerlock.secure
- Upload the Galaxy Store AAB produced by this repository.
- When Seller Portal asks for AAB signing, use Galaxy Store managed signing unless there is a specific reason to provide another signing key.

## 3. Price
- Select Paid.
- Standard price target: USD 0.99.
- Review Samsung's automatically converted local prices before submission.

## 4. App information
Use `STORE_LISTING.md` as the initial English listing copy.

Required marketing items should include:
- TimerLock application icon
- phone screenshots showing IDLE, ACTIVE, EXPIRED and System Lock Access states
- support email
- privacy policy URL

## 5. Permissions and review disclosure
Explain that Device Admin is optional and explicitly activated by the user through Android's Device Admin confirmation UI.

Purpose:
- request `DevicePolicyManager.lockNow()` when a configured countdown expires
- allow the administrator to test the system lock function

TimerLock does not read the user's Android lock-screen PIN, pattern, password or biometrics.

## 6. Data Safety
Current application design:
- data collected: none
- data shared: none
- internet permission: none
- analytics: none
- advertising: none
- account/login: none

Local-only information includes timer state, settings, local event logs, and a salted hash of the TimerLock administrator password.

Re-check these answers if analytics, crash reporting, cloud backup, advertising, payments, or any network SDK is added later.

## 7. Compatibility
Current release targetSdk: 35.
Galaxy Store currently requires target API >= 33.
TimerLock is a Java-only Android app and does not package native `.so` libraries.

## 8. Testing before submission
Test on at least one current Samsung Galaxy phone:
- install and first-run password creation
- enable Device Admin
- Test Screen Lock
- countdown expiry with screen ON
- countdown expiry with screen OFF
- Android unlock after expiry
- TimerLock remains EXPIRED after Android unlock
- admin reset uses one TimerLock password session
- reboot while ACTIVE
- reboot while EXPIRED
- notification permission allowed and denied
- alarm/vibration behavior

## 9. Recommended release path
1. Seller Portal account and Commercial Seller Status
2. Upload AAB
3. Open/closed beta on Samsung devices
4. Fix any validation feedback
5. Submit for review
6. Publish at USD 0.99
