package com.stillshelf.app.ui.screens

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BookBookmark
import com.stillshelf.app.core.model.BookDetail
import com.stillshelf.app.core.model.BookProgressMutation
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.core.model.Library
import com.stillshelf.app.core.model.PlaybackProgress
import com.stillshelf.app.core.model.Server
import com.stillshelf.app.core.model.SessionState
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.downloads.manager.BookDownloadManager
import com.stillshelf.app.downloads.manager.DownloadItem
import com.stillshelf.app.playback.controller.PlaybackController
import com.stillshelf.app.playback.controller.PlaybackUiState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.lang.reflect.Proxy
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region onPlayPauseClick

    @Test
    fun onPlayPauseClick_withActiveBook_togglesPlayPause() = runTest(dispatcher) {
        val uiState = MutableStateFlow(PlaybackUiState(book = testBook()))
        val controller = testPlaybackController(uiStateFlow = uiState)
        val viewModel = buildViewModel(controller = controller)
        advanceUntilIdle()

        viewModel.onPlayPauseClick()

        verify(exactly = 1) { controller.togglePlayPause() }
    }

    @Test
    fun onPlayPauseClick_withNoBookAndNoPreview_doesNotTogglePlay() = runTest(dispatcher) {
        val controller = testPlaybackController()
        val viewModel = buildViewModel(controller = controller)
        advanceUntilIdle()

        viewModel.onPlayPauseClick()

        verify(exactly = 0) { controller.togglePlayPause() }
    }

    @Test
    fun onPlayPauseClick_withPreviewItem_startsPlayback() = runTest(dispatcher) {
        val previewItem = ContinueListeningItem(
            book = testBook(id = "preview-book"),
            progressPercent = 0.3,
            currentTimeSeconds = 180.0
        )
        val controller = testPlaybackController()
        val viewModel = buildViewModel(
            controller = controller,
            miniPlayerItem = previewItem
        )
        advanceUntilIdle()

        viewModel.onPlayPauseClick()

        verify(exactly = 1) { controller.playBook("preview-book", any()) }
    }

    // endregion

    // region skip controls

    @Test
    fun onRewindClick_seeksBackward() = runTest(dispatcher) {
        val controller = testPlaybackController()
        val viewModel = buildViewModel(controller = controller)
        advanceUntilIdle()

        viewModel.onRewindClick()

        // Verify seekBy was called (default prefs produce a negative delta)
        verify(exactly = 1) { controller.seekBy(deltaMs = any()) }
    }

    @Test
    fun onForwardClick_seeksForward() = runTest(dispatcher) {
        val controller = testPlaybackController()
        val viewModel = buildViewModel(controller = controller)
        advanceUntilIdle()

        viewModel.onForwardClick()

        verify(exactly = 1) { controller.seekBy(deltaMs = any()) }
    }

    // endregion

    // region sleep timer

    @Test
    fun startSleepTimerMinutes_withActiveBook_delegatesToController() = runTest(dispatcher) {
        val controller = testPlaybackController(
            uiStateFlow = MutableStateFlow(PlaybackUiState(book = testBook()))
        )
        val viewModel = buildViewModel(controller = controller)
        advanceUntilIdle()

        viewModel.startSleepTimerMinutes(30)

        verify(exactly = 1) { controller.startSleepTimerMinutes(30) }
    }

    @Test
    fun startSleepTimerMinutes_clampsMinimumToOne() = runTest(dispatcher) {
        val controller = testPlaybackController(
            uiStateFlow = MutableStateFlow(PlaybackUiState(book = testBook()))
        )
        val viewModel = buildViewModel(controller = controller)
        advanceUntilIdle()

        viewModel.startSleepTimerMinutes(0)

        verify(exactly = 1) { controller.startSleepTimerMinutes(1) }
    }

    @Test
    fun startSleepTimerMinutes_withNoBookAndNoPreview_setsActionMessage() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.startSleepTimerMinutes(30)
        advanceUntilIdle()

        assertEquals("Start playback to use the timer.", viewModel.actionMessage.value)
    }

    // endregion

    // region bookmarks

    @Test
    fun editBookmark_withBlankTitle_setsErrorMessage() = runTest(dispatcher) {
        val controller = testPlaybackController(
            uiStateFlow = MutableStateFlow(PlaybackUiState(book = testBook()))
        )
        val viewModel = buildViewModel(controller = controller)
        advanceUntilIdle()

        val bookmark = BookBookmark(id = "bm1", libraryItemId = "book1", title = "Original", timeSeconds = 60.0)
        viewModel.editBookmark(bookmark, "   ")
        advanceUntilIdle()

        assertEquals("Bookmark title can't be empty.", viewModel.actionMessage.value)
    }

    @Test
    fun editBookmark_withNoActiveBook_setsErrorMessage() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        val bookmark = BookBookmark(id = "bm1", libraryItemId = "book1", title = "Note", timeSeconds = 60.0)
        viewModel.editBookmark(bookmark, "New Title")
        advanceUntilIdle()

        assertEquals("Unable to edit bookmark right now.", viewModel.actionMessage.value)
    }

    // endregion

    // region clearActionMessage

    @Test
    fun clearActionMessage_resetsToNull() = runTest(dispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()
        // Trigger a message by calling sleep timer with no book
        viewModel.startSleepTimerMinutes(30)
        advanceUntilIdle()

        viewModel.clearActionMessage()

        assertNull(viewModel.actionMessage.value)
    }

    // endregion

    // region helpers

    private fun testPlaybackController(
        uiStateFlow: StateFlow<PlaybackUiState> = MutableStateFlow(PlaybackUiState())
    ): PlaybackController {
        return mockk(relaxed = true) {
            every { uiState } returns uiStateFlow
            every { getCachedContinueListeningItem() } returns null
        }
    }

    private fun testSessionPreferences(dir: File): SessionPreferences {
        dir.deleteOnExit()
        return SessionPreferences(
            dataStore = PreferenceDataStoreFactory.create(
                produceFile = { File(dir, "player_test.preferences_pb") }
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun testSessionRepository(
        miniPlayerItem: ContinueListeningItem? = null
    ): SessionRepository = Proxy.newProxyInstance(
        SessionRepository::class.java.classLoader,
        arrayOf(SessionRepository::class.java)
    ) { _, method, args ->
        when (method.name) {
            "observeBookProgressMutations" -> emptyFlow<BookProgressMutation>()
            "observeSessionState" -> emptyFlow<SessionState>()
            "observeRealtimeInvalidations" -> emptyFlow<Any>()
            "observeServerConnectionMessages" -> emptyFlow<String>()
            "observeActiveServerConnectionStatus" -> emptyFlow<Any>()
            "observeActiveServerDataState" -> emptyFlow<Any>()
            "observeServers" -> emptyFlow<List<Server>>()
            "observeLibrariesForActiveServer" -> emptyFlow<List<Library>>()
            "fetchMiniPlayerItem" -> AppResult.Success(miniPlayerItem)
            "fetchBookDetail" -> {
                val bookId = args?.getOrNull(0) as? String ?: "book"
                AppResult.Success(
                    BookDetail(
                        book = testBook(id = bookId),
                        description = null,
                        publishedYear = null,
                        sizeBytes = null,
                        chapters = emptyList(),
                        bookmarks = emptyList()
                    )
                )
            }
            "fetchPlaybackProgress" -> AppResult.Success<PlaybackProgress?>(null)
            "syncPlaybackProgress" -> AppResult.Success(Unit)
            "markBookFinished" -> AppResult.Success(PlaybackProgress(null, null, null))
            "createBookmark" -> AppResult.Success(Unit)
            "updateBookmark" -> AppResult.Success(Unit)
            "deleteBookmark" -> AppResult.Success(Unit)
            "hashCode" -> System.identityHashCode(this)
            "equals" -> args?.firstOrNull() === this
            "toString" -> "TestSessionRepository"
            else -> throw UnsupportedOperationException("Unexpected call: ${method.name}")
        }
    } as SessionRepository

    private fun testDownloadManager(): BookDownloadManager = mockk(relaxed = true) {
        every { activeItems } returns emptyFlow<List<DownloadItem>>()
    }

    private fun buildViewModel(
        controller: PlaybackController = testPlaybackController(),
        miniPlayerItem: ContinueListeningItem? = null,
        bookIdArg: String? = null
    ): PlayerViewModel {
        val prefsDir = createTempDirectory(prefix = "player-vm-test").toFile()
        return PlayerViewModel(
            savedStateHandle = SavedStateHandle(
                buildMap {
                    if (bookIdArg != null) put("bookId", bookIdArg)
                }
            ),
            playbackController = controller,
            sessionRepository = testSessionRepository(miniPlayerItem),
            sessionPreferences = testSessionPreferences(prefsDir),
            bookDownloadManager = testDownloadManager()
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

    // endregion
}
