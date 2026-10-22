# ARCA Release & Publishing Compliance Checklist (TODO)

> [!WARNING]
> **CRITICAL STORE POLICY COMPLIANCE**: Review every item in this document before submitting ARCA to the **Google Play Store**, **F-Droid**, or any public application repository. Failure to comply with these rules can result in immediate rejection, policy strikes, or permanent developer account termination.

---

## 1. Google Play Developer Policy Violations (Instant Account Ban / Rejection)

### A. In-App APK Downloader & `REQUEST_INSTALL_PACKAGES`
* **The Policy Violation**:
  * **Google Play Policy (Malware / Device Abuse)**: Google Play strictly restricts `REQUEST_INSTALL_PACKAGES`. Only dedicated third-party app stores or enterprise device management apps are permitted to request it. A file or attendance manager requesting package installation permissions for self-updates is an instant rejection / policy strike.
  * **Google Play DDA (Section 4.5)**: Strictly prohibits apps distributed via Google Play from downloading or updating themselves outside of Google Play's official billing and update mechanism.
  * **Play Protect / AV Heuristics**: Downloading and prompting execution of `.apk` binaries over the internet without signature verification causes apps to get flagged as a "Dropper" or "Trojan Downloader".
  * **F-Droid Exclusion**: F-Droid explicitly bans in-app binary updaters (`Anti-Feature: NonFreeUpstream / Non-free binary updater`).
* **Actions Required Before Publishing on Google Play**:
  - [ ] **Do NOT add** `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />` to `AndroidManifest.xml` for Google Play builds.
  - [ ] **Do NOT implement** an in-app `.apk` downloader that downloads binary executables from GitHub and triggers `PackageInstaller`.
  - [ ] **Google Play / F-Droid Update Pattern**: Make "Check for Updates" open the official GitHub Release page or Google Play Store link in the user's external web browser via standard `Intent.ACTION_VIEW`:
    ```kotlin
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/HrshD1eux/arca-filemanager/releases/latest"))
    context.startActivity(intent)
    ```

---

## 2. High-Risk Google Play Permissions Declarations

### B. `MANAGE_EXTERNAL_STORAGE` (`All Files Access`)
* **Google Play Requirement**: Google strictly audits apps using `MANAGE_EXTERNAL_STORAGE`. Only apps whose core purpose is file management, document management, or device backup are permitted.
* **Actions Required**:
  - [ ] Prepare an unlisted YouTube video showing the core file manager workflow: browsing root storage, moving/copying files, viewing archives, and managing local storage.
  - [ ] In the Google Play Console Declaration Form, select **"Core File Manager"** as the primary capability.

### C. `QUERY_ALL_PACKAGES` (Package Visibility)
* **Google Play Requirement**: Querying all installed packages on the device is considered high-risk personal data access.
* **Actions Required**:
  - [ ] Submit the declaration form explaining that ARCA contains an **Installed App Manager & Backup** module requiring inventory of user apps to calculate storage footprints, inspect permissions, and back up base APKs.

---

## 3. Copyright & Intellectual Property Protections

### D. Proprietary Formats & Trademarks
* [x] **Meta / WhatsApp Trademarks**: Generic database terminology used for encrypted `.crypt*` formats without proprietary sandbox paths.
* [x] **Brand Disclaimers**: Generic sharing targets used in APK sharing dialogs instead of proprietary names (Drive, Quick Share, WhatsApp).
* [x] **RAR Compression (RARLAB / Eugene Roshal)**: RAR compression is strictly disabled. ARCA only extracts RAR archives via `junrar`, avoiding RARLAB patent and trade-secret litigation.
* [x] **DMCA / DRM Notice**: APK export UI includes an explicit user notice stating that extraction is for personal backup of legally licensed apps and does not circumvent DRM.

---

## 4. Open-Source Licensing Compliance

### E. Apache 2.0 & Third-Party Library Attribution
* [x] Open-source libraries (`commons-compress`, `junrar`, `tukaani-xz`, `isofilereader`, `androidx`, `compose`, `kotlinx`) are listed with project and license links under **Settings -> About ARCA -> Open Source Libraries**.
* [x] Copyright notices and license grants harmonized under Apache License 2.0.

---

## 5. FTC & Truth-in-Advertising Disclosures

### F. Flash Memory Wear-Leveling Disclaimer
* [x] File Shredder dialogs truthfully state the physical reality of modern UFS / eMMC flash memory (controller wear leveling and block retirement), avoiding deceptive "100% anti-recovery" claims.
