package com.Hrshd1eux.filemanager.feature.privacy

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.Hrshd1eux.filemanager.core.version.AppVersionInfo
import com.Hrshd1eux.filemanager.core.version.rememberAppVersionInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.designsystem.VaultEmerald

/**
 * Real, comprehensive Privacy Statement screen for ARCA.
 *
 * Discloses:
 * 1. Data processed locally on device (metadata, vault data, preferences, credentials).
 * 2. Permissions requested and why (All Files Access, Notifications, Foreground Services).
 * 3. User-activated network features (direct point-to-point, zero relays, protocol encryption).
 * 4. Zero external transmission policy (no developer servers, zero telemetry, no cloud leaks).
 * 5. Accurate crash reporting and analytics disclosures (zero SDKs, local logcat only).
 * 6. Third-party library disclosures (Commons Compress, XZ, Junrar, Palantir ISO, AndroidX, Kotlin).
 * 7. Privacy contact and inquiries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyStatementScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Privacy Statement",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                PrivacyHeroCard()
            }

            item {
                LocalDataProcessingCard()
            }

            item {
                PermissionsDisclosureCard()
            }

            item {
                NetworkFeaturesDisclosureCard()
            }

            item {
                ExternalTransmissionCard()
            }

            item {
                CrashReportingAndAnalyticsCard()
            }

            item {
                ThirdPartyLibrariesDisclosureCard()
            }

            item {
                PrivacyContactCard()
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun PrivacyHeroCard(
    modifier: Modifier = Modifier,
    versionInfo: AppVersionInfo = rememberAppVersionInfo()
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Offline-First Privacy Guarantee",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Last verified: ${versionInfo.displayVersion} • Zero Telemetry",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "ARCA is built upon an unyielding premise: your files, folders, and personal data belong exclusively to you. We do not track user behavior, collect personal information, or operate remote servers to monetize or log your storage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun LocalDataProcessingCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                icon = Icons.Default.Storage,
                title = "1. Data Processed Locally on Your Device"
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "All core operations performed by ARCA execute entirely on your device hardware without relying on cloud processing:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            DisclosureItem(
                title = "File & Storage Metadata",
                desc = "Filenames, file sizes, MIME types, modification dates, and folder hierarchy are read from local storage strictly to render listings, sort items, search, and identify duplicate files. This metadata is processed in volatile memory (RAM) and local database caches, and is never logged to external diagnostic stores."
            )
            DisclosureItem(
                title = "Private Vault Content",
                desc = "Files placed in the Private Vault are encrypted locally using AES-256-GCM cipher with keys derived on-demand from your passphrase. Decrypted content exists only ephemerally in RAM during active viewing and is completely wiped when the vault locks."
            )
            DisclosureItem(
                title = "User Preferences & Settings",
                desc = "Theme selection, display modes (grid/list), hidden file visibility, confirm-delete preferences, and auto-lock timeouts are stored in your device's private Android SharedPreferences."
            )
            DisclosureItem(
                title = "Remote Server Credentials",
                desc = "Host addresses, ports, usernames, and passwords for user-configured remote servers are stored solely in app-private encrypted storage on your device and are never sent to third-party services."
            )
        }
    }
}

@Composable
private fun PermissionsDisclosureCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                icon = Icons.Default.Security,
                title = "2. Permissions Requested & Why"
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "ARCA requests only permissions fundamentally essential for local file management, archiving, and foreground tasks:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            DisclosureItem(
                title = "All Files Access (MANAGE_EXTERNAL_STORAGE)",
                desc = "Required on Android 11+ (API 30+) to enable full file management capabilities—browsing, moving, copying, renaming, deleting, compressing, and extracting files across internal shared storage and removable SD cards."
            )
            DisclosureItem(
                title = "Legacy Storage (READ/WRITE_EXTERNAL_STORAGE)",
                desc = "Declared with maxSdkVersion to grant file management capabilities on legacy devices running Android 10 (API 29) or older."
            )
            DisclosureItem(
                title = "Notifications (POST_NOTIFICATIONS)",
                desc = "Required on Android 13+ (API 33+) exclusively to show user-visible progress indicators during batch file tasks (e.g. compressing a large zip, extracting an archive, bulk copy/move) and vault background lock alerts. Never used for marketing or background notifications."
            )
            DisclosureItem(
                title = "Foreground Service (DATA_SYNC)",
                desc = "Prevents the Android operating system from prematurely killing active archive compression, extraction, or file transfer tasks when you navigate away from the app."
            )
            DisclosureItem(
                title = "Explicitly Excluded Permissions",
                desc = "ACCESS_NETWORK_STATE is stripped via manifest rules. ARCA requests ZERO location, camera, microphone, contacts, calendar, or device telephony/IMEI permissions."
            )
        }
    }
}

@Composable
private fun NetworkFeaturesDisclosureCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                icon = Icons.Default.Lan,
                title = "3. What Happens When You Use Network Features"
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "ARCA supports user-initiated remote storage connections (FTP, FTPS, SFTP, SMB, and WebDAV). Here is exactly how network communication operates:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            DisclosureItem(
                title = "100% User-Initiated",
                desc = "ARCA never opens network sockets or transmits packets on its own. Network activity occurs exclusively when you configure and connect to a server."
            )
            DisclosureItem(
                title = "Direct Point-to-Point Communication",
                desc = "All file transfers and directory queries travel directly between your Android device and the target IP address or hostname you entered. There are no intermediate relay servers, cloud gateways, or proxy nodes."
            )
            DisclosureItem(
                title = "Protocol Encryption Standards",
                desc = "SFTP uses SSHv2 encryption, FTPS uses TLS, and WebDAV can use HTTPS. Cleartext protocols (Plaintext FTP or cleartext HTTP WebDAV) are supported for local LAN environments but carry explicit warnings in the connection dialog."
            )
            DisclosureItem(
                title = "Zero Passive Background Connections",
                desc = "Remote connections are disconnected when not in use. ARCA does not maintain persistent background connections or sync daemons."
            )
        }
    }
}

@Composable
private fun ExternalTransmissionCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                icon = Icons.Default.SyncDisabled,
                title = "4. Zero External Services & No Data Transmission"
            )
            Spacer(modifier = Modifier.height(12.dp))

            DisclosureItem(
                title = "No Transmission to External Services",
                desc = "ARCA transmits zero data to the app developers, advertising networks, commercial partners, or cloud platforms. There are no remote telemetry servers or backend tracking APIs."
            )
            DisclosureItem(
                title = "No Advertising or Fingerprinting",
                desc = "ARCA contains zero advertising SDKs. It does not read or transmit your Android Advertising ID (GAID), hardware serial numbers, or unique device fingerprints."
            )
            DisclosureItem(
                title = "Cloud Backup Leakage Prevention",
                desc = "ARCA sets android:allowBackup=\"false\" in its manifest, preventing your sensitive settings, vault files, or remote credentials from being automatically extracted into Google Drive or third-party cloud backups."
            )
        }
    }
}

@Composable
private fun CrashReportingAndAnalyticsCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                icon = Icons.Default.Analytics,
                title = "5. Crash Reporting & Analytics Disclosure"
            )
            Spacer(modifier = Modifier.height(12.dp))

            DisclosureItem(
                title = "Zero Analytics SDKs",
                desc = "ARCA contains no analytics frameworks whatsoever. We do not use Firebase Analytics, Google Analytics, Mixpanel, Segment, Flurry, Amplitude, or any behavior-tracking libraries."
            )
            DisclosureItem(
                title = "Zero Automated Crash Reporting",
                desc = "ARCA does not include automated crash-reporting SDKs such as Firebase Crashlytics, Sentry, or Bugsnag. When a crash occurs, no diagnostic payload is sent over the network."
            )
            DisclosureItem(
                title = "Local System Logs Only",
                desc = "If an unexpected application error occurs, Android records the stack trace strictly to the local operating system log (logcat) on your device. This log never leaves your phone unless you manually export it."
            )
            DisclosureItem(
                title = "Voluntary User Reporting",
                desc = "If you encounter a problem and choose to file a bug report on our GitHub issue tracker or via email, you have full control over what details and screenshots you share."
            )
        }
    }
}

@Composable
private fun ThirdPartyLibrariesDisclosureCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                icon = Icons.Default.Code,
                title = "6. Third-Party Library Disclosures"
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "ARCA incorporates vetted, open-source libraries for local archive compression and user interface rendering. None of these libraries contain network beacons or telemetry trackers:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            DisclosureItem(
                title = "Apache Commons Compress",
                desc = "Used solely for local in-process decompression and creation of Tar, 7z, Cpio, and Dump archives. Operates entirely offline with zero network code."
            )
            DisclosureItem(
                title = "Tukaani XZ for Java",
                desc = "Used for local XZ stream compression and LZMA algorithms. Completely offline and public domain."
            )
            DisclosureItem(
                title = "Junrar",
                desc = "Used exclusively for reading and extracting uncompressed/compressed RAR archives locally on device."
            )
            DisclosureItem(
                title = "Palantir ISO File Reader",
                desc = "Used for inspecting ISO 9660 disc image file structures locally without network interaction."
            )
            DisclosureItem(
                title = "AndroidX & Jetpack Compose",
                desc = "Google's official open-source Android libraries for UI rendering, Lifecycle management, Navigation, and WorkManager task execution."
            )
        }
    }
}

@Composable
private fun PrivacyContactCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                icon = Icons.Default.Email,
                title = "7. Contact & Privacy Inquiries"
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "If you have questions, concerns, or technical inquiries regarding ARCA's privacy practices or security implementation, you can contact the project maintainers directly:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        runCatching {
                            uriHandler.openUri("https://github.com/HrshD1eux/filemanager/issues")
                        }.onFailure {
                            Toast.makeText(context, "https://github.com/HrshD1eux/filemanager/issues", Toast.LENGTH_LONG).show()
                        }
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Public Issue Tracker",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "github.com/HrshD1eux/filemanager/issues",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:HrshD1eux@proton.me")
                            putExtra(Intent.EXTRA_SUBJECT, "Privacy Inquiry - ARCA")
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(emailIntent, "Send Privacy Inquiry"))
                        }.onFailure {
                            Toast.makeText(context, "Contact: HrshD1eux@proton.me", Toast.LENGTH_LONG).show()
                        }
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Email Inquiry",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "HrshD1eux@proton.me",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = VaultEmerald,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DisclosureItem(
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = VaultEmerald,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
