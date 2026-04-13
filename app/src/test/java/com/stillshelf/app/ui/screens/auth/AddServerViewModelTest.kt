package com.stillshelf.app.ui.screens.auth

import com.stillshelf.app.core.model.BookProgressMutation
import com.stillshelf.app.core.model.Library
import com.stillshelf.app.core.model.Server
import com.stillshelf.app.core.model.SessionState
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddServerViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region server name validation

    @Test
    fun onServerNameChange_withBlankName_setsRequiredError() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        viewModel.onServerNameChange("")
        assertEquals("Server name is required.", viewModel.uiState.value.serverNameError)
    }

    @Test
    fun onServerNameChange_withSingleChar_setsTooShortError() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        viewModel.onServerNameChange("A")
        assertEquals("Server name must be at least 2 characters.", viewModel.uiState.value.serverNameError)
    }

    @Test
    fun onServerNameChange_withInvalidChar_setsInvalidCharError() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        viewModel.onServerNameChange("My@Server")
        assertFalse(viewModel.uiState.value.serverNameError.isNullOrBlank())
    }

    @Test
    fun onServerNameChange_withValidName_clearError() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        viewModel.onServerNameChange("My Server")
        assertNull(viewModel.uiState.value.serverNameError)
    }

    @Test
    fun onServerNameChange_updatesNameInState() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        viewModel.onServerNameChange("Demo")
        assertEquals("Demo", viewModel.uiState.value.serverName)
    }

    // endregion

    // region URL / canContinue

    @Test
    fun onBaseUrlChange_stripsSpaces() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        viewModel.onBaseUrlChange("http://my server.local")
        assertEquals("http://myserver.local", viewModel.uiState.value.baseUrl)
    }

    @Test
    fun canContinue_whenBothNameAndUrlProvided_isTrue() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        viewModel.onServerNameChange("Home")
        viewModel.onBaseUrlChange("http://192.168.1.1")
        assertTrue(viewModel.uiState.value.canContinue)
    }

    @Test
    fun canContinue_whenNameHasError_isFalse() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        viewModel.onServerNameChange("A")
        viewModel.onBaseUrlChange("http://192.168.1.1")
        assertFalse(viewModel.uiState.value.canContinue)
    }

    @Test
    fun canContinue_whenUrlIsBlank_isFalse() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        viewModel.onServerNameChange("Home")
        viewModel.onBaseUrlChange("")
        assertFalse(viewModel.uiState.value.canContinue)
    }

    // endregion

    // region duplicate server detection

    @Test
    fun duplicateServerMessage_withMatchingUrl_returnsMessage() = runTest(dispatcher) {
        val existingServer = testServer(id = "srv1", name = "Existing", baseUrl = "http://192.168.1.1")
        val viewModel = buildViewModel(knownServers = listOf(existingServer))
        advanceUntilIdle()

        val message = viewModel.duplicateServerMessage("http://192.168.1.1/")
        assertFalse(message.isNullOrBlank())
        assertTrue(message!!.contains("Existing"))
    }

    @Test
    fun duplicateServerMessage_withNoMatch_returnsNull() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertNull(viewModel.duplicateServerMessage("http://newserver.local"))
    }

    @Test
    fun duplicateServerMessage_withBlankUrl_returnsNull() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        assertNull(viewModel.duplicateServerMessage("  "))
    }

    // endregion

    // region test connection

    @Test
    fun onTestConnectionClick_onSuccess_setsConnectionSuccess() = runTest(dispatcher) {
        val viewModel = buildViewModel(connectionResult = AppResult.Success("Audiobookshelf 2.x"))
        viewModel.onBaseUrlChange("http://192.168.1.1")
        viewModel.onTestConnectionClick()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.connectionSuccess == true)
        assertEquals("Audiobookshelf 2.x", viewModel.uiState.value.connectionMessage)
    }

    @Test
    fun onTestConnectionClick_onError_setsConnectionFailure() = runTest(dispatcher) {
        val viewModel = buildViewModel(connectionResult = AppResult.Error("Connection refused"))
        viewModel.onBaseUrlChange("http://192.168.1.1")
        viewModel.onTestConnectionClick()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.connectionSuccess == false)
        assertEquals("Connection refused", viewModel.uiState.value.connectionMessage)
    }

    @Test
    fun onTestConnectionClick_withBlankUrl_doesNothing() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        viewModel.onTestConnectionClick()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.connectionMessage)
    }

    // endregion

    // region helpers

    @Suppress("UNCHECKED_CAST")
    private fun buildViewModel(
        knownServers: List<Server> = emptyList(),
        connectionResult: AppResult<String> = AppResult.Success("OK")
    ): AddServerViewModel {
        val repo = Proxy.newProxyInstance(
            SessionRepository::class.java.classLoader,
            arrayOf(SessionRepository::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "observeServers" -> flowOf(knownServers)
                "observeSessionState" -> emptyFlow<SessionState>()
                "observeBookProgressMutations" -> emptyFlow<BookProgressMutation>()
                "observeLibrariesForActiveServer" -> emptyFlow<List<Library>>()
                "testServerConnection" -> connectionResult
                "hashCode" -> System.identityHashCode(this)
                "equals" -> false
                "toString" -> "TestRepo"
                else -> throw UnsupportedOperationException("Unexpected: ${method.name}")
            }
        } as SessionRepository
        return AddServerViewModel(repo)
    }

    private fun testServer(id: String, name: String, baseUrl: String) = Server(
        id = id,
        name = name,
        baseUrl = baseUrl,
        createdAt = 0L
    )

    // endregion
}
