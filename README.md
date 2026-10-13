# Jarvis Voice Assistant

Android voice assistant app inspired by JARVIS. It listens for the wake word, calls Gemini for natural-language understanding, and can perform safe phone actions like launching apps, calling contacts, controlling volume, flashlight, and screen actions.

## Project structure

- `app/` — Android app module
- `build.gradle.kts` — root Gradle config
- `settings.gradle.kts` — project settings
- `gradle.properties` — Gradle properties

## Features

- Wake word: "Jarvis"
- Audio capture + Gemini response parsing
- Hinglish voice replies
- App launch, call, volume, torch, lock/unlock controls
- Accessibility service for system actions
- Compose UI for home, setup, guide, and settings screens

## Setup

1. Open the project in Android Studio.
2. Add your credentials in the app Settings screen:
   - Gemini API key
   - Gemini model
   - Picovoice Porcupine access key
3. Grant required permissions when the app asks.
4. Build and run on Android 11+.

## Notes

This project is intended for real Android devices and requires the proper OS permissions and accessibility configuration to work fully.
