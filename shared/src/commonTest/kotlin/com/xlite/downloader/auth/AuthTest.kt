package com.xlite.downloader.auth

import com.xlite.downloader.data.MemorySecretStore
import com.xlite.downloader.domain.engine.AuthManager
import com.xlite.downloader.domain.engine.XunleiCodes
import com.xlite.downloader.domain.model.AuthState
import com.xlite.downloader.testutil.FakePlatformPaths
import com.xlite.downloader.testutil.FakeXunleiSdkPort
import com.xlite.downloader.testutil.TestJwt
import com.xlite.downloader.testutil.dataRoot
import com.xlite.downloader.testutil.nowSeconds
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.fakefilesystem.FakeFileSystem

@OptIn(ExperimentalEncodingApi::class)
class JwtParserTest {
    @Test
    fun parsesExpiryAndStringAudience() {
        val audience = TestJwt.audienceFor("xl-6mVv12584")
        val token = TestJwt.create(audience, 1_900_000_000)
        val info = JwtParser.parse(token)
        assertNotNull(info)
        assertEquals(1_900_000_000, info.expiresAtEpochSeconds)
        assertEquals(audience, info.audience)
    }

    @Test
    fun acceptsAudienceArrayAndMissingPadding() {
        val payload = """{"exp":123,"aud":["abc","def"]}"""
        val body = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
            .encode(payload.encodeToByteArray())
        val info = JwtParser.parse("header.$body.")
        assertNotNull(info)
        assertEquals(123, info.expiresAtEpochSeconds)
        assertEquals("abc", info.audience)
    }

    @Test
    fun rejectsMalformedTokens() {
        assertNull(JwtParser.parse(""))
        assertNull(JwtParser.parse("only-one-part"))
        assertNull(JwtParser.parse("a.%%%invalid.signature"))
    }
}

@OptIn(ExperimentalEncodingApi::class)
class AppIdResolverTest {
    private val rawAppId = "xl-6mVv12584"

    @Test
    fun returnsAudienceVerbatimForSdkInit() {
        val audience = TestJwt.audienceFor(rawAppId)
        val resolved = AppIdResolver.fromAudience(audience)
        assertEquals(audience, resolved)
        assertEquals(rawAppId, AppIdResolver.displayName(resolved!!))
    }

    @Test
    fun acceptsUrlSafeAlphabetFromJwt() {
        val audience = TestJwt.audienceFor(rawAppId)
            .replace('+', '-')
            .replace('/', '_')
            .trimEnd('=')
        val resolved = AppIdResolver.fromAudience(audience)
        assertNotNull(resolved)
        assertEquals(rawAppId, AppIdResolver.displayName(resolved))
    }

    @Test
    fun rejectsBlankAndBinaryOnlyAudience() {
        assertNull(AppIdResolver.fromAudience(null))
        assertNull(AppIdResolver.fromAudience("   "))
        val binary = Base64.Default.encode(byteArrayOf(0, 0, 0))
        assertNull(AppIdResolver.fromAudience(binary))
    }

    @Test
    fun displayNameToleratesMissingPadding() {
        val unpadded = Base64.Default.encode("xl-abc".encodeToByteArray() + byteArrayOf(0, 9))
            .trimEnd('=')
        assertEquals("xl-abc", AppIdResolver.displayName(unpadded))
    }
}

class AuthManagerTest {
    private fun manager(
        port: FakeXunleiSdkPort = FakeXunleiSdkPort(),
        store: MemorySecretStore = MemorySecretStore("xl_testkey_1234567890"),
    ): Triple<AuthManager, FakeXunleiSdkPort, MemorySecretStore> {
        val fs = FakeFileSystem()
        val auth = AuthManager(port, store, FakePlatformPaths(dataRoot()), fs, "0.1.0-test")
        return Triple(auth, port, store)
    }

    @Test
    fun initializeUsesStoredKeyAndDerivesAppId() = runTest {
        val (auth, port, store) = manager()
        val ok = auth.initialize(null)
        assertTrue(ok)
        assertIs<AuthState.Ready>(auth.auth.value)
        assertEquals(TestJwt.audienceFor("xl-test"), auth.appId)
        assertEquals("xl_testkey_1234567890", store.readApiKey())
        assertEquals(TestJwt.audienceFor("xl-test"), port.initArgs?.first)
        assertEquals("xl-test", auth.appId?.let { AppIdResolver.displayName(it) })
        assertNotNull(port.initArgs)
        assertTrue(port.initArgs!!.third.endsWith("xunlei"))
        assertTrue(port.startedTasks.isEmpty())
    }

    @Test
    fun initializeWithoutStoredKeyStaysNotConfigured() = runTest {
        val (auth, _, _) = manager(store = MemorySecretStore(null))
        assertFalse(auth.initialize(null))
        assertIs<AuthState.NotConfigured>(auth.auth.value)
        assertFalse(auth.isReady())
    }

    @Test
    fun invalidTokenIsReportedWithoutTouchingInit() = runTest {
        val port = FakeXunleiSdkPort().apply { loginTokenCode = XunleiCodes.APPNAME_APPKEY_ERROR }
        val (auth, _, _) = manager(port = port)
        assertFalse(auth.initialize(null))
        assertIs<AuthState.Error>(auth.auth.value)
        assertNull(port.initArgs)
        assertNull(auth.apiKey)
    }

    @Test
    fun initFailureSurfacesMappedMessage() = runTest {
        val port = FakeXunleiSdkPort().apply { initResult = XunleiCodes.APPNAME_APPKEY_ERROR }
        val (auth, _, _) = manager(port = port)
        assertFalse(auth.initialize(null))
        val state = auth.auth.value
        assertIs<AuthState.Error>(state)
        assertTrue(state.message.contains("API Key"))
    }

    @Test
    fun loginFailureDoesNotBecomeReady() = runTest {
        val port = FakeXunleiSdkPort().apply { loginResult = XunleiCodes.AUTH_TOKEN_VERIFY_FAILED }
        val (auth, _, _) = manager(port = port)
        assertFalse(auth.initialize(null))
        assertIs<AuthState.Error>(auth.auth.value)
        assertNull(auth.token)
    }

    @Test
    fun appIdOverrideTakesPrecedence() = runTest {
        val (auth, port, _) = manager()
        assertTrue(auth.authenticate("xl_testkey_1234567890", "override-app"))
        assertEquals("override-app", auth.appId)
        assertEquals("override-app", port.initArgs?.first)
    }

    @Test
    fun testConnectionReportsDerivedAppIdAndExpiry() = runTest {
        val (auth, _, _) = manager()
        val result = auth.testConnection("xl_testkey_1234567890", null)
        assertTrue(result.success)
        assertEquals(TestJwt.audienceFor("xl-test"), result.appId)
        assertTrue((result.tokenExpiresAtEpochSeconds ?: 0) > nowSeconds())
        assertNull(auth.apiKey)
    }

    @Test
    fun testConnectionFailsForInvalidKey() = runTest {
        val port = FakeXunleiSdkPort().apply { loginTokenCode = XunleiCodes.APPNAME_APPKEY_ERROR }
        val (auth, _, _) = manager(port = port)
        val result = auth.testConnection("xl_wrong_key_0000", null)
        assertFalse(result.success)
        assertFalse(result.message.isBlank())
    }

    @Test
    fun refreshRotatesTokenAndKeepsReady() = runTest {
        var tokenCounter = 0
        val port = FakeXunleiSdkPort(tokenFor = {
            tokenCounter++
            TestJwt.create(TestJwt.audienceFor("xl-test"), nowSeconds() + 3_600L + tokenCounter)
        })
        val (auth, _, _) = manager(port = port)
        assertTrue(auth.initialize(null))
        val before = auth.token
        assertTrue(auth.refresh(force = true))
        assertNotNull(auth.token)
        assertTrue(auth.token != before)
        assertIs<AuthState.Ready>(auth.auth.value)
    }

    @Test
    fun clearStoredKeyResetsState() = runTest {
        val (auth, _, store) = manager()
        assertTrue(auth.initialize(null))
        auth.clearStoredKey()
        assertNull(store.readApiKey())
        assertNull(auth.token)
        assertIs<AuthState.NotConfigured>(auth.auth.value)
    }
}
