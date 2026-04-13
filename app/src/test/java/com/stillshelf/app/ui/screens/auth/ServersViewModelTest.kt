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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
class ServersViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region server selection

    @Test
    fun onServerSelected_onSuccess_emitsSelectionApplied() = runTest(dispatcher) {
        val viewModel = buildViewModel(setActiveServerResult = AppResult.Success(Unit))
        val events = mutableListOf<ServersEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onServerSelected("srv1")
        advanceUntilIdle()
        job.cancel()

        assertTrue(events.any { it is ServersEvent.SelectionApplied })
    }

    @Test
    fun onServerSelected_onNoSavedSession_emitsNavigateToLogin() = runTest(dispatcher) {
        val server = testServer("srv1", "Home", "http://home.local")
        val viewModel = buildViewModel(
            servers = listOf(server),
            setActiveServerResult = AppResult.Error("No saved session for this server.")
        )
        // Subscribe to uiState to activate the combine() upstream, then advance so it emits
        val stateJob = launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val events = mutableListOf<ServersEvent>()
        val eventsJob = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onServerSelected("srv1")
        advanceUntilIdle()
        stateJob.cancel()
        eventsJob.cancel()

        val event = events.filterIsInstance<ServersEvent.NavigateToLogin>().firstOrNull()
        assertTrue("Expected NavigateToLogin event", event != null)
        assertEquals("Home", event?.serverName)
    }

    @Test
    fun onServerSelected_onOtherError_setsErrorMessage() = runTest(dispatcher) {
        val viewModel = buildViewModel(setActiveServerResult = AppResult.Error("Timeout"))
        val job = launch { viewModel.uiState.collect { } }

        viewModel.onServerSelected("srv1")
        advanceUntilIdle()
        job.cancel()

        assertEquals("Timeout", viewModel.uiState.value.errorMessage)
    }

    // endregion

    // region update / delete

    @Test
    fun updateServer_onSuccess_clearsBusy() = runTest(dispatcher) {
        val viewModel = buildViewModel(updateServerResult = AppResult.Success(Unit))
        val job = launch { viewModel.uiState.collect { } }

        viewModel.updateServer("srv1", "New Name", "http://new.local")
        advanceUntilIdle()
        job.cancel()

        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun updateServer_onError_setsErrorMessage() = runTest(dispatcher) {
        val viewModel = buildViewModel(updateServerResult = AppResult.Error("Update failed"))
        val job = launch { viewModel.uiState.collect { } }

        viewModel.updateServer("srv1", "New Name", "http://new.local")
        advanceUntilIdle()
        job.cancel()

        assertEquals("Update failed", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun deleteServer_onError_setsErrorMessage() = runTest(dispatcher) {
        val viewModel = buildViewModel(deleteServerResult = AppResult.Error("Delete failed"))
        val job = launch { viewModel.uiState.collect { } }

        viewModel.deleteServer("srv1")
        advanceUntilIdle()
        job.cancel()

        assertEquals("Delete failed", viewModel.uiState.value.errorMessage)
    }

    // endregion

    // region clearError

    @Test
    fun clearError_afterError_resetsErrorMessage() = runTest(dispatcher) {
        val viewModel = buildViewModel(setActiveServerResult = AppResult.Error("Timeout"))
        val job = launch { viewModel.uiState.collect { } }

        viewModel.onServerSelected("srv1")
        advanceUntilIdle()

        viewModel.clearError()
        advanceUntilIdle()
        job.cancel()

        assertNull(viewModel.uiState.value.errorMessage)
    }

    // endregion

    // region helpers

    @Suppress("UNCHECKED_CAST")
    private fun buildViewModel(
        servers: List<Server> = emptyList(),
        activeServerId: String? = null,
        setActiveServerResult: AppResult<Unit> = AppResult.Success(Unit),
        updateServerResult: AppResult<Unit> = AppResult.Success(Unit),
        deleteServerResult: AppResult<Unit> = AppResult.Success(Unit)
    ): ServersViewModel {
        val repo = Proxy.newProxyInstance(
            SessionRepository::class.java.classLoader,
            arrayOf(SessionRepository::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "observeServers" -> flowOf(servers)
                "observeSessionState" -> flowOf(SessionState(activeServerId = activeServerId, activeLibraryId = null))
                "observeBookProgressMutations" -> emptyFlow<BookProgressMutation>()
                "observeLibrariesForActiveServer" -> emptyFlow<List<Library>>()
                "setActiveServer" -> setActiveServerResult
                "updateServer" -> updateServerResult
                "deleteServer" -> deleteServerResult
                "hashCode" -> System.identityHashCode(this)
                "equals" -> false
                "toString" -> "TestRepo"
                else -> throw UnsupportedOperationException("Unexpected: ${method.name}")
            }
        } as SessionRepository
        return ServersViewModel(repo)
    }

    private fun testServer(id: String, name: String, baseUrl: String) = Server(
        id = id,
        name = name,
        baseUrl = baseUrl,
        createdAt = 0L
    )

    // endregion
}
