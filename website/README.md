# TimerLock website v4.3.0

Production source for timerlock-free.vercel.app.

## Measurement source of truth

- **Website visitors / page views:** Vercel Web Analytics when enabled on the production project.
- **Actual APK downloads:** GitHub Releases asset `download_count` for `TimerLock-v4.3.0.apk`.
- **Interaction events:** `APK Download Click` and `Donation Click` are emitted to Vercel Analytics where custom events are supported by the active Vercel plan.
- **Donation:** Ko-fi at https://ko-fi.com/timerlock, shown as a large optional-support CTA.
- **Android app privacy:** the Android app itself remains offline and contains no web analytics SDK.

The direct GitHub APK URL is intentionally retained to avoid the earlier Vercel download-proxy/install issue.
