# KTS File Explorer

**KTS File Explorer** is a powerful, lightweight, modern, and highly accessible Android file management application designed for both everyday users and screen reader users (TalkBack / Jieshuo). It offers deep local file organization, advanced audio editing and conversion, multi-format archive extraction, PC Wi-Fi transfer, SMB network browsing, and Google Drive cloud storage.

---

## 🌟 Key Features

### 📁 Advanced File Management
- **Intuitive Browsing:** Effortlessly navigate internal device storage and SD cards.
- **Categorized View:** Instant smart categorization for Audio, Videos, Images, Documents, APKs, Downloads, and New/Recent files.
- **Dual-Mode Applications View:**
  - View raw APK installer packages found across device storage.
  - View installed applications (User apps and System apps) with direct launch, system App Info, and uninstall actions.
- **Favorites & Quick Bookmarks:** Pin frequently used directories to a dedicated Favorites tab for single-tap access.
- **Powerful Search & Sorting:** Real-time search across directories and categories. Sort by Name, Date, Size, or File Type globally or per-folder.
- **Full File Operations:** Cut, Copy, Paste, Rename, Delete, Share, Open With, and Detailed File Properties with instant accessibility announcements.
- **Batch Renaming with Multi-Position Support:** Rename multiple files simultaneously with powerful position controls:
  - **Beginning (Prefix):** Prepend custom text and optional sequential numbers to the start of filenames.
  - **End (Suffix):** Append text and optional numbering right before the file extension.
  - **Center / Text (Find & Replace):** Search for text within filenames and replace or remove it.
  - **New Name (Sequential Pattern):** Complete rename with custom base name, starting index, digit format (`01`, `001`), and customizable separators (`_`, `-`, space).
  - **Live Preview:** Real-time preview of changes before executing the rename.
  - **Extension Protection:** Keep original file extensions safely intact.

### 🎙️ Audio Tools & Batch Media Conversion
- **Native MP3 Encoding DSP Engine:** Embedded high-speed C audio engine for fast, high-quality audio compression.
- **Format Conversion:** Convert between MP3, M4A, AAC, WAV, OGG, and FLAC with configurable bitrates (64 kbps up to 320 kbps), sample rates, and audio channels (Stereo/Mono).
- **Batch Folder Conversion:** Convert entire folders of audio and video files in one operation with live progress indicators.
- **Audio Trimmer & Splitter:** Cut and split audio files with microsecond precision, visual/accessible waveforms, and audible position markers.
- **Built-in Media Player:** Accessible media playback with rotor-friendly seek controls (configurable 5s, 10s, 30s, 1m intervals) and selection preview playback.

### 📦 Comprehensive Archive Support
- **Archive Extraction:** Decompress **ZIP, RAR, 7Z, TAR, GZ, BZ2, and XZ** files directly on your device.
- **Archive Creation:** Create standard ZIP archives from files and folders.
- **Open Archives From External Apps:** Open ZIP attachments received from WhatsApp, Telegram, or browsers directly inside KTS File Explorer.

### 🌐 Cloud & Network Connectivity
- **Google Drive Integration:** Connect securely using official Google OAuth 2.0. Browse, upload, download, and manage your Google Drive files and folders.
- **Live Transfer Progress Dialog:** Real-time percentage, transferred bytes, and transfer speed (KB/s or MB/s) with Cancel and Background minimization options.
- **Background Floating Widget:** Minimize active file transfers to a floating progress card at the bottom-right of your screen and restore the full dialog anytime.
- **SMB Windows / Mac Network Shares:** Connect directly to computer shared folders over local Wi-Fi without needing third-party utilities.
- **FTP Client:** Connect to local or remote FTP servers for seamless downloads and uploads.
- **Wi-Fi PC Sharing:** Start a local HTTP file server on your phone to view, download, and upload files directly from your computer's web browser.

### ♿ Accessibility First (TalkBack & Jieshuo Optimized)
- **Screen Reader Announcements:** Optimized touch targets, clear labels, and live state updates.
- **Customizable Folder Announcements:** Configure whether folder names, item counts, or sizes are announced first, or disable individual attributes according to your preference.
- **Audio Feedback:** Live accessibility announcements during copy, paste, delete, compression, and transfer operations.

### 🔄 In-App GitHub Updates
- Check for updates directly inside the app under **Settings > App Updates** or via the Navigation Drawer.
- Automated background update checks keep the app up to date with the latest GitHub releases.
- Download and install newer versions with a single tap.

---

## 📱 System Requirements

- **Operating System:** Android 11.0 (API level 30) or higher
- **Target SDK:** Android 15 / 16 (API level 37)
- **Architecture:** ARM64 (`arm64-v8a`), ARMv7 (`armeabi-v7a`), x86, and x86_64
- **App Size:** ~7.3 MB (ultra-compact and optimized)

---

## 🔒 Security & Privacy

- **Cryptographically Signed:** Official release builds are signed with a dedicated 4096-bit RSA certificate valid until October 2056.
- **No Third-Party Analytics:** Your files and private data remain strictly on your device or in the remote storage services you choose to connect.

---

## 📥 Download & Installation

1. Go to the [Releases](https://github.com/KTShihab/KTSExplorer/releases) section.
2. Download the latest `KTSExplorer.apk`.
3. Open the downloaded APK on your Android device to install.
4. Grant the **All files access** (`MANAGE_EXTERNAL_STORAGE`) permission when prompted to allow the app to browse and organize your files.

---

## 🛠️ Built With

- **Language:** Java & C/C++ (NDK)
- **UI Architecture:** AndroidX, Material Design 3, ViewBinding
- **Networking:** OkHttp, Commons Net (FTP), SMBJ (SMB2/3)
- **Cloud:** Google Play Services Auth, Google Drive REST API v3
- **Compression:** Apache Commons Compress, Junrar, XZ
- **Build System:** Gradle (Kotlin DSL) with R8 Code & Resource Shrinking

---

## 👨‍💻 Author

Developed with care by **KTShihab**  
Part of the KTS Suite:
- **KTS Advanced Media Player**
- **KTS Launcher**
- **KTS Text Solution**
- **KTS File Explorer**

---

## 📄 License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.
