# SANWOLF DAW Privacy Policy

**Effective date:** 11 October 2026

SANWOLF DAW ("the app") is made by SANWOLF ("we", "us"). This policy explains what the app does with your information. In short: your music stays on your phone unless you choose to use the cloud or AI features. We don't sell your data, show ads or track you.

## What stays on your device

- **Projects.** Your songs, patterns, tracks, mix settings and presets are saved in the app's private storage on your phone.
- **Microphone recordings.** The app uses the microphone only while you are recording. Recordings are saved on your phone. They are not uploaded to us, to Firebase or to Google's AI services.
- **Imported audio.** Audio files you import are copied into the app's private storage.
- **Exports.** When you export a song (for example WAV, MP3 or MIDI), the file is saved where you choose or shared with the app you pick. We don't receive a copy.
- **Settings.** Preferences such as metronome and display size are stored on your phone.

Android may include the app's data in your device backup (for example a Google account backup) if you have backups turned on. That is controlled by your device settings, not by us.

## What leaves your device, and why

### Cloud sync and collaboration (optional)

If you use cloud sync, presets in the cloud or collaboration rooms, the app uses **Google Firebase**:

- **Firebase Authentication** creates an anonymous account for you, identified by a random user ID. We don't ask for your name, email or phone number. If you set a producer name, it is saved with that account.
- **Cloud Firestore** stores the projects and presets you sync, under your user ID: project title, tempo, tracks, notes, patterns, automation, mix and mastering settings, and the names of audio files used in the project. **The audio itself is not uploaded.**
- **Collaboration rooms** store the room code, the host's user ID and producer name, the project title, tempo and the producer names of people in the room. Anyone who has the room code can join that room.
- When the app talks to Firebase, Google receives technical information such as your IP address and basic device and app details, as part of providing the service.

Google processes this data for us under the [Firebase terms and privacy information](https://firebase.google.com/support/privacy).

### AI features (optional)

When you use an AI feature (for example AI Studio, the co-producer, the patch generator or the mastering assistant), the app sends a request to the **Google Gemini API**. A request can contain:

- the text you type, such as a style or instruction; and
- a summary of your project, such as its title, tempo, time signature, track names and types, note counts, and mix and mastering settings and loudness readings.

Your recordings and audio files are not sent. If you don't use AI features, nothing is sent to Gemini.

Google's handling of this data depends on the kind of Gemini API access the app uses. On Google's **unpaid (free) tier**, Google may use the content of requests and responses to improve its products, and human reviewers may read it, so don't put personal or confidential information in AI prompts. On the **paid tier**, Google does not use it to improve its products. See the [Gemini API Additional Terms](https://ai.google.dev/gemini-api/terms).

<!-- TODO(SANWOLF): once you know which tier the release build uses, replace the paragraph above with one sentence, e.g. "SANWOLF DAW uses the paid tier of the Gemini API." -->

## What we don't do

- We don't sell or rent your data.
- We don't show ads and don't use advertising IDs.
- We don't use analytics or crash-reporting services.
- We don't collect your location, contacts, photos or files outside the app.

## Security

Data sent to Firebase and to the Gemini API is encrypted in transit (HTTPS/TLS). Cloud projects and presets are stored under your own user ID, and only your account should be able to read them. Collaboration rooms are an exception: anyone with the room code can read them.

<!-- TODO(SANWOLF): no firestore.rules file is in the repo. Make sure the deployed Firestore security rules only allow request.auth.uid == userId on users/{userId}/**, or this statement isn't true. -->

## How long we keep data

- **On your phone:** until you delete it in the app or uninstall the app.
- **In the cloud:** until you delete the project in the app or ask us to delete your data. If you sign out of an anonymous account, you may lose access to its cloud projects. Ask us and we will delete them.
- **AI requests:** we don't store them. Google keeps them as its Gemini API terms describe.

## Your choices and rights

- You can use the app fully offline, without cloud or AI features.
- You can delete projects, including cloud copies, in the app.
- You can deny or revoke microphone access in Android settings. Only recording stops working.
- To ask for a copy of your cloud data, or for it to be deleted, email **sanwolfza@gmail.com**. If possible, include the user ID shown in the app's Cloud sync screen. We will reply within 30 days.

Depending on where you live (for example South Africa (POPIA), the EU/UK (GDPR) or California), you may have more rights, such as access, correction, deletion and objection. Contact us to use them.

## Children

The app is not directed at children under 13, and we don't knowingly collect personal information from them. If you think a child has given us information, contact us and we will delete it.

## Changes

If this policy changes, we will update the effective date above and, for important changes, tell you in the app.

## Contact

SANWOLF, **sanwolfza@gmail.com**
