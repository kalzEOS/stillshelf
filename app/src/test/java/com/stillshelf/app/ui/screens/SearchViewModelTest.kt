package com.stillshelf.app.ui.screens

import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.datastore.SessionPreferenceState
import com.stillshelf.app.core.model.BookProgressMutation
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.Library
import com.stillshelf.app.core.model.NamedEntitySummary
import com.stillshelf.app.core.model.SearchResults
import com.stillshelf.app.core.model.Server
import com.stillshelf.app.core.model.SessionState
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.domain.usecase.BookProgressActionCoordinator
import com.stillshelf.app.downloads.manager.BookDownloadManager
import com.stillshelf.app.downloads.manager.DownloadItem
import com.stillshelf.app.playback.controller.PlaybackController
import com.stillshelf.app.playback.controller.PlaybackUiState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class SearchViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region query changes

    @Test
    fun onQueryChange_withBlankQuery_clearsResults() = runTest(dispatcher) {
        val viewModel = buildViewModel(searchResult = AppResult.Success(emptyResults()))
        viewModel.onQueryChange("Dune")
        advanceUntilIdle()
        viewModel.onQueryChange("")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.books.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("", viewModel.uiState.value.query)
    }

    @Test
    fun onQueryChange_withResults_populatesBooks() = runTest(dispatcher) {
        // Ranking returns only best-scoring matches; "Dune" (exact token) scores better than
        // "Dune Messiah" (2-token title). We just assert at least one result is returned.
        val books = listOf(testBook("b1", "Dune"), testBook("b2", "Dune Messiah"))
        val viewModel = buildViewModel(
            searchResult = AppResult.Success(emptyResults(books = books))
        )

        viewModel.onQueryChange("Dune")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.books.isNotEmpty())
        assertEquals("Dune", viewModel.uiState.value.books.first().title)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun onQueryChange_onError_setsErrorMessage() = runTest(dispatcher) {
        val viewModel = buildViewModel(searchResult = AppResult.Error("No connection"))

        viewModel.onQueryChange("anything")
        advanceUntilIdle()

        assertEquals("No connection", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // endregion

    // region clearQuery

    @Test
    fun clearQuery_resetsAllState() = runTest(dispatcher) {
        val viewModel = buildViewModel(
            searchResult = AppResult.Success(emptyResults(books = listOf(testBook("b1", "Dune"))))
        )
        viewModel.onQueryChange("Dune")
        advanceUntilIdle()

        viewModel.clearQuery()

        assertEquals("", viewModel.uiState.value.query)
        assertTrue(viewModel.uiState.value.books.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // endregion

    // region useRecentSearchTerm

    @Test
    fun useRecentSearchTerm_withBlankTerm_doesNothing() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        viewModel.useRecentSearchTerm("  ")
        advanceUntilIdle()
        assertEquals("", viewModel.uiState.value.query)
    }

    @Test
    fun useRecentSearchTerm_setsQueryAndSearches() = runTest(dispatcher) {
        val foundationBooks = listOf(testBook("b1", "Foundation"))
        val viewModel = buildViewModel(
            searchResult = AppResult.Success(emptyResults(books = foundationBooks))
        )

        viewModel.useRecentSearchTerm("Foundation")
        advanceUntilIdle()

        assertEquals("Foundation", viewModel.uiState.value.query)
        assertEquals(1, viewModel.uiState.value.books.size)
    }

    // endregion

    // region clearActionMessage

    @Test
    fun clearActionMessage_resetsToNull() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.clearActionMessage()

        assertNull(viewModel.uiState.value.actionMessage)
    }

    // endregion

    // region helpers

    @Suppress("UNCHECKED_CAST")
    private fun buildViewModel(
        searchResult: AppResult<SearchResults> = AppResult.Success(emptyResults()),
        recentSearchTerms: List<String> = emptyList()
    ): SearchViewModel {
        val repo = Proxy.newProxyInstance(
            SessionRepository::class.java.classLoader,
            arrayOf(SessionRepository::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "observeSessionState" -> flowOf(SessionState(activeServerId = null, activeLibraryId = null))
                "observeBookProgressMutations" -> emptyFlow<BookProgressMutation>()
                "observeServers" -> emptyFlow<List<Server>>()
                "observeLibrariesForActiveServer" -> emptyFlow<List<Library>>()
                "searchActiveLibrary" -> searchResult
                "hashCode" -> System.identityHashCode(this)
                "equals" -> false
                "toString" -> "TestRepo"
                else -> throw UnsupportedOperationException("Unexpected: ${method.name}")
            }
        } as SessionRepository

        val sessionPreferences = mockk<SessionPreferences>(relaxed = true) {
            every { state } returns flowOf(
                SessionPreferenceState(
                    activeServerId = null,
                    activeLibraryId = null,
                    recentSearchTerms = recentSearchTerms
                )
            )
            coEvery { addRecentSearchTerm(any()) } just runs
            coEvery { clearRecentSearchTerms() } just runs
        }

        val downloadManager = mockk<BookDownloadManager>(relaxed = true) {
            every { activeItems } returns emptyFlow<List<DownloadItem>>()
        }

        val playbackController = mockk<PlaybackController>(relaxed = true) {
            every { uiState } returns MutableStateFlow(PlaybackUiState())
        }

        val progressCoordinator = BookProgressActionCoordinator(
            markBookFinished = { _, _, _ -> AppResult.Success(com.stillshelf.app.core.model.PlaybackProgress(null, null, null)) },
            reconcilePlaybackProgress = { _, _, _ -> }
        )

        return SearchViewModel(
            sessionRepository = repo,
            sessionPreferences = sessionPreferences,
            bookDownloadManager = downloadManager,
            playbackController = playbackController,
            bookProgressActionCoordinator = progressCoordinator
        )
    }

    private fun emptyResults(
        books: List<BookSummary> = emptyList(),
        authors: List<NamedEntitySummary> = emptyList(),
        series: List<NamedEntitySummary> = emptyList(),
        narrators: List<NamedEntitySummary> = emptyList()
    ) = SearchResults(books = books, authors = authors, series = series, narrators = narrators)

    private fun testBook(id: String, title: String) = BookSummary(
        id = id,
        libraryId = "lib1",
        title = title,
        authorName = "Author",
        narratorName = null,
        durationSeconds = 3600.0,
        coverUrl = null
    )

    // endregion
}
