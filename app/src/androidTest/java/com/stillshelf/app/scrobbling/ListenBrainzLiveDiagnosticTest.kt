package com.stillshelf.app.scrobbling

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in, read-only diagnostic. Uses a public username; never accepts or prints a token. */
class ListenBrainzLiveDiagnosticTest {
    @Test fun publicAccountCanBeReadFromDevice() = runBlocking {
        val username = InstrumentationRegistry.getArguments().getString("listenBrainzLiveUser")
        assumeTrue("Live diagnostics require an explicit public username", !username.isNullOrBlank())
        val connection = ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = username!!, allowSubmissions = false)
        val client = ListenBrainzClient(ListeningHttp())
        withTimeout(25_000) {
            assertEquals(username, client.validate(connection))
            val stats = client.statistics(connection)
            println("ListenBrainz live read completed; countAvailable=${stats.totalScrobbles != null}; partial=${stats.note != null}")
        }
    }
}
