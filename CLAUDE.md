# Kithara Lyrics & Chords Scraper (Android)

Android port of the Chrome extension `kithara-lyrics-chords-scraper`. Scrapes lyrics and chords from kithara.to, converts to ChordPro-style text with inline chords, then saves (`.txt`) or shares via Android intents. README and UI are Greek.

## Stack
- Kotlin, Android, namespace/applicationId `com.example.kithara` (source package `com.example.kitharascraper`)
- minSdk 29, target/compileSdk 37; version `0.1.7`
- Jetpack Compose (Material 3) plus some XML views/Material, AndroidX SplashScreen
- Jsoup for HTML parsing, Kotlin coroutines
- Gradle (Kotlin DSL), version catalog in `gradle/libs.versions.toml`

## Layout
- `app/src/main/java/com/example/kitharascraper/MainActivity.kt`: the app (UI, scraping, export)
- `CHANGELOG.md` (Greek, Keep a Changelog)

## Commands
- Debug APK: `.\gradlew.bat assembleDebug`
- Release APKs are published as `app-release.apk` on GitHub Releases.

## Conventions
- Greek localization for all user-facing strings; use string resources, not hardcoded text.
- Keep chord-placement logic consistent with the Chrome extension and with `kithara-scraper-v2` (`ChordProParser`).
- Update `CHANGELOG.md` per release.

## Don't
- Don't publish a release without asking first.

## Working across two PCs
This repo is worked on from a home PC and an office PC, synced via GitHub.
- At the start of a session, read `HANDOFF.md` for current status.
- At the end of a session, update `HANDOFF.md`, then commit and push.
