# 🔷 Desmond PTT

A floating Push-to-Talk Android app that sends voice messages to Telegram as **you** (not a bot), using [TDLib](https://github.com/tdlib/td) compiled from source for Android.

Built for talking to AI assistants hands-free — hold the floating button, speak, release, done. The voice message lands in your Telegram chat where your AI can transcribe and respond.

## Features

- **Floating overlay button** — always accessible over any app
- **Push-to-talk UX** — hold to record, release to send
- **TDLib userbot** — sends voice messages as your Telegram account (not a bot)
- **Drag to reposition** — move the button anywhere on screen
- **Smart hold detection** — distinguishes taps, holds, and drags
- **Visual feedback** — recording (red), sending (orange spin), success (green), error (red)
- **Haptic feedback** — vibration on record start/stop
- **Configurable** — hold delay, min recording duration, target chat, webhook URL
- **Encrypted config** — credentials stored in Android EncryptedSharedPreferences
- **First-launch setup wizard** — guided API credential entry
- **Optional webhook** — send audio to a server for transcription (Whisper, etc.)
- **Fallback to Bot API** — if TDLib auth isn't complete, falls back to bot

## Quick Start

### 1. Get Telegram API Credentials

1. Go to [https://my.telegram.org](https://my.telegram.org)
2. Log in with your phone number
3. Go to "API development tools"
4. Create an application — note the **API ID** and **API Hash**

### 2. Configure Credentials

```bash
cp local.properties.template local.properties
# Edit local.properties with your values:
#   telegram.apiId=YOUR_API_ID
#   telegram.apiHash=YOUR_API_HASH
#   telegram.botToken=YOUR_BOT_TOKEN   (optional fallback)
#   telegram.chatId=YOUR_CHAT_ID       (optional fallback)
```

### 3. Install the APK

Build from source (see below) or download from [Releases](https://github.com/Desmond-Adrian-Assistant/desmond-ptt/releases).

> **Compatibility:** ARM64 (arm64-v8a) only — covers ~95% of modern Android phones (2018+). Will not work on 32-bit ARM, x86 emulators, or x86 Chromebooks.

### 4. First Launch

The app will walk you through setup:

1. **Target Chat** — username of the bot/chat to send voice messages to
2. **Webhook** (optional) — URL for server-side transcription

After setup, grant permissions (overlay, microphone, notifications) and tap **Start Floating Button**.

### 5. Use It

- **Hold** the floating button to record
- **Release** to send
- **Drag** to reposition (cancels recording if you move)

## Architecture

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Floating    │────▶│  AudioRecorder   │────▶│ TelegramUploader│
│  Button      │     │  (MediaRecorder) │     │                 │
│  Service     │     │  AAC/M4A         │     │  ┌─ TDLib ──┐   │
└─────────────┘     └──────────────────┘     │  │ (userbot) │   │
                                              │  └───────────┘   │
                                              │  ┌─ Bot API ─┐   │
                                              │  │ (fallback) │   │
                                              │  └───────────┘   │
                                              │  ┌─ Webhook ─┐   │
                                              │  │ (optional) │   │
                                              │  └───────────┘   │
                                              └─────────────────┘
```

- **FloatingButtonService** — foreground service managing the overlay, touch handling, drag vs hold detection
- **AudioRecorder** — records audio via MediaRecorder (AAC in MPEG-4 container)
- **TelegramClient** — TDLib wrapper handling auth flow and message sending
- **TelegramUploader** — orchestrates sending: TDLib → Bot API → Webhook fallback chain
- **AppConfig** — EncryptedSharedPreferences for all credentials and settings
- **MainActivity** — setup wizard + auth flow UI + service toggle

## Building from Source

### Prerequisites

- Android Studio or Android SDK command-line tools
- JDK 17
- NDK (for TDLib compilation)

### 1. Build TDLib for Android

The native TDLib library (`libtdjni.so`) is ~21MB per ABI and not included in this repo. You need to build it yourself:

```bash
# Clone TDLib
git clone https://github.com/tdlib/td.git
cd td

# Follow the official Android build instructions:
# https://github.com/tdlib/td#building-for-android

# The build produces:
#   libtdjni.so (native library)
#   TdApi.java  (Java API bindings)
```

After building, place files:
```
app/src/main/jniLibs/arm64-v8a/libtdjni.so
app/src/main/java/org/drinkless/tdlib/TdApi.java
```

The `Client.java` wrapper is already included in the repo.

### 2. Build the APK

```bash
# Clone this repo
git clone https://github.com/ayedreeean/desmond-ptt.git
cd desmond-ptt

# Build debug APK
./gradlew assembleDebug

# APK will be at app/build/outputs/apk/debug/app-debug.apk
```

## Server-Side Webhook (Optional)

The `server/` directory contains a Node.js webhook server that:
1. Receives audio uploads from the app
2. Transcribes with OpenAI Whisper (local CLI)
3. Optionally sends transcription confirmation via Telegram

```bash
cd server

# Set environment variables
export TELEGRAM_BOT_TOKEN="your-bot-token"
export TELEGRAM_CHAT_ID="your-chat-id"

# Run
node ptt-webhook.js
```

## Settings

Long-press the Start/Stop button in the app to access settings:

| Setting | Default | Description |
|---------|---------|-------------|
| Hold delay | 400ms | How long to hold before recording starts |
| Min recording | 500ms | Minimum recording duration (shorter = discarded) |
| Target chat | — | Telegram username to send voice to |
| Webhook URL | — | Optional server URL for audio transcription |

## Privacy

- **Microphone**: Used only while you hold the PTT button. No background recording.
- **Overlay**: Required for the floating button to appear over other apps.
- **Telegram API**: Your credentials are stored locally in encrypted storage. Voice messages are sent directly to Telegram servers via TDLib — no intermediary.
- **Webhook** (if configured): Audio is sent to your specified server URL for transcription. This is optional and off by default.

## Credits

- [TDLib](https://github.com/tdlib/td) — Telegram Database Library by Telegram
- [OpenAI Whisper](https://github.com/openai/whisper) — speech recognition (server-side transcription)
- Built as part of the [Desmond AI](https://github.com/ayedreeean/desmond-log) project

## License

MIT — see [LICENSE](LICENSE)
