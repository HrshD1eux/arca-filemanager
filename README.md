# ARCA File Manager

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg?logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%7C%20Material%203-4285F4.svg)](https://developer.android.com/jetpack/compose)

**ARCA** is a modern, privacy-first, offline-first Android file manager built with Jetpack Compose and Material 3. Designed for speed, aesthetic polish, and security, ARCA puts complete control over local and remote storage in the user's hands with zero bloat and zero tracking.

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
