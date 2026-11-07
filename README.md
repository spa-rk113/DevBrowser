# 🚀 DevBrowser

A lightweight Android browser with experimental developer tools.

## ✨ Features

### Browser
- Google Search and website navigation
- Back, Forward, Reload and Home
- Multiple browser tabs
- Tab switching and closing
- Session restoration
- JavaScript-enabled WebView

### Developer Tools
- Inspect Element
- Pick Element
- DOM Elements tree
- HTML Editor
- CSS Editor
- Computed CSS information
- CSS Box Model
- JavaScript Console
- Console logs and errors
- Local webpage modifications

### Interface
- Dark browser interface
- Resizable developer tools panel
- Browser-style toolbar
- Smooth UI transitions
- Custom launcher icon
- Interactive first-launch tutorial

## 🛠 Requirements

- JDK 17
- Gradle 8.9
- Android SDK 35
- Android device running Android 6.0 or newer

## 📦 Build APK

1. Open the repository on GitHub.
2. Open the Actions tab.
3. Select Build DevBrowser APK.
4. Click Run workflow.
5. Wait for the workflow to complete.
6. Open the successful workflow run.
7. Download the DevBrowser-APK artifact.

## 📁 Project Structure

```text
DevBrowser/
├── .github/
│   ├── tools/
│   │   └── generate_icons.py
│   └── workflows/
│       └── build.yml
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/devbrowser/
│       │   └── MainActivity.kt
│       └── res/
├── assets/
│   └── devbrowser-logo.png
├── build.gradle
├── gradle.properties
├── settings.gradle
└── README.md
