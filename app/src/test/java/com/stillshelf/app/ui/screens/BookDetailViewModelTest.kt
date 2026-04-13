package com.stillshelf.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import com.stillshelf.app.core.model.BookBookmark
import com.stillshelf.app.core.model.BookChapter
import com.stillshelf.app.core.model.BookDetail
import com.stillshelf.app.core.model.BookProgressMutation
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.Library
import com.stillshelf.app.core.model.PlaybackProgress
import com.stillshelf.app.core.model.Server
import com.stillshelf.app.core.model.SessionState
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.DetailRefreshPolicy
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.downloads.manager.BookDownloadManager
import com.stillshelf.app.downloads.manager.DownloadItem
import com.stillshelf.app.playback.controller.PlaybackController
import com.stillshelf.app.playback.controller.PlaybackUiState
import com.stillshelf.app.ui.navigation.DetailRoute
import io.mockk.every
import io.mockk.mockk
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
class BookDetailViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()

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
    fun init_withBlankBookId_setsErrorMessage() = runTest(dispatcher) {
        val viewModel = buildViewModel(bookId = "")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.errorMessage.isNullOrBlank())
    }

    @Test
    fun init_withValidBookId_startsLoadingThenClears() = runTest(dispatcher) {
        val viewModel = buildViewModel(bookId = "book1")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun init_withRefreshError_setsErrorMessage() = runTest(dispatcher) {
        val viewModel = buildViewModel(
            bookId = "book1",
            refreshResult = AppResult.Error("Network error")
        )
        advanceUntilIdle()

        assertEquals("Network error", viewModel.uiState.value.errorMessage)
    }

    // endregion

    // region tab selection

    @Test
    fun setSelectedTab_updatesState() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.setSelectedTab("Chapters")

        assertEquals("Chapters", viewModel.uiState.value.selectedTab)
    }

    // endregion

    // region addToCollection

    @Test
    fun addToCollection_onSuccess_setsActionMessage() = runTest(dispatcher) {
        val viewModel = buildViewModel(
            addToCollectionResult = AppResult.Success("Collections")
        )
        advanceUntilIdle()

        viewModel.addToCollection()
        advanceUntilIdle()

        assertEquals("Added to Collections", viewModel.uiState.value.actionMessage)
    }

    @Test
    fun addToCollection_onError_setsActionMessage() = runTest(dispatcher) {
        val viewModel = buildViewModel(
            addToCollectionResult = AppResult.Error("Not found")
        )
        advanceUntilIdle()

        viewModel.addToCollection()
        advanceUntilIdle()

        assertEquals("Not found", viewModel.uiState.value.actionMessage)
    }

    // endregion

    // region markAsUnfinished / resetBookProgress

    @Test
    fun markAsUnfinished_onSuccess_setsActionMessage() = runTest(dispatcher) {
        // refreshBookDetail fails so it doesn't clear the actionMessage set by markAsUnfinished
        val viewModel = buildViewModel(
            markFinishedResult = AppResult.Success(PlaybackProgress(null, null, null)),
            refreshResult = AppResult.Error("offline")
        )
        advanceUntilIdle()

        viewModel.markAsUnfinished()
        advanceUntilIdle()

        assertEquals("Marked as unfinished.", viewModel.uiState.value.actionMessage)
    }

    @Test
    fun markAsUnfinished_onError_setsActionMessage() = runTest(dispatcher) {
        val viewModel = buildViewModel(
            markFinishedResult = AppResult.Error("Server error")
        )
        advanceUntilIdle()

        viewModel.markAsUnfinished()
        advanceUntilIdle()

        assertEquals("Server error", viewModel.uiState.value.actionMessage)
    }

    @Test
    fun resetBookProgress_onSuccess_setsActionMessageAndResetsProgress() = runTest(dispatcher) {
        // refreshBookDetail fails so it doesn't clear the actionMessage set by resetBookProgress
        val viewModel = buildViewModel(
            markFinishedResult = AppResult.Success(PlaybackProgress(0.0, 0.0, null)),
            refreshResult = AppResult.Error("offline")
        )
        advanceUntilIdle()

        viewModel.resetBookProgress()
        advanceUntilIdle()

        assertEquals("Book progress reset.", viewModel.uiState.value.actionMessage)
        assertEquals(0.0, viewModel.uiState.value.progressPercent)
    }

    // endregion

    // region bookmark editing

    @Test
    fun editBookmark_withBlankTitle_setsError() = runTest(dispatcher) {
        val detail = testDetail()
        val viewModel = buildViewModel(observedDetail = detail)
        advanceUntilIdle()

        viewModel.editBookmark(testBookmark(), "  ")

        assertEquals("Bookmark title can't be empty.", viewModel.uiState.value.actionMessage)
    }

    @Test
    fun editBookmark_onSuccess_setsActionMessage() = runTest(dispatcher) {
        val detail = testDetail(bookmarks = listOf(testBookmark()))
        // refreshBookDetail fails so it doesn't clear the actionMessage
        val viewModel = buildViewModel(
            observedDetail = detail,
            updateBookmarkResult = AppResult.Success(Unit),
            refreshResult = AppResult.Error("offline")
        )
        advanceUntilIdle()

        viewModel.editBookmark(testBookmark(), "New Title")
        advanceUntilIdle()

        assertEquals("Bookmark updated.", viewModel.uiState.value.actionMessage)
    }

    @Test
    fun deleteBookmark_onSuccess_setsActionMessage() = runTest(dispatcher) {
        val bm = testBookmark()
        val detail = testDetail(bookmarks = listOf(bm))
        // refreshBookDetail fails so it doesn't clear the actionMessage
        val viewModel = buildViewModel(
            observedDetail = detail,
            deleteBookmarkResult = AppResult.Success(Unit),
            refreshResult = AppResult.Error("offline")
        )
        advanceUntilIdle()

        viewModel.deleteBookmark(bm)
        advanceUntilIdle()

        assertEquals("Bookmark deleted.", viewModel.uiState.value.actionMessage)
    }

    // endregion

    // region clearActionMessage

    @Test
    fun clearActionMessage_resetsToNull() = runTest(dispatcher) {
        val viewModel = buildViewModel(addToCollectionResult = AppResult.Success("Collections"))
        advanceUntilIdle()

        viewModel.addToCollection()
        advanceUntilIdle()

        viewModel.clearActionMessage()

        assertNull(viewModel.uiState.value.actionMessage)
    }

    // endregion

    // region helpers

    @Suppress("UNCHECKED_CAST")
    private fun buildViewModel(
        bookId: String = "book1",
        refreshResult: AppResult<Unit> = AppResult.Success(Unit),
        observedDetail: BookDetail? = null,
        addToCollectionResult: AppResult<String> = AppResult.Success("Collections"),
        markFinishedResult: AppResult<PlaybackProgress> = AppResult.Success(PlaybackProgress(null, null, null)),
        updateBookmarkResult: AppResult<Unit> = AppResult.Success(Unit),
        deleteBookmarkResult: AppResult<Unit> = AppResult.Success(Unit)
    ): BookDetailViewModel {
        val repo = Proxy.newProxyInstance(
            SessionRepository::class.java.classLoader,
            arrayOf(SessionRepository::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "observeBookProgressMutations" -> emptyFlow<BookProgressMutation>()
                "observeSessionState" -> emptyFlow<SessionState>()
                "observeServers" -> emptyFlow<List<Server>>()
                "observeLibrariesForActiveServer" -> emptyFlow<List<Library>>()
                "observeBookDetail" -> flowOf(observedDetail)
                "refreshBookDetail" -> refreshResult
                "fetchPlaybackProgress" -> AppResult.Success(null as PlaybackProgress?)
                "addBookToDefaultCollection" -> addToCollectionResult
                "markBookFinished" -> markFinishedResult
                "syncPlaybackProgress" -> AppResult.Success(Unit)
                "updateBookmark" -> updateBookmarkResult
                "deleteBookmark" -> deleteBookmarkResult
                "hashCode" -> System.identityHashCode(this)
                "equals" -> false
                "toString" -> "TestRepo"
                else -> throw UnsupportedOperationException("Unexpected: ${method.name}")
            }
        } as SessionRepository

        val playbackController = mockk<PlaybackController>(relaxed = true) {
            every { uiState } returns MutableStateFlow(PlaybackUiState())
        }

        val downloadManager = mockk<BookDownloadManager>(relaxed = true) {
            every { activeItems } returns emptyFlow<List<DownloadItem>>()
            every { getCompletedDownload(any()) } returns null
        }

        val savedStateHandle = SavedStateHandle(
            if (bookId.isNotEmpty()) mapOf(DetailRoute.BOOK_ID_ARG to bookId) else emptyMap()
        )

        return BookDetailViewModel(
            savedStateHandle = savedStateHandle,
            sessionRepository = repo,
            playbackController = playbackController,
            bookDownloadManager = downloadManager
        )
    }

    private fun testBook(id: String = "book1") = BookSummary(
        id = id,
        libraryId = "lib1",
        title = "Test Book",
        authorName = "Author",
        narratorName = null,
        durationSeconds = 3600.0,
        coverUrl = null
    )

    private fun testDetail(
        bookmarks: List<BookBookmark> = emptyList()
    ) = BookDetail(
        book = testBook(),
        description = null,
        publishedYear = null,
        sizeBytes = null,
        chapters = emptyList<BookChapter>(),
        bookmarks = bookmarks
    )

    private fun testBookmark() = BookBookmark(
        id = "bm1",
        libraryItemId = "book1",
        title = "My Bookmark",
        timeSeconds = 120.0
    )

    // endregion
}
