package com.xlite.downloader.util

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class UrlValidatorTest {
    @Test
    fun acceptsHttpAndHttps() {
        val http = UrlValidator.parse("http://example.com/file.zip")
        assertIs<UrlParseResult.Valid>(http)
        assertEquals("http://example.com/file.zip", http.url)
        assertIs<UrlParseResult.Valid>(UrlValidator.parse("https://cdn.example.com/a/b.iso?x=1"))
    }

    @Test
    fun rejectsUnsupportedSchemesWithChineseReason() {
        for (input in listOf(
            "magnet:?xt=urn:btih:abc",
            "ftp://example.com/file.zip",
            "ftps://example.com/file.zip",
            "ed2k://|file|x|1|hash|/",
            "file:///C:/a.txt",
        )) {
            val parsed = UrlValidator.parse(input)
            assertIs<UrlParseResult.Unsupported>(parsed, "expected unsupported: $input")
            assertTrue(parsed.message.contains("HTTP"), "reason should mention HTTP: ${parsed.message}")
        }
    }

    @Test
    fun rejectsMalformedHttpUrls() {
        assertIs<UrlParseResult.Invalid>(UrlValidator.parse("https://"))
        assertIs<UrlParseResult.Invalid>(UrlValidator.parse("https://a b/c"))
        assertIs<UrlParseResult.Invalid>(UrlValidator.parse("https://example.com\u0000/a"))
    }

    @Test
    fun treatsBlankAsBlank() {
        assertIs<UrlParseResult.Blank>(UrlValidator.parse("   "))
        assertIs<UrlParseResult.Blank>(UrlValidator.parse(""))
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun decodesThunderLinks() {
        val payload = Base64.Default.encode("AAhttps://example.com/a%20b.zipZZ".encodeToByteArray())
        val parsed = UrlValidator.parse("thunder://$payload")
        assertIs<UrlParseResult.Valid>(parsed)
        assertEquals("https://example.com/a%20b.zip", parsed.url)
        assertTrue(parsed.decodedThunder)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun rejectsThunderLinksThatAreNotHttp() {
        val payload = Base64.Default.encode("AAmagnet:?xt=urn:btih:abcZZ".encodeToByteArray())
        assertIs<UrlParseResult.Invalid>(UrlValidator.parse("thunder://$payload"))
        assertIs<UrlParseResult.Invalid>(UrlValidator.parse("thunder://not-base64%%"))
    }

    @Test
    fun splitsAndTrimsInput() {
        val parts = UrlValidator.splitInput(
            " https://a.com/1.zip\r\nhttps://a.com/2.zip\t\n\n  https://a.com/3.zip ",
        )
        assertEquals(listOf("https://a.com/1.zip", "https://a.com/2.zip", "https://a.com/3.zip"), parts)
    }
}

class FileNameUtilsTest {
    @Test
    fun derivesNameFromUrlWithPercentDecoding() {
        assertEquals("中文 a.zip", FileNameUtils.fromUrl("https://x.com/dir/%E4%B8%AD%E6%96%87%20a.zip", 1))
        assertEquals("file.iso", FileNameUtils.fromUrl("https://x.com/file.iso?token=abc#frag", 1))
        assertEquals("download-42", FileNameUtils.fromUrl("https://x.com/", 42))
    }

    @Test
    fun sanitizesIllegalCharactersAndReservedNames() {
        assertEquals("a_b_c_.zip", FileNameUtils.sanitize("a/b:c*.zip", "fallback"))
        assertEquals("_CON", FileNameUtils.sanitize("CON", "fallback"))
        assertEquals("name", FileNameUtils.sanitize("name...   ", "fallback"))
        assertEquals("fallback", FileNameUtils.sanitize("   ", "fallback"))
    }

    @Test
    fun truncatesLongNamesKeepingExtension() {
        val long = "a".repeat(300) + ".mp4"
        val name = FileNameUtils.sanitize(long, "fallback")
        assertEquals(180, name.length)
        assertTrue(name.endsWith(".mp4"))
    }

    @Test
    fun percentDecodeLeavesInvalidSequences() {
        assertEquals("100%", FileNameUtils.percentDecode("100%"))
        assertEquals("a%zzb", FileNameUtils.percentDecode("a%zzb"))
        assertEquals("中", FileNameUtils.percentDecode("%E4%B8%AD"))
    }

    @Test
    fun uniqueNameAvoidsExistingFilesAndReservedNames() {
        val fs = FakeFileSystem()
        val dir = "/downloads".toPath()
        fs.createDirectories(dir)
        fs.write(dir / "a.zip") { writeUtf8("x") }
        assertEquals("a (1).zip", FileNameUtils.uniqueName(dir, "a.zip", fs))
        assertEquals("a (2).zip", FileNameUtils.uniqueName(dir, "a.zip", fs, setOf("a (1).zip")))
        assertEquals("b.zip", FileNameUtils.uniqueName(dir, "b.zip", fs, setOf("other.zip")))
    }
}

class RedactorTest {
    @Test
    fun masksApiKeysAndJwt() {
        val text = "key=xl_0123456789abcdef0123456789abcdef token=eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjF9.sig"
        val redacted = Redactor.redact(text)
        assertTrue(!redacted.contains("xl_0123456789"))
        assertTrue(redacted.contains("REDACTED"))
        assertTrue(!redacted.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertTrue(!redacted.contains("eyJleHAiOjF9"))
    }

    @Test
    fun masksSecretQueryParameters() {
        val redacted = Redactor.redact("https://x.com/a?token=abc123&sign=zzz&name=ok")
        assertTrue(!redacted.contains("abc123"))
        assertTrue(!redacted.contains("zzz"))
        assertTrue(redacted.contains("name=ok"))
    }

    @Test
    fun hostAndPathDropsQueryCredentialsAndFragment() {
        assertEquals(
            "https://example.com/a/b.zip",
            Redactor.hostAndPath("https://user:pw@example.com/a/b.zip?token=secret#frag"),
        )
        assertEquals("http://example.com", Redactor.hostAndPath("http://example.com?x=1"))
        assertEquals("https://example.com", Redactor.hostAndPath("https://example.com"))
    }
}
