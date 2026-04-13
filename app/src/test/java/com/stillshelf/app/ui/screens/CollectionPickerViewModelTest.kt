package com.stillshelf.app.ui.screens

import com.stillshelf.app.core.model.BookProgressMutation
import com.stillshelf.app.core.model.Library
import com.stillshelf.app.core.model.NamedEntitySummary
import com.stillshelf.app.core.model.Server
import com.stillshelf.app.core.model.SessionState
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
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
class CollectionPickerViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region loadDestinations

    @Test
    fun loadDestinations_onSuccess_populatesCollectionsAndPlaylists() = runTest(dispatcher) {
        val collections = listOf(testEntity("c1", "Favorites"))
        val playlists = listOf(testEntity("p1", "Road Trip"))
        val viewModel = buildViewModel(
            collectionsResult = AppResult.Success(collections),
            playlistsResult = AppResult.Success(playlists)
        )

        viewModel.loadDestinations()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.collections.size)
        assertEquals("Favorites", viewModel.uiState.value.collections.first().name)
        assertEquals(1, viewModel.uiState.value.playlists.size)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun loadDestinations_onPartialError_setsErrorMessage() = runTest(dispatcher) {
        val viewModel = buildViewModel(
            collectionsResult = AppResult.Success(emptyList()),
            playlistsResult = AppResult.Error("Playlists unavailable")
        )

        viewModel.loadDestinations()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.errorMessage.isNullOrBlank())
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("Playlists"))
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // endregion

    // region addBookToExistingCollection

    @Test
    fun addBookToExistingCollection_withBlankBookId_doesNothing() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        viewModel.addBookToExistingCollection("", "c1")
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.actionMessage)
    }

    @Test
    fun addBookToExistingCollection_withBlankCollectionId_doesNothing() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        viewModel.addBookToExistingCollection("book1", "")
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.actionMessage)
    }

    @Test
    fun addBookToExistingCollection_onSuccess_setsActionMessage() = runTest(dispatcher) {
        val collections = listOf(testEntity("c1", "Favorites"))
        val viewModel = buildViewModel(
            collectionsResult = AppResult.Success(collections),
            playlistsResult = AppResult.Success(emptyList()),
            addToCollectionResult = AppResult.Success(Unit)
        )
        viewModel.loadDestinations()
        advanceUntilIdle()

        viewModel.addBookToExistingCollection("book1", "c1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.actionMessage?.contains("Favorites") == true)
    }

    @Test
    fun addBookToExistingCollection_onError_setsErrorMessage() = runTest(dispatcher) {
        val viewModel = buildViewModel(
            collectionsResult = AppResult.Success(emptyList()),
            playlistsResult = AppResult.Success(emptyList()),
            addToCollectionResult = AppResult.Error("Server error")
        )

        viewModel.addBookToExistingCollection("book1", "c1")
        advanceUntilIdle()

        assertEquals("Server error", viewModel.uiState.value.errorMessage)
    }

    // endregion

    // region createCollectionAndAddBook

    @Test
    fun createCollectionAndAddBook_withBlankName_setsErrorMessage() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        viewModel.createCollectionAndAddBook("book1", "  ")
        assertEquals("Collection name is required.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun createCollectionAndAddBook_withBlankBookId_doesNothing() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        viewModel.createCollectionAndAddBook("", "My List")
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.actionMessage)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun createCollectionAndAddBook_onSuccess_setsActionMessage() = runTest(dispatcher) {
        val created = testEntity("c2", "Road Trip Reads")
        val viewModel = buildViewModel(
            createCollectionResult = AppResult.Success(created),
            addToCollectionResult = AppResult.Success(Unit),
            collectionsResult = AppResult.Success(emptyList()),
            playlistsResult = AppResult.Success(emptyList())
        )

        viewModel.createCollectionAndAddBook("book1", "Road Trip Reads")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.actionMessage?.contains("Road Trip Reads") == true)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    // endregion

    // region clearMessages

    @Test
    fun clearMessages_resetsActionAndError() = runTest(dispatcher) {
        val viewModel = buildViewModel(
            collectionsResult = AppResult.Success(emptyList()),
            playlistsResult = AppResult.Success(emptyList()),
            addToCollectionResult = AppResult.Success(Unit)
        )
        viewModel.addBookToExistingCollection("book1", "c1")
        advanceUntilIdle()

        viewModel.clearMessages()

        assertNull(viewModel.uiState.value.actionMessage)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // endregion

    // region helpers

    @Suppress("UNCHECKED_CAST")
    private fun buildViewModel(
        collectionsResult: AppResult<List<NamedEntitySummary>> = AppResult.Success(emptyList()),
        playlistsResult: AppResult<List<NamedEntitySummary>> = AppResult.Success(emptyList()),
        addToCollectionResult: AppResult<Unit> = AppResult.Success(Unit),
        addToPlaylistResult: AppResult<Unit> = AppResult.Success(Unit),
        createCollectionResult: AppResult<NamedEntitySummary> = AppResult.Success(testEntity("c1", "New")),
        createPlaylistResult: AppResult<NamedEntitySummary> = AppResult.Success(testEntity("p1", "New")),
        ): CollectionPickerViewModel {
        val repo = Proxy.newProxyInstance(
            SessionRepository::class.java.classLoader,
            arrayOf(SessionRepository::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "observeSessionState" -> emptyFlow<SessionState>()
                "observeBookProgressMutations" -> emptyFlow<BookProgressMutation>()
                "observeServers" -> emptyFlow<List<Server>>()
                "observeLibrariesForActiveServer" -> emptyFlow<List<Library>>()
                "fetchCollectionsForActiveLibrary" -> collectionsResult
                "fetchPlaylistsForActiveLibrary" -> playlistsResult
                "addBookToCollection" -> addToCollectionResult
                "addBookToPlaylist" -> addToPlaylistResult
                "createCollection" -> createCollectionResult
                "createPlaylist" -> createPlaylistResult
                "createCollectionWithBook" -> createCollectionResult
                "createPlaylistWithBook" -> createPlaylistResult
                "hashCode" -> System.identityHashCode(this)
                "equals" -> false
                "toString" -> "TestRepo"
                else -> throw UnsupportedOperationException("Unexpected: ${method.name}")
            }
        } as SessionRepository
        return CollectionPickerViewModel(repo)
    }

    private fun testEntity(id: String, name: String) = NamedEntitySummary(id = id, name = name)

    // endregion
}
