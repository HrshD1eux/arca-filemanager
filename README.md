<p align="center">
  <a href="https://hrshd1eux.github.io/arca-filemanager/">
    <img src="docs/assets/logo.png" width="120" height="120" alt="ARCA File Manager Logo" style="border-radius: 24px;" />
  </a>
</p>

<h1 align="center">ARCA File Manager</h1>

<p align="center">
  <b>Modern, Fast & Private Android File Manager</b><br>
  Built with Jetpack Compose & Material 3 • Completely Offline-First • Zero Tracking
</p>

<p align="center">
  <a href="https://hrshd1eux.github.io/arca-filemanager/">Website</a> •
  <a href="https://github.com/HrshD1eux/arca-filemanager/releases/latest">Download APK</a> •
  <a href="https://hrshd1eux.github.io/arca-filemanager/privacy.html">Privacy Policy</a>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License: GPL v3"></a>
  <a href="https://android.com"><img src="https://img.shields.io/badge/Platform-Android-3DDC84.svg?logo=android&logoColor=white" alt="Platform"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.0+-7F52FF.svg?logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%7C%20Material%203-4285F4.svg" alt="Compose"></a>
</p>

---

## ✨ Features

- **🛡️ Fully Offline-First & Private:** No telemetry, no analytics SDKs, no cloud account requirements.
- **🎨 Modern Design System:**
  - True Obsidian OLED dark base (`#0B0E14`) and Deep Slate elevated containers.
  - Zero-overhead GPU RenderNode spring micro-interactions (`bounceClick`).
  - Two-tone semantic iconography badges.
- **📦 Multi-Archive Compression & Extraction:**
  - Full archive support: **7z**, **ZIP**, **TAR**, **GZ**, **BZ2**, **XZ**, and password-protected **RAR** extraction.
  - Resumable background extraction and integrity testing.
- **🔐 Encrypted Vault:**
  - Hardware-backed AES-GCM encryption with biometric authentication or custom secure PIN.
- **🌐 Network Storage Protocols:**
  - Seamless SFTP, FTP, SMB, and WebDAV client integration.
  - Built-in local FTP server for high-speed Wi-Fi PC file transfers.
- **📊 Storage Analyzer:**
  - Interactive storage breakdown, largest files detection, duplicate scanner, and visual image/media previews.
- **🔄 In-App Updater:**
  - Resumable background release updates directly from GitHub Releases.

---

## 🛠️ Building from Source

### Prerequisites
- Android Studio Ladybug / Meerkat or later
- JDK 17 or higher
- Android SDK (API 35)

### Build Commands
```bash
# Clone the repository
git clone https://github.com/HrshD1eux/arca-filemanager.git
cd arca-filemanager

# Run unit tests
./gradlew testDebugUnitTest

# Assemble Release APK
./gradlew assembleRelease
```

---

## 📄 License

ARCA File Manager is free software: you can redistribute it and/or modify it under the terms of the **[GNU General Public License v3.0 (GPLv3)](LICENSE)** as published by the Free Software Foundation.

```
Copyright (C) 2026 ARCA Contributors & hrshd1eux Studio

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.
```

Third-party libraries used in ARCA remain under their respective licenses as detailed in the app's *About & Legal Information* section.
