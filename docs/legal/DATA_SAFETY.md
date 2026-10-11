# Play Console "Data safety": cheat sheet for SANWOLF DAW

Based on a code scan of the `feature/fixes-and-compact-ui` branch on 2026-10-11. **Re-check this whenever you add an SDK or a feature.** This is not legal advice.

## What the scan found

- **Manifest permissions:**
  - `INTERNET`
  - `RECORD_AUDIO`
  - `MODIFY_AUDIO_SETTINGS`
  - There are no storage, location, contacts, camera, phone or notification permissions.
- **SDKs actually called in code:**
  - Firebase Auth (anonymous sign-in only, plus an optional display name).
  - Cloud Firestore (`users/{uid}/projects`, `users/{uid}/presets` and `collab_rooms/{roomId}`).
  - The Gemini API, called directly over REST with OkHttp at `generativelanguage.googleapis.com`. Requests contain text only: prompts and project summaries. No audio is sent; Lyria returns audio.
- **SDKs in Gradle but not called anywhere:**
  - `firebase-ai`
  - `firebase-appcheck-recaptcha` and `firebase-appcheck-debug`
  - `androidx.credentials` and `googleid` (Sign in with Google)
  - Retrofit, Coil and Room (Room is linked but no `@Database` was found)

  If you wire any of these up, update this sheet and the privacy policy. If you turn on App Check, add "Device or other IDs: App functionality / Fraud prevention".
- **Not present:** analytics, Crashlytics, ads, advertising ID, location, billing.
- **Local only:**
  - Projects (`filesDir/projects`)
  - Imported samples
  - Recordings
  - SharedPreferences
  - `allowBackup="true"`, so Android Auto Backup may copy app data to the user's Google account. That is Google's backup, not collection by you.

## Top-level answers

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (Firebase and Gemini use HTTPS/TLS) |
| Do you provide a way for users to request that their data is deleted? | **Yes**: by email to sanwolfza@gmail.com, and cloud projects can be deleted in the app |

## Data types

"Collected" means it leaves the device to you or to your service providers. Data processed by a service provider on your behalf (Firebase, Gemini API) counts as **collected, not shared**, under Play's definitions. Data handled only on the device is neither.

| Data type (Play category) | Collected? | Shared? | Optional? | Purpose(s) | Why |
|---|---|---|---|---|---|
| **Personal info › User IDs** | Yes | No | Yes (only with cloud sync or collab) | App functionality, Account management | Anonymous Firebase UID. Projects and presets are stored under it. |
| **Personal info › Name** | Yes | No | Yes | App functionality | The optional producer display name, stored in the Firebase Auth profile and in collab rooms. |
| **Audio › Voice or sound recordings** | **No** | No | n/a | n/a | Mic recordings stay on the device. Nothing uploads audio. Play counts only data that leaves the device. |
| **Audio › Music files** | **No** | No | n/a | n/a | Imported audio stays local. Firestore stores only file names and URIs. |
| **App activity › Other user-generated content** | Yes | No | Yes | App functionality | Synced projects: title, notes, patterns, mix settings and file names (Firestore). Also AI prompts and project summaries sent to the Gemini API. |
| **App activity › Other actions** | No | No | n/a | n/a | No analytics. |
| **App info and performance › Crash logs / Diagnostics** | No | No | n/a | n/a | No Crashlytics. Firebase SDKs may send their own minimal technical telemetry. Check Firebase's data disclosure page and tick it if Google lists it for Auth or Firestore. |
| **Device or other IDs** | Probably not | No | n/a | n/a | Not used in code. Firebase Auth and Firestore may use a Firebase installation ID. See Firebase's "Data disclosure" guidance for the Play Data safety form: https://firebase.google.com/docs/android/play-data-disclosure. |
| Location, Contacts, Photos/videos, Messages, Calendar, Health, Financial, Web history | No | No | n/a | n/a | Not accessed. |

**Ephemeral processing:** you may mark the Gemini prompts as "processed ephemerally" only if you use the **paid** Gemini API tier. On the free tier, Google may keep and use them to improve its products, so they are not ephemeral.

## Other Play Console items

- **Privacy policy URL:** host `docs/legal/PRIVACY.md`, for example on GitHub Pages or a Google Site, and paste the public URL.
- **Account deletion (Play requirement):** Play requires a deletion path when an app lets users create an account. Anonymous Firebase accounts are a grey area. Give the email route (sanwolfza@gmail.com) in the form and the "delete account" web URL field. Adding an in-app "Delete my cloud data" button is the cleanest fix.
- **Microphone:** `RECORD_AUDIO` is requested at runtime for recording only. No special declaration is needed beyond the normal permission prompt.
- **Target audience:** choose 13+ (or 18+), not children. The privacy policy says the app is not directed at under-13s.
- **AI-generated content:** under Play's policy, apps that generate content with AI should let users report offensive output. Consider adding a "Report" option on AI results.
