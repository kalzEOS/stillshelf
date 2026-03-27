package com.stillshelf.app.ui.screens

import com.stillshelf.app.core.model.BookProgressMutation
import com.stillshelf.app.core.model.Library
import com.stillshelf.app.core.model.Server
import com.stillshelf.app.core.model.SessionState
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeMenuViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onServerSelected_sameActiveServer_ignoresTap() = runTest(dispatcher) {
        val requestedServerIds = mutableListOf<String>()
        val repository = testSessionRepository(
            sessionState = MutableStateFlow(
                SessionState(
                    activeServerId = "server-local",
                    activeLibraryId = "library-local"
                )
            ),
            servers = MutableStateFlow(
                listOf(
                    testServer("server-local", "Local", "https://local.example"),
                    testServer("server-remote", "Remote", "https://remote.example")
                )
            ),
            setActiveServer = { serverId ->
                requestedServerIds += serverId
                AppResult.Success(Unit)
            }
        )
        val viewModel = HomeMenuViewModel(repository)

        advanceUntilIdle()
        viewModel.onServerSelected("server-local")
        advanceUntilIdle()

        assertTrue(requestedServerIds.isEmpty())
        assertFalse(viewModel.uiState.value.isSwitchingServer)
    }

    @Test
    fun onServerSelected_missingSavedSession_emitsNavigateToLogin() = runTest(dispatcher) {
        val repository = testSessionRepository(
            sessionState = MutableStateFlow(
                SessionState(
                    activeServerId = "server-local",
                    activeLibraryId = "library-local"
                )
            ),
            servers = MutableStateFlow(
                listOf(
                    testServer("server-local", "Local", "https://local.example"),
                    testServer("server-remote", "Remote", "https://remote.example")
                )
            ),
            setActiveServer = {
                AppResult.Error("No saved session for this server. Please log in again.")
            }
        )
        val viewModel = HomeMenuViewModel(repository)

        advanceUntilIdle()
        val event = async { viewModel.events.first() }
        viewModel.onServerSelected("server-remote")
        advanceUntilIdle()

        assertEquals(
            HomeMenuEvent.NavigateToLogin(
                serverName = "Remote",
                baseUrl = "https://remote.example"
            ),
            event.await()
        )
        assertFalse(viewModel.uiState.value.isSwitchingServer)
    }

    @Suppress("UNCHECKED_CAST")
    private fun testSessionRepository(
        sessionState: MutableStateFlow<SessionState>,
        servers: MutableStateFlow<List<Server>>,
        libraries: MutableStateFlow<List<Library>> = MutableStateFlow(emptyList()),
        setActiveServer: suspend (String) -> AppResult<Unit>
    ): SessionRepository {
        return Proxy.newProxyInstance(
            SessionRepository::class.java.classLoader,
            arrayOf(SessionRepository::class.java)
        ) { _, method, args ->
            when (method.name) {
                "observeSessionState" -> sessionState
                "observeServers" -> servers
                "observeLibrariesForActiveServer" -> libraries
                "setActiveServer" -> runBlocking { setActiveServer(args?.getOrNull(0) as String) }
                "observeBookProgressMutations" -> emptyFlow<BookProgressMutation>()
                "hashCode" -> System.identityHashCode(this)
                "equals" -> args?.firstOrNull() === this
                "toString" -> "TestSessionRepository"
                else -> throw UnsupportedOperationException("Unexpected SessionRepository call: ${method.name}")
            }
        } as SessionRepository
    }

    private fun testServer(id: String, name: String, baseUrl: String): Server {
        return Server(
            id = id,
            name = name,
            baseUrl = baseUrl,
            createdAt = 1L
        )
    }
}
