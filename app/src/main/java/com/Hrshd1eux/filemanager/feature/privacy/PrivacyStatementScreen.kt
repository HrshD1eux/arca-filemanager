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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.R
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
                        text = stringResource(R.string.privacy_screen_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel)
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
                        text = stringResource(R.string.privacy_hero_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.privacy_hero_subtitle, versionInfo.displayVersion),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.privacy_hero_body),
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
                title = stringResource(R.string.privacy_section1_title)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.privacy_section1_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            DisclosureItem(
                title = stringResource(R.string.privacy_section1_item1_title),
                desc = stringResource(R.string.privacy_section1_item1_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section1_item2_title),
                desc = stringResource(R.string.privacy_section1_item2_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section1_item3_title),
                desc = stringResource(R.string.privacy_section1_item3_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section1_item4_title),
                desc = stringResource(R.string.privacy_section1_item4_desc)
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
                title = stringResource(R.string.privacy_section2_title)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.privacy_section2_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            DisclosureItem(
                title = stringResource(R.string.privacy_section2_item1_title),
                desc = stringResource(R.string.privacy_section2_item1_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section2_item2_title),
                desc = stringResource(R.string.privacy_section2_item2_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section2_item3_title),
                desc = stringResource(R.string.privacy_section2_item3_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section2_item4_title),
                desc = stringResource(R.string.privacy_section2_item4_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section2_item5_title),
                desc = stringResource(R.string.privacy_section2_item5_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section2_item6_title),
                desc = stringResource(R.string.privacy_section2_item6_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section2_item7_title),
                desc = stringResource(R.string.privacy_section2_item7_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section2_item8_title),
                desc = stringResource(R.string.privacy_section2_item8_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section2_item9_title),
                desc = stringResource(R.string.privacy_section2_item9_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section2_item10_title),
                desc = stringResource(R.string.privacy_section2_item10_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section2_item11_title),
                desc = stringResource(R.string.privacy_section2_item11_desc)
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
                title = stringResource(R.string.privacy_section3_title)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.privacy_section3_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            DisclosureItem(
                title = stringResource(R.string.privacy_section3_item1_title),
                desc = stringResource(R.string.privacy_section3_item1_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section3_item2_title),
                desc = stringResource(R.string.privacy_section3_item2_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section3_item3_title),
                desc = stringResource(R.string.privacy_section3_item3_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section3_item4_title),
                desc = stringResource(R.string.privacy_section3_item4_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section3_item5_title),
                desc = stringResource(R.string.privacy_section3_item5_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section3_item6_title),
                desc = stringResource(R.string.privacy_section3_item6_desc)
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
                title = stringResource(R.string.privacy_section4_title)
            )
            Spacer(modifier = Modifier.height(12.dp))

            DisclosureItem(
                title = stringResource(R.string.privacy_section4_item1_title),
                desc = stringResource(R.string.privacy_section4_item1_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section4_item2_title),
                desc = stringResource(R.string.privacy_section4_item2_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section4_item3_title),
                desc = stringResource(R.string.privacy_section4_item3_desc)
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
                title = stringResource(R.string.privacy_section5_title)
            )
            Spacer(modifier = Modifier.height(12.dp))

            DisclosureItem(
                title = stringResource(R.string.privacy_section5_item1_title),
                desc = stringResource(R.string.privacy_section5_item1_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section5_item2_title),
                desc = stringResource(R.string.privacy_section5_item2_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section5_item3_title),
                desc = stringResource(R.string.privacy_section5_item3_desc)
            )
            DisclosureItem(
                title = stringResource(R.string.privacy_section5_item4_title),
                desc = stringResource(R.string.privacy_section5_item4_desc)
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
                title = stringResource(R.string.privacy_section6_title)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.privacy_section6_intro),
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
                title = "Zip4j",
                desc = "Used locally for standard and password-encrypted ZIP archive creation and extraction without any network code."
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
                title = stringResource(R.string.privacy_section7_title)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.privacy_section7_desc),
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
                            uriHandler.openUri("https://github.com/HrshD1eux/arca-filemanager/issues")
                        }.onFailure {
                            Toast.makeText(context, "https://github.com/HrshD1eux/arca-filemanager/issues", Toast.LENGTH_LONG).show()
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
                        text = "github.com/HrshD1eux/arca-filemanager/issues",
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
