package com.stillshelf.app.scrobbling

import io.mockk.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ListeningArtistAliasesTest {
    private val mbid = "b3f5bc93-c962-458f-961e-d861e6e0c828"
    @Test fun aliasesAreIdentityCheckedReadOnlyAndNeverFetchedByScrolling() = runTest {
        val http = mockk<ListeningHttp> {
            coEvery { json(any()) } answers {
                val request = firstArg<Request>()
                assertEquals("GET", request.method)
                assertEquals("musicbrainz.org", request.url.host)
                assertNull(request.header("Authorization"))
                JSONObject("""{"id":"$mbid","name":"فضل شاكر","aliases":[{"name":"Fadel Shaker"}]}""")
            }
        }
        val aliases = ListeningArtistAliases(http)
        assertTrue(aliases.names(mbid, false).isEmpty())
        coVerify(exactly = 0) { http.json(any()) }
        assertEquals(setOf("فضل شاكر", "Fadel Shaker"), aliases.names(mbid, true))
        assertTrue(aliases.names(mbid, false).contains("Fadel Shaker"))
        aliases.names(mbid, true)
        coVerify(exactly = 1) { http.json(any()) }
    }
    @Test fun timeoutsAndWrongIdentitiesNeverInventAliasesOrImmediatelyRetry() = runTest {
        val http = mockk<ListeningHttp> { coEvery { json(any()) } coAnswers { awaitCancellation() } }
        val aliases = ListeningArtistAliases(http)
        assertTrue(aliases.names(mbid, true).isEmpty())
        assertTrue(aliases.names(mbid, true).isEmpty())
        coVerify(exactly = 1) { http.json(any()) }
        val wrong = ListeningArtistAliases(mockk { coEvery { json(any()) } returns JSONObject("""{"id":"different","name":"Wrong Artist"}""") })
        assertTrue(wrong.names(mbid, true).isEmpty())
    }
}
