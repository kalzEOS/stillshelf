package com.stillshelf.app.scrobbling

import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ListeningSettingsViewModelTest {
    @Test fun selectionChangesPreserveStatisticsSourceVisibilityAndCredentials() {
        val original = ListeningSettings(loaded = true, showStatistics = true, statisticsSource = StatisticsSource.EXTERNAL,
            connection = ListeningConnection(ListeningProvider.KOITO, token = "saved"))
        val direct = original.withSelection(ScrobblingSelection.DIRECT)
        assertTrue(direct.canSubmit)
        val off = direct.withSelection(ScrobblingSelection.OFF)
        assertFalse(off.canSubmit)
        assertEquals(ScrobblingSelection.OFF, off.selection)
        val native = off.withSelection(ScrobblingSelection.NAVIDROME)
        assertFalse(native.canSubmit)
        assertEquals(ScrobblingSelection.NAVIDROME, native.selection)
        assertEquals(original.statisticsSource, native.statisticsSource)
        assertEquals(original.showStatistics, native.showStatistics)
        assertEquals("saved", native.connection?.token)
    }
    @Before fun before() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun after() { Dispatchers.resetMain() }
    private val connection = ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "reader", token = "saved-token")
    private val state = MutableStateFlow(ListeningSettings(loaded = true, connection = connection))
    private val store = mockk<ListeningSettingsStore> {
        every { this@mockk.state } returns this@ListeningSettingsViewModelTest.state
        coEvery { load() } returns Unit
        coEvery { update(any()) } coAnswers {
            this@ListeningSettingsViewModelTest.state.value = firstArg<(ListeningSettings) -> ListeningSettings>()(this@ListeningSettingsViewModelTest.state.value)
        }
    }
    private val client = mockk<ListeningProviderClient> {
        coEvery { validate(any()) } returns "reader"
        coEvery { statistics(any()) } returns ListeningStatistics("Provider", 0)
    }
    private val clients = mockk<ListeningClients> { every { forProvider(any()) } returns client }
    private fun vm() = ListeningSettingsViewModel(store, clients, mockk(relaxed = true), mockk(relaxed = true))

    @Test fun statisticsOnlyConnectionCannotSubmitEvenWithPreviouslyEnabledSwitch() = runTest {
        state.value = state.value.copy(scrobblingEnabled = true, connection = null)
        val vm = vm()
        vm.saveStatisticsConnection(connection)
        assertEquals(StatisticsSource.EXTERNAL, state.value.statisticsSource)
        assertFalse(state.value.canSubmit)
        assertFalse(state.value.connection!!.allowSubmissions)
        assertEquals("saved-token", state.value.connection!!.token)
        vm.enableDirect(state.value.connection!!.revision)
        assertTrue(state.value.canSubmit)
        vm.setScrobblingEnabled(false)
        assertEquals("saved-token", state.value.connection!!.token)
        assertFalse(state.value.canSubmit)
    }

    @Test fun statisticsSetupPreservesNavidromeMethodAndVisibility() = runTest {
        state.value = state.value.copy(method = ScrobblingMethod.NAVIDROME, showStatistics = true)
        vm().saveStatisticsConnection(connection)
        assertEquals(ScrobblingMethod.NAVIDROME, state.value.method)
        assertTrue(state.value.showStatistics)
        assertFalse(state.value.canSubmit)
    }

    @Test fun enablingDirectUsesSelectedSendingAccountAndNeverChangesStatisticsSource() = runTest {
        val other = ListeningConnection(ListeningProvider.KOITO, "https://koito.invalid", "reader", "koito-token", allowSubmissions = false)
        state.value = state.value.copy(connections = listOf(connection, other), multipleConnections = true,
            showStatistics = true, statisticsSource = StatisticsSource.EXTERNAL)
        val vm = vm()
        vm.enableDirect(other.revision)
        assertTrue(state.value.canSubmit)
        assertEquals("koito-token", state.value.sendingConnection?.token)
        assertSame(connection, state.value.connection)
        assertTrue(state.value.showStatistics)
        coVerify { client.validate(other) }
        vm.setScrobblingEnabled(false)
        assertFalse(state.value.canSubmit)
        assertEquals(2, state.value.savedConnections.size)
    }

    @Test fun readOnlyOrRejectedSendingCredentialsCannotEnableDirect() = runTest {
        val readOnly = ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "reader")
        state.value = state.value.copy(connection = readOnly)
        val vm = vm()
        vm.enableDirect(readOnly.revision)
        assertFalse(state.value.canSubmit)
        coVerify(exactly = 0) { client.validate(any()) }
        state.value = state.value.copy(connection = connection)
        coEvery { client.validate(any()) } throws ListeningApiException(401, false, "Invalid token")
        vm.enableDirect(connection.revision)
        assertFalse(state.value.canSubmit)
        assertEquals("Invalid token", vm.message.value)
    }

    @Test fun verifyCallsProviderAndReportsSuccessWithoutRewritingCredentials() = runTest {
        val vm = vm()
        vm.verifyConnection()
        coVerify(exactly = 1) { client.validate(connection) }
        coVerify(exactly = 1) { client.statistics(any()) }
        assertTrue(vm.message.value!!.contains("Statistics access and scrobbling credentials checked"))
        assertSame(connection, state.value.connection)
        coVerify(exactly = 0) { store.update(any()) }
    }

    @Test fun verifyReportsProviderErrorAndKeepsSavedConnection() = runTest {
        coEvery { client.validate(any()) } throws ListeningApiException(401, false, "Token rejected. Check the API token.")
        val vm = vm()
        vm.verifyConnection()
        assertEquals("Token rejected. Check the API token.", vm.message.value)
        assertSame(connection, state.value.connection)
        assertFalse(vm.busy.value)
    }

    @Test fun verifyDistinguishesValidCredentialsFromUnavailableStatistics() = runTest {
        coEvery { client.statistics(any()) } throws ListeningConnectionException("Provider timed out")
        val vm = vm()
        vm.verifyConnection()
        assertEquals("Credentials verified, but statistics could not be read: Provider timed out", vm.message.value)
        assertSame(connection, state.value.connection)
    }

    @Test fun failedConnectionEditRetainsSavedCredentialsAndReportsActualTransportFailure() = runTest {
        coEvery { client.validate(any()) } throws ListeningConnectionException("api.listenbrainz.org did not respond in time.")
        val vm = vm()
        vm.save(ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "reader", token = "replacement"))
        assertSame(connection, state.value.connection)
        assertEquals("saved-token", state.value.connection?.token)
        assertTrue(vm.message.value!!.contains("did not respond in time"))
        assertFalse(vm.networkBusy.value)
    }

    @Test fun localSettingsSaveNeverStartsNetworkProgress() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { store.update(any()) } coAnswers { gate.await() }
        val vm = vm()
        vm.update { it.copy(method = ScrobblingMethod.NAVIDROME) }
        assertTrue(vm.busy.value)
        assertFalse(vm.networkBusy.value)
        coVerify(exactly = 0) { client.validate(any()) }
        gate.complete(Unit)
    }
}
