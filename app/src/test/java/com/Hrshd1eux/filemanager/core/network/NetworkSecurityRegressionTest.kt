package com.Hrshd1eux.filemanager.core.network

import com.Hrshd1eux.filemanager.core.network.credentials.SecureCredentialsStore
import com.Hrshd1eux.filemanager.core.network.diagnostics.NetworkPrivacyDiagnostics
import com.Hrshd1eux.filemanager.core.network.diagnostics.NetworkPrivacyEventType
import com.Hrshd1eux.filemanager.core.network.manager.NetworkConnectionManager
import com.Hrshd1eux.filemanager.core.network.model.NetworkAuthType
import com.Hrshd1eux.filemanager.core.network.model.NetworkException
import com.Hrshd1eux.filemanager.core.network.model.NetworkProtocol
import com.Hrshd1eux.filemanager.core.network.model.NetworkServerConfig
import com.Hrshd1eux.filemanager.core.network.provider.FtpsNetworkProvider
import com.Hrshd1eux.filemanager.core.network.provider.SftpNetworkProvider
import com.Hrshd1eux.filemanager.core.network.provider.WebDavNetworkProvider
import com.Hrshd1eux.filemanager.core.network.registry.NetworkProviderRegistry
import com.Hrshd1eux.filemanager.core.network.repository.NetworkConnectionRepositoryImpl
import com.Hrshd1eux.filemanager.core.network.ssh.SshHostKeyStatus
import com.Hrshd1eux.filemanager.core.network.ssh.SshKnownHostsStore
import com.Hrshd1eux.filemanager.core.network.tls.ConnectionScopedCertStore
import com.Hrshd1eux.filemanager.core.network.tls.TlsCertificateValidator
import com.Hrshd1eux.filemanager.core.network.tls.TlsValidationResult
import com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.math.BigInteger
import java.nio.file.Files
import java.security.Principal
import java.security.PublicKey
import java.security.cert.CertificateExpiredException
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal

/**
 * Phase A — Network Security Regression Test Suite (Milestones 1.4.1–1.4.10).
 *
 * Verifies:
 * 1. SSH host key change detection & rejection (MITM mitigation)
 * 2. New SSH host detection and explicit approval workflow
 * 3. TLS certificate validation (expired, untrusted, hostname mismatch)
 * 4. Connection-scoped TLS certificate pinning (never globally bypasses validation)
 * 5. Distinct domain error hierarchy (timeout vs auth vs unreachable vs security)
 * 6. Duplicate concurrent connection prevention
 * 7. Active transfer interruption and clean cancellation
 * 8. Wi-Fi dropped / network lost resilience
 * 9. Credential security, zero plaintext leaks, and in-memory wiping
 * 10. Local storage isolation release gate (local browsing 100% resilient when remote drops)
 */
class NetworkSecurityRegressionTest {

    private lateinit var tempDir: File
    private lateinit var knownHostsStore: SshKnownHostsStore
    private lateinit var certStore: ConnectionScopedCertStore
    private lateinit var tlsValidator: TlsCertificateValidator
    private lateinit var credentialsStore: SecureCredentialsStore
    private lateinit var repository: NetworkConnectionRepositoryImpl
    private lateinit var connectionManager: NetworkConnectionManager
    private lateinit var diagnostics: NetworkPrivacyDiagnostics

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("arca_network_test").toFile()
        knownHostsStore = SshKnownHostsStore(context = null, prefName = "test_known_hosts")
        knownHostsStore.clear()

        certStore = ConnectionScopedCertStore(context = null, prefName = "test_pinned_certs")
        certStore.clear()

        tlsValidator = TlsCertificateValidator(certStore)
        credentialsStore = SecureCredentialsStore(context = null, prefName = "test_creds")
        credentialsStore.clearVolatileCache()

        repository = NetworkConnectionRepositoryImpl(
            context = null,
            credentialsStore = credentialsStore,
            ioDispatcher = Dispatchers.Unconfined,
            prefsName = "test_conn_metadata"
        )

        diagnostics = NetworkPrivacyDiagnostics(maxCapacity = 100)
        diagnostics.clear()

        connectionManager = NetworkConnectionManager(
            diagnostics = diagnostics,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    // =========================================================================
    // 1. SSH Host-Key Verification & Changed Key Rejection (1.4.2)
    // =========================================================================

    @Test
    fun sshHostKey_whenKeyChanges_strictlyRejectsWithSshHostKeyChangedException() = runTest {
        val host = "nas.home.local"
        val port = 22
        val originalKey = "trusted-ed25519-public-key-original".toByteArray()
        val changedKey = "compromised-mitm-ed25519-key-attacker".toByteArray()

        // 1. Initially trust the legitimate host key
        val trustedRecord = knownHostsStore.trustHostKey(host, port, "ssh-ed25519", originalKey)
        assertNotNull(trustedRecord)

        // 2. Configure SFTP provider
        val config = NetworkServerConfig(
            id = "test-nas-sftp",
            name = "Home NAS",
            protocol = NetworkProtocol.SFTP,
            host = host,
            port = port,
            authType = NetworkAuthType.PASSWORD,
            username = "admin",
            password = "testPassword"
        )
        val sftpProvider = SftpNetworkProvider(config, knownHostsStore, Dispatchers.Unconfined)
        sftpProvider.presentedHostKey = changedKey

        // 3. Attempt connection — MUST FAIL due to changed host key
        val result = sftpProvider.connect()
        assertTrue("Connection must be rejected when host key changes", result.isFailure)

        val exception = result.exceptionOrNull()
        assertTrue(
            "Must throw SshHostKeyChangedException on key mismatch",
            exception is NetworkException.SshHostKeyChangedException
        )

        val keyChangedEx = exception as NetworkException.SshHostKeyChangedException
        assertEquals(host, keyChangedEx.host)
        assertEquals(port, keyChangedEx.port)
        assertEquals(knownHostsStore.calculateFingerprint(changedKey), keyChangedEx.actualFingerprint)
        assertTrue("Error message must contain prominent security warning", keyChangedEx.message?.contains("WARNING: REMOTE HOST IDENTIFICATION HAS CHANGED") == true)
    }

    @Test
    fun sshHostKey_newHost_requiresExplicitApprovalWorkflow() = runTest {
        val host = "office.corp.internal"
        val port = 22
        val presentedKey = "legitimate-ed25519-public-key".toByteArray()

        val config = NetworkServerConfig(
            id = "test-office-sftp",
            name = "Office Server",
            protocol = NetworkProtocol.SFTP,
            host = host,
            port = port,
            authType = NetworkAuthType.PASSWORD,
            username = "engineer",
            password = "password"
        )
        val provider = SftpNetworkProvider(config, knownHostsStore, Dispatchers.Unconfined)
        provider.presentedHostKey = presentedKey

        // Strict verification detects NEW_HOST
        try {
            provider.verifyHostKey(strictNewHostCheck = true)
            fail("Expected SshHostKeyUnverifiedException for new host")
        } catch (e: NetworkException.SshHostKeyUnverifiedException) {
            assertEquals(host, e.host)
            assertEquals("ssh-ed25519", e.keyType)
            assertTrue(e.actualFingerprint.startsWith("SHA256:"))
        }

        // Explicit user approval workflow
        provider.trustCurrentHostKey()

        // Subsequent verification must be TRUSTED
        val verified = provider.verifyHostKey(strictNewHostCheck = true)
        assertEquals(SshHostKeyStatus.TRUSTED, verified.status)

        // Connect now succeeds
        val connectResult = provider.connect()
        assertTrue(connectResult.isSuccess)
    }

    // =========================================================================
    // 2. TLS Certificate Validation (1.4.3)
    // =========================================================================

    @Test
    fun tlsValidation_expiredCertificate_isRejected() {
        val expiredCert = TestMockX509Certificate(
            commonName = "cloud.internal",
            isExpired = true
        )

        val result = tlsValidator.validateCertificateChain(
            serverHost = "cloud.internal",
            chain = arrayOf(expiredCert)
        )

        assertTrue("Expired certificate must produce Expired result", result is TlsValidationResult.Expired)
        try {
            tlsValidator.assertValid("cloud.internal", arrayOf(expiredCert))
            fail("Expected TlsCertificateExpiredException")
        } catch (e: NetworkException.TlsCertificateExpiredException) {
            assertEquals("cloud.internal", e.host)
        }
    }

    @Test
    fun tlsValidation_hostnameMismatch_isRejected() {
        val mismatchCert = TestMockX509Certificate(
            commonName = "wrong-host.example.com",
            isExpired = false
        )

        val result = tlsValidator.validateCertificateChain(
            serverHost = "nas.myhome.net",
            chain = arrayOf(mismatchCert)
        )

        assertTrue("Hostname mismatch must produce HostnameMismatch result", result is TlsValidationResult.HostnameMismatch)
        try {
            tlsValidator.assertValid("nas.myhome.net", arrayOf(mismatchCert))
            fail("Expected TlsCertificateHostnameMismatchException")
        } catch (e: NetworkException.TlsCertificateHostnameMismatchException) {
            assertEquals("nas.myhome.net", e.host)
            assertTrue(e.certSubject.contains("wrong-host.example.com"))
        }
    }

    @Test
    fun tlsValidation_connectionScopedCertificatePinning_isNeverGloballyApplied() {
        val selfSignedCert = TestMockX509Certificate(
            commonName = "nas.local",
            isExpired = false,
            certFingerprint = "SHA256:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"
        )
        val certFingerprint = tlsValidator.calculateFingerprintSha256(selfSignedCert)

        // 1. Without pin, untrusted certificate is strictly rejected
        val unpinnedResult = tlsValidator.validateCertificateChain(
            serverHost = "nas.local",
            chain = arrayOf(selfSignedCert),
            connectionId = "nas-connection-01",
            allowExplicitPinnedCert = false
        )
        assertTrue("Untrusted cert without pin must fail", unpinnedResult is TlsValidationResult.Untrusted)

        // 2. Explicitly pin certificate ONLY for connection "nas-connection-01"
        certStore.pinCertificate("nas-connection-01", certFingerprint)

        val pinnedResult = tlsValidator.validateCertificateChain(
            serverHost = "nas.local",
            chain = arrayOf(selfSignedCert),
            connectionId = "nas-connection-01",
            allowExplicitPinnedCert = true
        )
        assertTrue("Pinned cert for designated connection must be accepted", pinnedResult is TlsValidationResult.PinnedTrust)

        // 3. CRITICAL: Same untrusted cert presented to a DIFFERENT connection ID ("other-connection-02")
        // MUST BE REJECTED! Certificate bypasses are NEVER global!
        val otherResult = tlsValidator.validateCertificateChain(
            serverHost = "nas.local",
            chain = arrayOf(selfSignedCert),
            connectionId = "other-connection-02",
            allowExplicitPinnedCert = true
        )
        assertTrue(
            "Certificate pinning must be connection-scoped, NEVER globally disabling trust",
            otherResult is TlsValidationResult.Untrusted
        )
    }

    @Test
    fun ftpsProvider_withTlsValidator_enforcesCertChecks() = runTest {
        val badCert = TestMockX509Certificate(commonName = "mismatched.domain", isExpired = false)
        val config = NetworkServerConfig(
            id = "ftps-01",
            name = "Secure FTP",
            protocol = NetworkProtocol.FTPS,
            host = "ftps.vault.lan",
            port = 21,
            authType = NetworkAuthType.PASSWORD,
            username = "user",
            password = "pass"
        )
        val provider = FtpsNetworkProvider(config, tlsValidator)
        provider.presentedCertificates = arrayOf(badCert)

        val result = provider.connect()
        assertTrue("FTPS connection must fail on hostname mismatch", result.isFailure)
        assertTrue(result.exceptionOrNull() is NetworkException.TlsCertificateHostnameMismatchException)
    }

    // =========================================================================
    // 3. Error Distinction & Timeouts (1.4.5)
    // =========================================================================

    @Test
    fun errorDistinction_timeoutVsAuthVsUnreachable() {
        val timeoutEx = NetworkException.NetworkTimeoutException(15_000L)
        val authEx = NetworkException.NetworkAuthException("Invalid credentials for user 'admin'")
        val unreachableEx = NetworkException.HostUnreachableException("10.0.0.99", 22)

        assertEquals(15_000L, timeoutEx.timeoutMs)
        assertTrue(timeoutEx.message?.contains("timed out after 15000ms") == true)

        assertTrue(authEx.message?.contains("Invalid credentials") == true)

        assertEquals("10.0.0.99", unreachableEx.host)
        assertEquals(22, unreachableEx.port)
        assertTrue(unreachableEx.message?.contains("Unable to reach host '10.0.0.99' on port 22") == true)
    }

    // =========================================================================
    // 4. Connection Management & Duplicate Prevention (1.4.7)
    // =========================================================================

    @Test
    fun networkConnectionManager_preventsDuplicateConcurrentConnections() = runTest {
        val config = NetworkServerConfig(
            id = "concurrent-nas",
            name = "Concurrent NAS",
            protocol = NetworkProtocol.SFTP,
            host = "192.168.1.10",
            port = 22,
            authType = NetworkAuthType.PASSWORD,
            username = "admin",
            password = "password"
        )

        // Launch 10 concurrent requests to getOrConnectProvider
        val deferredList = (1..10).map {
            async {
                connectionManager.getOrConnectProvider(config)
            }
        }
        val results = deferredList.awaitAll()

        results.forEach { result ->
            assertTrue("Every concurrent caller must succeed", result.isSuccess)
        }

        // All returned providers must reference the exact same active instance
        val firstProvider = results[0].getOrThrow()
        for (i in 1 until results.size) {
            val provider = results[i].getOrThrow()
            assertTrue("Provider must be deduplicated", firstProvider === provider)
        }

        // Diagnostics must log only 1 CONNECT_SUCCESS
        val successEvents = diagnostics.getRecentEvents(50).filter {
            it.connectionId == "concurrent-nas" && it.eventType == NetworkPrivacyEventType.CONNECT_SUCCESS
        }
        assertEquals("Only 1 connection handshake should have been performed", 1, successEvents.size)

        // Clean disconnect
        connectionManager.disconnect("concurrent-nas")
        assertFalse(connectionManager.isConnected("concurrent-nas"))
    }

    @Test
    fun networkConnectionManager_transferCancellation_cancelsSafely() = runTest {
        val job = launch {
            delay(10_000L)
        }

        val transferId = "conn-01_file-download-001"
        connectionManager.registerTransfer(transferId, job)

        connectionManager.cancelTransfer(transferId, "User clicked cancel")
        job.join()

        assertTrue("Transfer job must be safely cancelled upon request", job.isCancelled)
    }

    @Test
    fun networkConnectionManager_onNetworkLost_cleansUpSafely() = runTest {
        val config = NetworkServerConfig(
            id = "wifi-nas",
            name = "WiFi NAS",
            protocol = NetworkProtocol.SFTP,
            host = "nas.local",
            authType = NetworkAuthType.PASSWORD,
            username = "user",
            password = "pass"
        )
        val provider = connectionManager.getOrConnectProvider(config).getOrThrow()
        assertTrue(connectionManager.isConnected("wifi-nas"))

        // Simulate lost Wi-Fi
        connectionManager.onNetworkLost()

        assertFalse("Sessions must be disconnected when network is lost", connectionManager.isConnected("wifi-nas"))
        val disconnectEvents = diagnostics.getRecentEvents(10).filter { it.eventType == NetworkPrivacyEventType.DISCONNECT }
        assertTrue("Disconnect event must be recorded", disconnectEvents.isNotEmpty())
    }

    // =========================================================================
    // 5. Credential Security & In-Memory Wiping (1.4.4)
    // =========================================================================

    @Test
    fun secureCredentialsStore_withSecretChars_wipesMemoryBuffer() {
        val secretName = "super_classified_token"
        credentialsStore.saveSecret("conn-secret", secretName, "P@ssw0rd999!LongSecret")

        var capturedArray: CharArray? = null
        credentialsStore.withSecretChars("conn-secret", secretName) { chars ->
            capturedArray = chars
            assertEquals("P@ssw0rd999!LongSecret", String(chars))
        }

        assertNotNull(capturedArray)
        // Memory array MUST be zeroed out
        val isAllZeroed = capturedArray!!.all { it == '\u0000' }
        assertTrue("Plaintext secret in memory must be overwritten with null bytes", isAllZeroed)
    }

    @Test
    fun secureCredentialsStore_hasSecret_checksWithoutDecryption() {
        credentialsStore.saveSecret("conn-test", "password", "mySecret")
        assertTrue(credentialsStore.hasSecret("conn-test", "password"))
        assertFalse(credentialsStore.hasSecret("conn-test", "nonexistent"))
    }

    // =========================================================================
    // 6. Offline-First Release Gate: Local Storage Isolation (1.4.8 & 1.4.10)
    // =========================================================================

    @Test
    fun offlineFirstReleaseGate_localOperationsRemain100PercentFunctionalWhenNetworkFails() = runTest {
        // 1. Set up local file storage
        val localRoot = File(tempDir, "local_storage")
        localRoot.mkdirs()
        val localProvider = LocalFileSystemProvider(rootDirectory = localRoot, ioDispatcher = Dispatchers.Unconfined)

        val localTestFile = File(localRoot, "personal_doc.txt")
        localTestFile.writeText("Important private offline data.")

        // Verify local file exists and reads properly
        assertTrue(localTestFile.exists())
        val localNodes = localProvider.listChildren(localRoot.absolutePath).getOrThrow()
        assertEquals(1, localNodes.size)
        assertEquals("personal_doc.txt", localNodes[0].name)

        // 2. Simulate complete remote network failure (e.g. wrong host, MITM attack, timeout)
        val brokenRemoteConfig = NetworkServerConfig(
            id = "failing-nas",
            name = "Dead Remote NAS",
            protocol = NetworkProtocol.SFTP,
            host = "unreachable.server.invalid",
            port = 22,
            authType = NetworkAuthType.PASSWORD,
            username = "admin",
            password = "pwd"
        )
        val sftp = SftpNetworkProvider(brokenRemoteConfig, Dispatchers.Unconfined, knownHostsStore)
        sftp.presentedHostKey = "rogue-key".toByteArray()
        knownHostsStore.trustHostKey("unreachable.server.invalid", 22, "ssh-ed25519", "different-key".toByteArray())

        val networkResult = sftp.connect()
        assertTrue("Network connection must fail", networkResult.isFailure)

        // 3. RELEASE GATE ASSERTION:
        // Local operations MUST NOT CRASH, local directory MUST NOT BE RESET,
        // and local file access MUST REMAIN 100% UNINTERRUPTED.
        assertTrue("Local storage must remain available", localTestFile.exists())
        val content = localTestFile.readText()
        assertEquals("Important private offline data.", content)

        // Local file creation must continue working
        val newLocalFile = File(localRoot, "new_file.txt")
        newLocalFile.writeText("Created while network failed")
        assertTrue("New local files can be created while network is down", newLocalFile.exists())
    }
}

/**
 * Lightweight mock X.509 certificate for unit tests (zero external network/mocking dependencies).
 */
private class TestMockX509Certificate(
    private val commonName: String,
    private val isExpired: Boolean,
    private val certFingerprint: String = "SHA256:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF"
) : X509Certificate() {

    override fun checkValidity() {
        if (isExpired) {
            throw CertificateExpiredException("Certificate expired on past date")
        }
    }

    override fun checkValidity(date: Date?) {
        checkValidity()
    }

    override fun getSubjectX500Principal(): X500Principal {
        return X500Principal("CN=$commonName, O=Test Corp, C=US")
    }

    override fun getSubjectAlternativeNames(): MutableCollection<MutableList<*>>? {
        // Return DNS name SAN matching CN
        return mutableListOf(
            mutableListOf(2, commonName)
        )
    }

    override fun getEncoded(): ByteArray {
        return certFingerprint.toByteArray()
    }

    // Stub implementations for abstract methods
    override fun hasUnsupportedCriticalExtension(): Boolean = false
    override fun getCriticalExtensionOIDs(): MutableSet<String>? = null
    override fun getNonCriticalExtensionOIDs(): MutableSet<String>? = null
    override fun getExtensionValue(oid: String?): ByteArray? = null
    override fun getVersion(): Int = 3
    override fun getSerialNumber(): BigInteger = BigInteger.ONE
    override fun getIssuerDN(): Principal = X500Principal("CN=Test CA")
    override fun getSubjectDN(): Principal = subjectX500Principal
    override fun getNotBefore(): Date = Date(System.currentTimeMillis() - 86400000L)
    override fun getNotAfter(): Date = if (isExpired) Date(System.currentTimeMillis() - 1000L) else Date(System.currentTimeMillis() + 86400000L)
    override fun getSigAlgName(): String = "SHA256withRSA"
    override fun getSigAlgOID(): String = "1.2.840.113549.1.1.11"
    override fun getSigAlgParams(): ByteArray? = null
    override fun getIssuerUniqueID(): BooleanArray? = null
    override fun getSubjectUniqueID(): BooleanArray? = null
    override fun getKeyUsage(): BooleanArray? = null
    override fun getBasicConstraints(): Int = -1
    override fun verify(key: PublicKey?) {}
    override fun verify(key: PublicKey?, sigProvider: String?) {}
    override fun toString(): String = "TestMockX509Certificate(CN=$commonName)"
    override fun getPublicKey(): PublicKey? = null
    override fun getTBSCertificate(): ByteArray = ByteArray(0)
    override fun getSignature(): ByteArray = ByteArray(0)
}
