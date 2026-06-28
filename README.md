# DrawThings

[![Version](https://img.shields.io/badge/version-v1.1.0-blue.svg)](https://github.com/alienindisgui-se/drawthings)

Android image annotation app built with **Jetpack Compose**. Pick a photo, draw on it, and export the result directly to your gallery.

## About

A lightweight image editor for quick annotations. Import any photo, add freehand strokes, circles, or text overlays, adjust borders, and export the finished image — all in a single Compose-first Android app.

## Features

- **Freehand drawing** with smooth Bézier interpolation
- **Arrow heads** on strokes
- **Hollow circles** with preset sizes
- **Text overlays** with configurable size
- **Collage export** — 2×2 grid layout for larger outputs
- **Color palette** — limited curated set for fast annotation
- **Auto-compressed export** to gallery, targeting **≤3 MiB** (normal) or **≤10 MiB** (collage)

## Quick start

```bash
# Open in Android Studio (Hedgehog+ recommended)
# - Sync Gradle
# - Run on device / emulator (minSdk 24, targetSdk 36)
```

## Tech stack

- **Kotlin** + **Jetpack Compose** UI
- **Material 3** theming
- **MediaStore** export
- **Gradle** (version catalog)
- **GitHub Actions** for automated builds and releases

## How it works

The editor surface is a single `Canvas` composable. Interactions are captured as typed actions (`DrawPath`, `HollowCircle`, `DrawText`) and replayed during export onto a scaled `android.graphics.Bitmap`. This keeps the UI resolution-independent and makes batch export (including collage mode) straightforward.

## Run

1. Open the project in Android Studio.
2. Sync Gradle.
3. Run on an emulator or physical device.

## Release

Releases are automated via GitHub Actions:

1. Merge your feature branch to `main`.
2. Go to **Actions → Create Release Tag → Run workflow**.
3. Pick a bump level (`patch`, `minor`, `major`) or leave it on `auto` to detect from commit messages.
4. The workflow creates and pushes the new tag, which triggers the build and publish job.

Commit conventions for auto-detection:
- `fix:` → patch
- `feat:` → minor
- `BREAKING CHANGE:` or `feat!:` → major
