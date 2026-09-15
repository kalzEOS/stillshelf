package com.stillshelf.app.downloads.navidrome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NavidromeDownloadWorkerPolicyTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun fileCoordinator_preventsCancelledDownloadFromBeingFinalized() {
        val targetFile = temporaryFolder.newFile("song.mp3")
        assertTrue(NavidromeDownloadFileCoordinator.prepare(targetFile, "first"))
        NavidromeDownloadFileCoordinator.temporaryFile(targetFile, "first").writeText("partial")

        NavidromeDownloadFileCoordinator.cancelAndDelete(targetFile, "first")

        assertFalse(NavidromeDownloadFileCoordinator.finalizeDownload(targetFile, "first"))
        assertFalse(targetFile.exists())
        assertFalse(NavidromeDownloadFileCoordinator.temporaryFile(targetFile, "first").exists())
    }

    @Test
    fun fileCoordinator_finalizesPreparedDownload() {
        val targetFile = temporaryFolder.newFile("song.mp3")
        assertTrue(NavidromeDownloadFileCoordinator.prepare(targetFile, "first"))
        NavidromeDownloadFileCoordinator.temporaryFile(targetFile, "first").writeText("complete")

        assertTrue(NavidromeDownloadFileCoordinator.finalizeDownload(targetFile, "first"))
        assertEquals("complete", targetFile.readText())
    }

    @Test
    fun fileCoordinator_oldTransferCannotAffectReplacement() {
        val targetFile = temporaryFolder.newFile("song.mp3")
        assertTrue(NavidromeDownloadFileCoordinator.prepare(targetFile, "first"))
        assertTrue(NavidromeDownloadFileCoordinator.prepare(targetFile, "second"))
        NavidromeDownloadFileCoordinator.temporaryFile(targetFile, "first").writeText("old")
        NavidromeDownloadFileCoordinator.temporaryFile(targetFile, "second").writeText("new")

        NavidromeDownloadFileCoordinator.cancelAndDelete(targetFile, "first")

        assertFalse(NavidromeDownloadFileCoordinator.finalizeDownload(targetFile, "first"))
        assertTrue(NavidromeDownloadFileCoordinator.finalizeDownload(targetFile, "second"))
        assertEquals("new", targetFile.readText())
    }

    @Test
    fun calculateProgress_reservesOneHundredForSuccessfulCompletion() {
        assertEquals(0, calculateNavidromeDownloadProgress(0, 100))
        assertEquals(50, calculateNavidromeDownloadProgress(50, 100))
        assertEquals(99, calculateNavidromeDownloadProgress(100, 100))
    }

    @Test
    fun calculateProgress_handlesUnknownContentLength() {
        assertEquals(0, calculateNavidromeDownloadProgress(50, -1))
    }

    @Test
    fun retryableHttpCodes_includeTransientServerFailures() {
        assertTrue(isRetryableNavidromeHttpCode(408))
        assertTrue(isRetryableNavidromeHttpCode(429))
        assertTrue(isRetryableNavidromeHttpCode(503))
        assertFalse(isRetryableNavidromeHttpCode(401))
    }

    @Test
    fun contentRange_mustResumeAtRequestedByte() {
        assertTrue(isValidNavidromeContentRange("bytes 1024-2047/4096", 1024))
        assertFalse(isValidNavidromeContentRange("bytes 0-2047/4096", 1024))
        assertFalse(isValidNavidromeContentRange(null, 1024))
    }
}
