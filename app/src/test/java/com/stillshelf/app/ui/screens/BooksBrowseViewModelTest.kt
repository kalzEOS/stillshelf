package com.stillshelf.app.ui.screens

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BookProgressMutation
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.Library
import com.stillshelf.app.core.model.PlaybackProgress
import com.stillshelf.app.core.model.Server
import com.stillshelf.app.core.model.SessionState
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.domain.usecase.BookProgressAction
import com.stillshelf.app.domain.usecase.BookProgressActionCoordinator
import com.stillshelf.app.domain.usecase.BookProgressActionResult
import com.stillshelf.app.downloads.manager.BookDownloadManager
import com.stillshelf.app.downloads.manager.DownloadItem
import com.stillshelf.app.playback.controller.PlaybackController
import com.stillshelf.app.playback.controller.PlaybackUiState
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.lang.reflect.Proxy
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BooksBrowseViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher(TestCoroutineScheduler())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region initialization

    @Test
    fun init_withEmptyLibrary_setsIsBootstrappingFalseAndEmptyBooks() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isBootstrapping)
        assertFalse(state.isLoading)
        assertTrue(state.books.isEmpty())
        assertNull(state.errorMessage)
    }

    @Test
    fun init_withBooks_populatesBooksList() = runTest(dispatcher) {
        val books = listOf(testBook("b1"), testBook("b2"))
        val viewModel = buildViewModel(booksOnFirstPage = books)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.books.size)
    }

    @Test
    fun init_withFetchError_setsErrorMessage() = runTest(dispatcher) {
        val viewModel = buildViewModel(fetchError = "Network error")
        advanceUntilIdle()

        assertEquals("Network error", viewModel.uiState.value.errorMessage)
    }

    // endregion

    // region UI preference setters

    @Test
    fun setLayoutMode_updatesUiState() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.setLayoutMode(BooksLayoutMode.List)

        assertEquals(BooksLayoutMode.List, viewModel.uiState.value.layoutMode)
    }

    @Test
    fun setStatusFilter_updatesUiState() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.setStatusFilter(BooksStatusFilter.InProgress)

        assertEquals(BooksStatusFilter.InProgress, viewModel.uiState.value.statusFilter)
    }

    @Test
    fun setSortKey_updatesUiState() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.setSortKey(BooksSortKey.Author)

        assertEquals(BooksSortKey.Author, viewModel.uiState.value.sortKey)
    }

    @Test
    fun toggleCollapseSeries_flipsCollapseSeries() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()
        val initial = viewModel.uiState.value.collapseSeries

        viewModel.toggleCollapseSeries()

        assertEquals(!initial, viewModel.uiState.value.collapseSeries)
    }

    // endregion

    // region book actions

    @Test
    fun addToCollection_onSuccess_setsActionMessage() = runTest(dispatcher) {
        val viewModel = buildViewModel(addToCollectionResult = AppResult.Success("Collections"))
        advanceUntilIdle()

        viewModel.addToCollection("book1")
        advanceUntilIdle()

        assertEquals("Added to Collections", viewModel.uiState.value.actionMessage)
    }

    @Test
    fun addToCollection_onError_setsErrorMessage() = runTest(dispatcher) {
        val viewModel = buildViewModel(addToCollectionResult = AppResult.Error("Failed"))
        advanceUntilIdle()

        viewModel.addToCollection("book1")
        advanceUntilIdle()

        assertEquals("Failed", viewModel.uiState.value.actionMessage)
    }

    @Test
    fun addToCollection_withBlankId_doesNothing() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.addToCollection("  ")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.actionMessage)
    }

    @Test
    fun markAsFinished_onSuccess_setsActionMessage() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.markAsFinished("book1")
        advanceUntilIdle()

        assertEquals(BookProgressAction.MarkFinished.defaultMessage, viewModel.uiState.value.actionMessage)
    }

    @Test
    fun markAsUnfinished_onError_setsErrorMessage() = runTest(dispatcher) {
        val viewModel = buildViewModel(markFinishedError = "Server unreachable")
        advanceUntilIdle()

        viewModel.markAsUnfinished("book1")
        advanceUntilIdle()

        assertEquals("Server unreachable", viewModel.uiState.value.actionMessage)
    }

    @Test
    fun resetBookProgress_onSuccess_setsActionMessage() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.resetBookProgress("book1")
        advanceUntilIdle()

        assertEquals(BookProgressAction.ResetProgress.defaultMessage, viewModel.uiState.value.actionMessage)
    }

    // endregion

    // region clearActionMessage

    @Test
    fun clearActionMessage_resetsToNull() = runTest(dispatcher) {
        val viewModel = buildViewModel(addToCollectionResult = AppResult.Success("OK"))
        advanceUntilIdle()
        viewModel.addToCollection("book1")
        advanceUntilIdle()

        viewModel.clearActionMessage()

        assertNull(viewModel.uiState.value.actionMessage)
    }

    // endregion

    // region helpers

    private fun testSessionPreferences(dir: File): SessionPreferences {
        dir.deleteOnExit()
        return SessionPreferences(
            dataStore = PreferenceDataStoreFactory.create(
                produceFile = { File(dir, "browse_test.preferences_pb") }
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun testSessionRepository(
        books: List<BookSummary> = emptyList(),
        fetchError: String? = null,
        addToCollectionResult: AppResult<String> = AppResult.Success("Collections")
    ): SessionRepository = Proxy.newProxyInstance(
        SessionRepository::class.java.classLoader,
        arrayOf(SessionRepository::class.java)
    ) { _, method, args ->
        when (method.name) {
            "observeBookProgressMutations" -> emptyFlow<BookProgressMutation>()
            "observeSessionState" -> flowOf(SessionState(activeServerId = "srv1", activeLibraryId = "lib1"))
            "observeRealtimeInvalidations" -> emptyFlow<Any>()
            "observeServerConnectionMessages" -> emptyFlow<String>()
            "observeActiveServerConnectionStatus" -> emptyFlow<Any>()
            "observeActiveServerDataState" -> emptyFlow<Any>()
            "observeServers" -> emptyFlow<List<Server>>()
            "observeLibrariesForActiveServer" -> emptyFlow<List<Library>>()
            "fetchBooksForActiveLibrary" -> if (fetchError != null) {
                AppResult.Error(fetchError)
            } else {
                AppResult.Success(books)
            }
            "fetchAllBooksForActiveLibrary" -> AppResult.Success(books)
            "addBookToDefaultCollection" -> addToCollectionResult
            "markBookFinished" -> AppResult.Success(PlaybackProgress(null, null, null))
            "hashCode" -> System.identityHashCode(this)
            "equals" -> args?.firstOrNull() === this
            "toString" -> "TestSessionRepository"
            else -> throw UnsupportedOperationException("Unexpected call: ${method.name}")
        }
    } as SessionRepository

    private fun testPlaybackController(): PlaybackController = mockk(relaxed = true) {
        every { uiState } returns MutableStateFlow(PlaybackUiState())
    }

    private fun testDownloadManager(): BookDownloadManager = mockk(relaxed = true) {
        every { activeItems } returns emptyFlow<List<DownloadItem>>()
    }

    private fun testProgressCoordinator(
        markFinishedError: String? = null
    ): BookProgressActionCoordinator = BookProgressActionCoordinator(
        markBookFinished = { _, finished, _ ->
            if (markFinishedError != null) {
                AppResult.Error(markFinishedError)
            } else {
                AppResult.Success(PlaybackProgress(progressPercent = if (finished) 1.0 else null, null, null))
            }
        },
        reconcilePlaybackProgress = { _, _, _ -> }
    )

    private fun buildViewModel(
        booksOnFirstPage: List<BookSummary> = emptyList(),
        fetchError: String? = null,
        addToCollectionResult: AppResult<String> = AppResult.Success("Collections"),
        markFinishedError: String? = null
    ): BooksBrowseViewModel {
        val prefsDir = createTempDirectory(prefix = "browse-vm-test").toFile()
        return BooksBrowseViewModel(
            sessionRepository = testSessionRepository(
                books = booksOnFirstPage,
                fetchError = fetchError,
                addToCollectionResult = addToCollectionResult
            ),
            sessionPreferences = testSessionPreferences(prefsDir),
            bookDownloadManager = testDownloadManager(),
            playbackController = testPlaybackController(),
            bookProgressActionCoordinator = testProgressCoordinator(markFinishedError)
        )
    }

    private fun testBook(id: String = "book1") = BookSummary(
        id = id,
        libraryId = "lib1",
        title = "Book $id",
        authorName = "Author",
        narratorName = null,
        durationSeconds = 3600.0,
        coverUrl = null
    )

    // endregion
}
