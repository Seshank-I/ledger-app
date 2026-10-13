# Simple Ledger

An offline Android app for recording money you lend and borrow. Every entry is kept forever: mistakes are
fixed by adding a cancelling entry, never by editing or deleting. Built for older users: large text, big
buttons, plain sentences.

Design doc: https://claude.ai/code/artifact/68c2eba1-7b14-420c-9823-12f02a19714d

## Install on a phone

The ready-made file is `app/build/outputs/apk/debug/app-debug.apk`.

1. Send it to the phone (WhatsApp to yourself, Google Drive, or USB cable).
2. Open it on the phone and allow "Install unknown apps" when Android asks.
   - Samsung: turn off **Auto Blocker** first (Settings → Security and privacy), then turn it back on.
   - iQOO / vivo: allow the security scan and enter the account password if asked.

To update the app later, install the new APK the same way. The records stay, **as long as the APK was built on
this same Mac** (see "Signing key" below).

## Build it yourself

Tools this Mac already has (set up on 9 Oct 2026):

| Tool | Where |
| --- | --- |
| Java 17 (Amazon Corretto) | `~/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk` |
| Android SDK | `~/Library/Android/sdk` |
| Gradle | downloaded automatically by `./gradlew` |

```sh
cd ~/Projects/ledger-app
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew assembleDebug        # builds the APK
./gradlew testDebugUnitTest    # runs the tests
```

Or open the folder in Android Studio and press ▶ Run.

## Google Drive automatic backup: one-time setup

The app backs up daily to the user's own Google Drive, into a folder "Simple Ledger Backups", keeping the newest
30 copies. It only uploads when something changed. It uses Google's narrow `drive.file` permission, so it can only
see files it created, never the rest of the Drive.

Google only lets the app ask for Drive access after this one-time setup in your Google account
(about 10 minutes, free):

1. Open https://console.cloud.google.com and create a project, e.g. "Simple Ledger".
2. **APIs & Services → Library** → search "Google Drive API" → **Enable**.
3. **Google Auth Platform** (called "OAuth consent screen" in older menus):
   - App name "Simple Ledger", your email as support and developer contact.
   - Audience: **External**, then **Publish app** so it is "In production". Left in "Testing", Google expires the
     sign-in every 7 days and the daily backup would stop. `drive.file` is a non-sensitive permission, so
     publishing should not require Google's review.
4. **Clients → Create client → Android**:
   - Package name: `com.simpleledger.app`
   - SHA-1: `C0:FA:B3:2E:32:56:A2:30:8C:84:B6:81:E5:A7:7B:2E:66:D0:4D:92`
     (from `~/.android/debug.keystore`; if that file ever changes, this must change too)
5. On each phone: Settings in the app → **Connect Google Drive** → choose the Google account → Allow.

No code change is needed after the setup. Until it is done, "Connect Google Drive" shows a message that Drive
could not be connected.

## Voice

The 🎤 Speak button uses the phone's own speech recognizer (Google or Samsung), so the app needs no microphone
permission. Settings → Voice language chooses English or Telugu. The app reads entries back aloud in English and
never saves anything from voice without the usual confirm screen.

## Signing key

Android only lets an app update itself if the new APK is signed with the same key as the old one. Debug builds
are signed with `~/.android/debug.keystore` on this Mac. **Back that file up.** If it is lost, the next APK will
not install over the old one, and the app would have to be uninstalled (losing records unless a backup was made).

## How it is organised

| Folder | What is in it |
| --- | --- |
| `data/` | Database tables, the append-only triggers, and the repository (the only code that writes) |
| `domain/` | Interest maths, hash chain, money and date formatting. Plain Kotlin, fully unit-tested |
| `backup/` | Backup file format, share, save, restore, and the daily Google Drive upload |
| `security/` | Optional PIN and lock screen state |
| `share/` | Draws a person's statement as a picture and sends it to WhatsApp |
| `voice/` | Voice commands: English/Telugu sentence parser, speech input, read-aloud |
| `ui/` | Screens, theme and the large-text components |
| `res/values/strings.xml` | Every word the user sees. Copy to `values-hi/` to add Hindi |
