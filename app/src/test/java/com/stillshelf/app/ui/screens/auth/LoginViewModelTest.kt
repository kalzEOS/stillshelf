package com.stillshelf.app.ui.screens.auth

import androidx.lifecycle.SavedStateHandle
import com.stillshelf.app.core.datastore.SecureStorageUnavailableException
import com.stillshelf.app.core.model.BookProgressMutation
import com.stillshelf.app.core.model.Library
import com.stillshelf.app.core.model.Server
import com.stillshelf.app.core.model.SessionState
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.LoginPersistenceMode
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.ui.navigation.AuthRoute
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
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
class LoginViewModelTest {
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
    fun login_showsTokenStoragePromptWhenSecurePersistenceIsUnavailable() = runTest(dispatcher) {
        val requestedModes = mutableListOf<LoginPersistenceMode>()
        val repository = testSessionRepository(requestedModes) { mode ->
            if (mode == LoginPersistenceMode.PersistentSecureOnly) {
                AppResult.Error(
                    message = "Secure token storage is unavailable on this device.",
                    cause = SecureStorageUnavailableException()
                )
            } else {
                AppResult.Success(Unit)
            }
        }
        val viewModel = LoginViewModel(
            savedStateHandle = testSavedStateHandle(),
            sessionRepository = repository
        )

        viewModel.onUsernameChange("demo")
        viewModel.onPasswordChange("password")
        viewModel.onLoginClick()
        advanceUntilIdle()

        assertEquals(listOf(LoginPersistenceMode.PersistentSecureOnly), requestedModes)
        assertTrue(viewModel.uiState.value.showTokenStorageFallbackPrompt)
        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun login_sessionOnlyRetryUsesSessionOnlyModeAndSucceeds() = runTest(dispatcher) {
        val requestedModes = mutableListOf<LoginPersistenceMode>()
        val repository = testSessionRepository(requestedModes) { mode ->
            if (mode == LoginPersistenceMode.PersistentSecureOnly) {
                AppResult.Error(
                    message = "Secure token storage is unavailable on this device.",
                    cause = SecureStorageUnavailableException()
                )
            } else {
                AppResult.Success(Unit)
            }
        }
        val viewModel = LoginViewModel(
            savedStateHandle = testSavedStateHandle(),
            sessionRepository = repository
        )

        viewModel.onUsernameChange("demo")
        viewModel.onPasswordChange("password")
        viewModel.onLoginClick()
        advanceUntilIdle()

        viewModel.onContinueSessionOnlyClick()
        advanceUntilIdle()

        assertEquals(
            listOf(
                LoginPersistenceMode.PersistentSecureOnly,
                LoginPersistenceMode.SessionOnly
            ),
            requestedModes
        )
        assertFalse(viewModel.uiState.value.showTokenStorageFallbackPrompt)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Suppress("UNCHECKED_CAST")
    private fun testSessionRepository(
        requestedModes: MutableList<LoginPersistenceMode>,
        addServerAndLogin: (LoginPersistenceMode) -> AppResult<Unit>
    ): SessionRepository {
        return Proxy.newProxyInstance(
            SessionRepository::class.java.classLoader,
            arrayOf(SessionRepository::class.java)
        ) { _, method, args ->
            when (method.name) {
                "addServerAndLogin" -> {
                    val mode = args?.getOrNull(4) as? LoginPersistenceMode
                        ?: LoginPersistenceMode.PersistentSecureOnly
                    requestedModes += mode
                    addServerAndLogin(mode)
                }
                "observeSessionState" -> emptyFlow<SessionState>()
                "observeBookProgressMutations" -> emptyFlow<BookProgressMutation>()
                "observeServers" -> emptyFlow<List<Server>>()
                "observeLibrariesForActiveServer" -> emptyFlow<List<Library>>()
                "hashCode" -> System.identityHashCode(this)
                "equals" -> args?.firstOrNull() === this
                "toString" -> "TestSessionRepository"
                else -> throw UnsupportedOperationException("Unexpected SessionRepository call: ${method.name}")
            }
        } as SessionRepository
    }

    private fun testSavedStateHandle(): SavedStateHandle {
        return SavedStateHandle(
            mapOf(
                AuthRoute.SERVER_NAME_ARG to "Demo Server",
                AuthRoute.BASE_URL_ARG to "http://demo"
            )
        )
    }
}
