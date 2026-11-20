package com.stillshelf.app.scrobbling

import com.stillshelf.app.core.datastore.SecureTokenStorage
import com.stillshelf.app.core.datastore.SecureStorageUnavailableException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ListeningSettingsStoreTest {
    @Test fun localRecordingDefaultsOffAndRestoresIndependentPreviousSource() = runTest {
        val store = ListeningSettingsStore(secrets)
        store.load()
        assertFalse(store.state.value.localRecordingEnabled)
        val connection = ListeningConnection(ListeningProvider.KOITO, "https://koito.invalid", "reader", "saved")
        store.update { it.saveConnection(connection, selectStatistics = true).copy(showStatistics = true,
            scrobblingEnabled = true, sendingConnectionId = connection.revision) }
        store.update { it.copy(localRecordingEnabled = true) }
        assertEquals(StatisticsSource.EXTERNAL, store.state.value.statisticsSource)
        store.update { it.copy(statisticsSource = StatisticsSource.LOCAL) }
        val reopened = ListeningSettingsStore(secrets)
        reopened.load()
        assertTrue(reopened.state.value.localRecordingEnabled)
        assertEquals(StatisticsSource.LOCAL, reopened.state.value.statisticsSource)
        reopened.update { it.copy(localRecordingEnabled = false) }
        assertEquals(StatisticsSource.EXTERNAL, reopened.state.value.statisticsSource)
        assertTrue(reopened.state.value.canSubmit)
        assertTrue(reopened.state.value.showStatistics)
        assertEquals("saved", reopened.state.value.connection?.token)
    }

    @Test fun disablingLocalWithRemovedPreviousConnectionSafelyUsesNativeHistory() = runTest {
        val store = ListeningSettingsStore(secrets)
        store.update { it.copy(localRecordingEnabled = true, statisticsSource = StatisticsSource.EXTERNAL,
            connection = ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "reader")) }
        store.update { it.copy(statisticsSource = StatisticsSource.LOCAL) }
        store.removeConnection()
        store.update { it.copy(localRecordingEnabled = false) }
        assertEquals(StatisticsSource.NAVIDROME, store.state.value.statisticsSource)
    }

    @Test fun legacyMigrationPreservesAllProvidersAndOnlyValidDirectSelectionsStayEnabled() = runTest {
        for (provider in ListeningProvider.entries) for (mode in ScrobblingSelection.entries) {
            val conn = ListeningConnection(provider, "https://service.invalid", "reader", "session", "api-key", "shared-secret")
            values["listening_configuration_v1"] = org.json.JSONObject()
                .put("method", if (mode == ScrobblingSelection.NAVIDROME) "NAVIDROME" else "STILL_SHELF")
                .put("enabled", mode == ScrobblingSelection.DIRECT).put("showStatistics", true)
                .put("statisticsSource", "EXTERNAL").put("connection", conn.toJson()).toString()
            val store = ListeningSettingsStore(secrets)
            store.load()
            assertEquals(mode == ScrobblingSelection.DIRECT, store.state.value.canSubmit)
            assertEquals(conn.revision, store.state.value.sendingConnection?.revision)
            assertEquals("session", store.state.value.connection?.token)
            assertEquals("api-key", store.state.value.connection?.apiKey)
            assertTrue(store.state.value.showStatistics)
            store.update { it }
            val reopened = ListeningSettingsStore(secrets)
            reopened.load()
            assertEquals(store.state.value.sendingConnectionId, reopened.state.value.sendingConnectionId)
            assertEquals(1, reopened.state.value.savedConnections.size)
            assertEquals(mode == ScrobblingSelection.DIRECT, reopened.state.value.canSubmit)
        }
    }

    @Test fun legacyDirectWithoutSendingCredentialsMigratesOffWithoutLosingStatistics() = runTest {
        values["listening_configuration_v1"] = org.json.JSONObject().put("method", "STILL_SHELF")
            .put("enabled", true).put("showStatistics", true).put("statisticsSource", "EXTERNAL")
            .put("connection", ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "reader").toJson()).toString()
        val store = ListeningSettingsStore(secrets)
        store.load()
        assertFalse(store.state.value.scrobblingEnabled)
        assertEquals("reader", store.state.value.connection?.username)
        assertTrue(store.state.value.showStatistics)
    }

    @Test fun twoConnectionsPersistIndependentSelectionsAndRemovalCannotRedirectSending() = runTest {
        val stats = ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "reader", token = "lb")
        val sending = ListeningConnection(ListeningProvider.KOITO, "https://koito.invalid", "reader", "koito")
        val store = ListeningSettingsStore(secrets)
        store.update { it.saveConnection(stats, selectStatistics = true).saveConnection(sending).copy(
            sendingConnectionId = sending.revision, scrobblingEnabled = true, showStatistics = true) }
        val reopened = ListeningSettingsStore(secrets)
        reopened.load()
        assertEquals("lb", reopened.state.value.connection?.token)
        assertEquals("koito", reopened.state.value.sendingConnection?.token)
        assertTrue(reopened.state.value.canSubmit)
        reopened.removeConnection(sending.revision)
        assertNull(reopened.state.value.sendingConnection)
        assertFalse(reopened.state.value.canSubmit)
        assertEquals("lb", reopened.state.value.connection?.token)
        assertEquals(1, reopened.state.value.savedConnections.size)
        assertTrue(reopened.state.value.showStatistics)
    }

    @Test fun addingSameAccountReusesConnectionAndPreservesCredentialsAndSendingPermission() {
        val old = ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "reader", token = "saved")
        val state = ListeningSettings(loaded = true, connection = old, scrobblingEnabled = true)
        val saved = state.saveConnection(ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "reader", allowSubmissions = false), selectStatistics = true)
        assertEquals(1, saved.savedConnections.size)
        assertEquals(old.revision, saved.connection?.revision)
        assertEquals("saved", saved.connection?.token)
        assertTrue(saved.canSubmit)
    }

    @Test fun changingAccountDisablesAffectedSenderButKeepsUnrelatedConnection() {
        val old = ListeningConnection(ListeningProvider.KOITO, "https://koito.invalid", "one", "old")
        val unrelated = ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "reader")
        val state = ListeningSettings(connection = unrelated, connections = listOf(old, unrelated),
            multipleConnections = true, sendingConnectionId = old.revision, scrobblingEnabled = true)
        val updated = state.saveConnection(ListeningConnection(ListeningProvider.KOITO, "https://koito.invalid", "two", "new"), old.revision)
        assertNull(updated.sendingConnection)
        assertFalse(updated.scrobblingEnabled)
        assertSame(unrelated, updated.connection)
        assertEquals(2, updated.savedConnections.size)
    }
    @Test fun statisticsOnlyPermissionPersistsAndLegacyConnectionsRemainCompatible() {
        val connection = ListeningConnection(ListeningProvider.KOITO, token = "test", allowSubmissions = false)
        assertFalse(ListeningConnection.fromJson(connection.toJson()).canSubmit)
        val legacy = connection.toJson().apply { remove("allowSubmissions") }
        assertTrue(ListeningConnection.fromJson(legacy).canSubmit)
    }
    private val values = mutableMapOf<String, String>()
    private val secrets = mockk<SecureTokenStorage> {
        coEvery { getNamedSecret(any()) } answers { values[firstArg()] }
        coEvery { saveNamedSecret(any(), any(), true, false) } answers { values[firstArg()] = secondArg() }
    }

    @Test fun methodAndToggleChangesRetainCredentialsAcrossReopening() = runTest {
        val store = ListeningSettingsStore(secrets)
        store.load()
        assertFalse(store.state.value.canSubmit)
        val connection = ListeningConnection(ListeningProvider.KOITO, "https://koito.invalid", token = "test-secret")
        store.update { it.copy(connection = connection, sendingConnectionId = connection.revision, method = ScrobblingMethod.STILL_SHELF, scrobblingEnabled = true, showStatistics = true, statisticsSource = StatisticsSource.EXTERNAL) }
        assertTrue(store.state.value.canSubmit)
        store.update { it.copy(scrobblingEnabled = false) }
        assertFalse(store.state.value.canSubmit)
        store.update { it.copy(method = ScrobblingMethod.NAVIDROME) }
        val reopened = ListeningSettingsStore(secrets)
        reopened.load()
        assertEquals("test-secret", reopened.state.value.connection?.token)
        assertTrue(reopened.state.value.showStatistics)
        assertEquals(StatisticsSource.EXTERNAL, reopened.state.value.statisticsSource)
        reopened.update { it.copy(method = ScrobblingMethod.STILL_SHELF, scrobblingEnabled = true) }
        assertTrue(reopened.state.value.canSubmit)
        reopened.update { it.copy(showStatistics = false) }
        assertTrue(reopened.state.value.canSubmit)
    }

    @Test fun removalOnlyClearsLocalConnectionAndSubmissionEnablement() = runTest {
        val store = ListeningSettingsStore(secrets)
        store.update { it.copy(connection = ListeningConnection(ListeningProvider.LISTEN_BRAINZ, token = "test"), showStatistics = true, statisticsSource = StatisticsSource.EXTERNAL) }
        store.removeConnection()
        val reopened = ListeningSettingsStore(secrets)
        reopened.load()
        assertNull(reopened.state.value.connection)
        assertFalse(reopened.state.value.scrobblingEnabled)
        assertTrue(reopened.state.value.showStatistics)
        assertEquals(StatisticsSource.EXTERNAL, reopened.state.value.statisticsSource)
        // No provider client exists in this storage operation, so it cannot delete remote history.
        coVerify(exactly = 0) { secrets.clearNamedSecret(any()) }
    }

    @Test fun failedSecurePersistenceDoesNotPublishNewSettings() = runTest {
        val store = ListeningSettingsStore(secrets)
        store.load()
        coEvery { secrets.saveNamedSecret(any(), any(), true, false) } throws SecureStorageUnavailableException()
        try { store.update { it.copy(scrobblingEnabled = true) }; fail("Expected secure storage failure") }
        catch (_: SecureStorageUnavailableException) { }
        assertFalse(store.state.value.scrobblingEnabled)
    }

    @Test fun connectionToStringDoesNotExposeSecretsAndReadOnlyLastFmCannotSubmit() {
        val connection = ListeningConnection(ListeningProvider.LAST_FM, username = "name", apiKey = "private-api-key", apiSecret = "private-api-secret")
        assertFalse(connection.toString().contains("private"))
        assertFalse(connection.canSubmit)
    }

    @Test fun repairedCredentialsKeepQueueOwnershipOnlyForTheSameAccount() {
        val old = ListeningConnection(ListeningProvider.KOITO, "https://koito.invalid", "reader", "expired", revision = "owner")
        val repaired = retainAccountRevision(old, ListeningConnection(ListeningProvider.KOITO, "https://koito.invalid", "reader", "new"))
        assertEquals("owner", repaired.revision)
        assertEquals("new", repaired.token)
        val other = retainAccountRevision(old, ListeningConnection(ListeningProvider.KOITO, "https://koito.invalid", "different", "new"))
        assertNotEquals("owner", other.revision)
    }
}
