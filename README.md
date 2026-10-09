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

## Signing key

Android only lets an app update itself if the new APK is signed with the same key as the old one. Debug builds
are signed with `~/.android/debug.keystore` on this Mac. **Back that file up.** If it is lost, the next APK will
not install over the old one, and the app would have to be uninstalled (losing records unless a backup was made).

## How it is organised

| Folder | What is in it |
| --- | --- |
| `data/` | Database tables, the append-only triggers, and the repository (the only code that writes) |
| `domain/` | Interest maths, hash chain, money and date formatting. Plain Kotlin, fully unit-tested |
| `backup/` | Backup file format, share, save and restore |
| `security/` | Optional PIN and lock screen state |
| `ui/` | Screens, theme and the large-text components |
| `res/values/strings.xml` | Every word the user sees. Copy to `values-hi/` to add Hindi |
