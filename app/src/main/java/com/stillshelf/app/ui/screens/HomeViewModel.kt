package com.stillshelf.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.core.model.SeriesStackSummary
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.core.util.hasMeaningfulStartedProgress
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.downloads.manager.BookDownloadManager
import com.stillshelf.app.downloads.manager.DownloadStatus
import com.stillshelf.app.ui.common.isResetToStart
import com.stillshelf.app.ui.common.withBookProgressMutation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.random.Random
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val sessionPreferences: SessionPreferences,
    private val bookDownloadManager: BookDownloadManager
) : ViewModel() {
    companion object {
        private const val HOME_FEED_CACHE_MAX_AGE_MS: Long = 15 * 60 * 1000L
        private const val HOME_FEED_CONTINUE_LIMIT = 10
        private const val HOME_FEED_RECENTLY_ADDED_LIMIT = 120
        private const val HOME_DISCOVER_LIMIT = 16
        private const val SILENT_REFRESH_INTERVAL_MS: Long = 5 * 60 * 1000L
        private const val SILENT_REFRESH_TICK_MS: Long = 30 * 1000L
    }

    private val mutableUiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()
    private val homeScreenVisible = MutableStateFlow(false)
    private val appInForeground = MutableStateFlow(true)
    private val activeLibraryIdState = MutableStateFlow<String?>(null)
    private var removedListenAgainBookIds: Set<String> = emptySet()
    private var isHomeFeedRefreshInFlight = false
    private var lastHomeFeedRefreshAtMs: Long = 0L
    private var homeVisibilityRefreshInFlight = false
    private val processLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            appInForeground.value = true
        }

        override fun onStop(owner: LifecycleOwner) {
            appInForeground.value = false
        }
    }

    private data class NormalizedHomeSections(
        val continueListening: List<ContinueListeningItem>,
        val recentlyAdded: List<BookSummary>,
        val listenAgain: List<BookSummary>,
        val recentSeries: List<SeriesStackSummary>,
        val discoverBooks: List<BookSummary>
    )

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
        observeActiveLibrary()
        observeBookProgressMutations()
        observeDownloadedState()
        observeSilentRefreshTicker()
    }

    private fun observeActiveLibrary() {
        viewModelScope.launch {
            var previousLibraryId: String? = null
            sessionRepository.observeSessionState()
                .map { it.activeLibraryId }
                .distinctUntilChanged()
                .collect { libraryId ->
                    if (libraryId != previousLibraryId) {
                        removedListenAgainBookIds = emptySet()
                        previousLibraryId = libraryId
                    }
                    activeLibraryIdState.value = libraryId
                    if (libraryId.isNullOrBlank()) {
                        mutableUiState.update { HomeUiState() }
                    } else {
                        loadCachedThenMaybeRefresh()
                    }
                }
        }
    }

    private fun observeBookProgressMutations() {
        viewModelScope.launch {
            sessionRepository.observeBookProgressMutations().collect { mutation ->
                mutableUiState.update { state ->
                    val updatedRecentlyAdded = state.recentlyAdded.map { it.withBookProgressMutation(mutation) }
                    val updatedDiscover = state.discoverBooks.map { it.withBookProgressMutation(mutation) }
                    val updatedRecentSeries = state.recentSeries.map { series ->
                        if (series.leadBook.id == mutation.bookId) {
                            series.copy(leadBook = series.leadBook.withBookProgressMutation(mutation))
                        } else {
                            series
                        }
                    }
                    val updatedContinue = when {
                        mutation.isFinished || mutation.isResetToStart() ->
                            state.continueListening.filterNot { item -> item.book.id == mutation.bookId }

                        else -> state.continueListening.map { it.withBookProgressMutation(mutation) }
                    }
                    val updatedListenAgain = if (mutation.isFinished) {
                        val sourceBook = updatedRecentlyAdded.firstOrNull { it.id == mutation.bookId }
                            ?: updatedDiscover.firstOrNull { it.id == mutation.bookId }
                            ?: updatedContinue.firstOrNull { it.book.id == mutation.bookId }?.book
                            ?: updatedRecentSeries.firstOrNull { it.leadBook.id == mutation.bookId }?.leadBook
                        if (sourceBook == null) {
                            state.listenAgain
                        } else {
                            (listOf(sourceBook) + state.listenAgain.filterNot { it.id == mutation.bookId })
                                .distinctBy { it.id }
                        }
                    } else {
                        state.listenAgain.filterNot { it.id == mutation.bookId }
                    }
                    state.copy(
                        continueListening = updatedContinue,
                        recentlyAdded = updatedRecentlyAdded,
                        discoverBooks = updatedDiscover,
                        recentSeries = updatedRecentSeries,
                        listenAgain = updatedListenAgain
                    )
                }
            }
        }
    }

    private suspend fun loadCachedThenMaybeRefresh() {
        val cachedResult = sessionRepository.fetchCachedHomeFeed(
            maxAgeMs = HOME_FEED_CACHE_MAX_AGE_MS
        )
        if (cachedResult is AppResult.Success && cachedResult.value != null) {
            val resolvedRecentSeries = resolveRecentSeries(
                backendRecentSeries = cachedResult.value.recentSeries,
                recentlyAdded = cachedResult.value.recentlyAdded
            )
            val normalizedSections = normalizeHomeSections(
                continueListening = cachedResult.value.continueListening,
                recentlyAdded = cachedResult.value.recentlyAdded,
                listenAgain = filterRemovedListenAgain(cachedResult.value.listenAgain),
                recentSeries = resolvedRecentSeries,
                discoverBooks = buildDiscoverBooks(cachedResult.value.recentlyAdded)
            )
            mutableUiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = null,
                    libraryName = cachedResult.value.libraryName,
                    continueListening = normalizedSections.continueListening,
                    recentlyAdded = normalizedSections.recentlyAdded,
                    listenAgain = normalizedSections.listenAgain,
                    recentSeries = normalizedSections.recentSeries,
                    discoverBooks = normalizedSections.discoverBooks,
                    authorImageUrls = cachedResult.value.authorImageUrls
                )
            }
            return
        }
        refreshNetwork(showLoading = true)
    }

    fun refresh() {
        viewModelScope.launch {
            val refreshed = refreshNetwork(showLoading = true)
            if (refreshed) {
                sessionPreferences.setLastLibrarySyncAtMs(System.currentTimeMillis())
            }
        }
    }

    fun addToCollection(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (val result = sessionRepository.addBookToDefaultCollection(bookId)) {
                is AppResult.Success -> mutableUiState.update { it.copy(actionMessage = "Added to Collections") }
                is AppResult.Error -> mutableUiState.update { it.copy(actionMessage = result.message) }
            }
        }
    }

    fun markAsFinished(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (val result = sessionRepository.markBookFinished(bookId, finished = true)) {
                is AppResult.Success -> {
                    removedListenAgainBookIds = removedListenAgainBookIds - bookId
                    applyBookFinishedState(bookId = bookId, finished = true)
                    mutableUiState.update { it.copy(actionMessage = "Marked as finished. Progress is now 100%.") }
                    refreshNetwork(showLoading = false)
                }
                is AppResult.Error -> mutableUiState.update { it.copy(actionMessage = result.message) }
            }
        }
    }

    fun markAsUnfinished(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (val result = sessionRepository.markBookFinished(bookId, finished = false)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Marked as unfinished.") }
                    removedListenAgainBookIds = removedListenAgainBookIds + bookId
                    applyBookFinishedState(bookId = bookId, finished = false)
                    refreshNetwork(showLoading = false)
                }
                is AppResult.Error -> mutableUiState.update { it.copy(actionMessage = result.message) }
            }
        }
    }

    fun resetBookProgress(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (
                val result = sessionRepository.markBookFinished(
                    bookId = bookId,
                    finished = false,
                    resetProgressWhenUnfinished = true
                )
            ) {
                is AppResult.Success -> {
                    removedListenAgainBookIds = removedListenAgainBookIds + bookId
                    applyBookResetState(bookId = bookId)
                    mutableUiState.update { it.copy(actionMessage = "Book progress reset.") }
                    refreshNetwork(showLoading = false)
                }

                is AppResult.Error -> mutableUiState.update { it.copy(actionMessage = result.message) }
            }
        }
    }

    fun removeFromContinueListening(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (
                val result = sessionRepository.markBookFinished(
                    bookId,
                    finished = false,
                    resetProgressWhenUnfinished = true
                )
            ) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            continueListening = it.continueListening.filterNot { item -> item.book.id == bookId },
                            actionMessage = "Removed from Continue Listening"
                        )
                    }
                }
                is AppResult.Error -> mutableUiState.update { it.copy(actionMessage = result.message) }
            }
        }
    }

    fun removeFromListenAgain(bookId: String) {
        if (bookId.isBlank()) return
        removedListenAgainBookIds = removedListenAgainBookIds + bookId
        mutableUiState.update { state ->
            state.copy(
                listenAgain = state.listenAgain.filterNot { it.id == bookId },
                actionMessage = "Removed from Listen Again"
            )
        }
    }

    fun toggleDownload(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            val target = findBookById(bookId)
            if (target == null) {
                mutableUiState.update { it.copy(actionMessage = "Unable to find book for download.") }
                return@launch
            }
            when (val result = bookDownloadManager.toggleDownload(target)) {
                is AppResult.Success -> mutableUiState.update { it.copy(actionMessage = result.value.message) }
                is AppResult.Error -> mutableUiState.update { it.copy(actionMessage = result.message) }
            }
        }
    }

    fun clearActionMessage() {
        mutableUiState.update { it.copy(actionMessage = null) }
    }

    fun onHomeScreenVisibilityChanged(isVisible: Boolean) {
        homeScreenVisible.value = isVisible
        if (isVisible) {
            viewModelScope.launch {
                refreshIfCacheMissing(showLoading = false)
            }
        }
    }

    private suspend fun refreshNetwork(showLoading: Boolean): Boolean {
        if (isHomeFeedRefreshInFlight) return false
        if (uiState.value.isLoading) return false
        isHomeFeedRefreshInFlight = true
        if (showLoading) {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        } else {
            mutableUiState.update { it.copy(errorMessage = null) }
        }

        var didRefresh = false
        try {
            when (
                val result = sessionRepository.fetchHomeFeed(
                    continueLimit = HOME_FEED_CONTINUE_LIMIT,
                    recentlyAddedLimit = HOME_FEED_RECENTLY_ADDED_LIMIT
                )
            ) {
                is AppResult.Success -> {
                    lastHomeFeedRefreshAtMs = System.currentTimeMillis()
                    didRefresh = true
                    val resolvedRecentSeries = resolveRecentSeries(
                        backendRecentSeries = result.value.recentSeries,
                        recentlyAdded = result.value.recentlyAdded
                    )
                    val normalizedSections = normalizeHomeSections(
                        continueListening = result.value.continueListening,
                        recentlyAdded = result.value.recentlyAdded,
                        listenAgain = filterRemovedListenAgain(result.value.listenAgain),
                        recentSeries = resolvedRecentSeries,
                        discoverBooks = buildDiscoverBooks(result.value.recentlyAdded)
                    )
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            libraryName = result.value.libraryName,
                            continueListening = normalizedSections.continueListening,
                            recentlyAdded = normalizedSections.recentlyAdded,
                            listenAgain = normalizedSections.listenAgain,
                            recentSeries = normalizedSections.recentSeries,
                            discoverBooks = normalizedSections.discoverBooks,
                            authorImageUrls = result.value.authorImageUrls
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = if (
                                state.recentlyAdded.isNotEmpty() ||
                                state.continueListening.isNotEmpty() ||
                                state.listenAgain.isNotEmpty()
                            ) {
                                null
                            } else {
                                result.message
                            }
                        )
                    }
                }
            }
        } finally {
            isHomeFeedRefreshInFlight = false
        }
        return didRefresh
    }

    private fun resolveRecentSeries(
        backendRecentSeries: List<SeriesStackSummary>,
        recentlyAdded: List<BookSummary>
    ): List<SeriesStackSummary> {
        if (backendRecentSeries.isNotEmpty()) return backendRecentSeries

        val grouped = linkedMapOf<String, MutableList<BookSummary>>()
        recentlyAdded.forEach { book ->
            val rawSeriesName = book.seriesName?.trim().orEmpty()
            if (rawSeriesName.isBlank()) return@forEach
            val key = normalizeSeriesKey(rawSeriesName)
            grouped.getOrPut(key) { mutableListOf() }.add(book)
        }

        return grouped.values
            .mapNotNull { books ->
                if (books.size <= 1) {
                    null
                } else {
                    val lead = books.first()
                    SeriesStackSummary(
                        seriesName = cleanSeriesName(lead.seriesName.orEmpty()),
                        leadBook = lead,
                        count = books.size
                    )
                }
            }
            .take(20)
    }

    private fun normalizeSeriesKey(value: String): String {
        return cleanSeriesName(value).lowercase()
    }

    private fun cleanSeriesName(value: String): String {
        return value
            .trim()
            .replace(Regex("\\s*#\\d+.*$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun observeDownloadedState() {
        viewModelScope.launch {
            bookDownloadManager.items.collect { items ->
                val downloadedIds = items
                    .filter { it.status == DownloadStatus.Completed }
                    .map { it.bookId }
                    .toSet()
                val progressByBookId = items
                    .filter { it.status == DownloadStatus.Queued || it.status == DownloadStatus.Downloading }
                    .associate { it.bookId to it.progressPercent.coerceIn(0, 100) }
                mutableUiState.update {
                    it.copy(
                        downloadedBookIds = downloadedIds,
                        downloadProgressByBookId = progressByBookId
                    )
                }
            }
        }
    }

    private fun findBookById(bookId: String): BookSummary? {
        val state = uiState.value
        return state.continueListening.firstOrNull { it.book.id == bookId }?.book
            ?: state.recentlyAdded.firstOrNull { it.id == bookId }
            ?: state.listenAgain.firstOrNull { it.id == bookId }
            ?: state.recentSeries.firstOrNull { it.leadBook.id == bookId }?.leadBook
    }

    private fun buildDiscoverBooks(recentlyAdded: List<BookSummary>): List<BookSummary> {
        val unique = recentlyAdded
            .asSequence()
            .filter { it.title.isNotBlank() }
            .distinctBy { "${it.title.trim().lowercase()}|${it.authorName.trim().lowercase()}" }
            .toList()
        if (unique.isEmpty()) return emptyList()
        return unique
            .shuffled(Random(System.currentTimeMillis()))
            .take(HOME_DISCOVER_LIMIT)
    }

    private fun observeSilentRefreshTicker() {
        viewModelScope.launch {
            combine(
                appInForeground,
                homeScreenVisible,
                activeLibraryIdState
            ) { foreground, homeVisible, libraryId ->
                foreground && !homeVisible && !libraryId.isNullOrBlank()
            }
                .distinctUntilChanged()
                .collectLatest { shouldRun ->
                    if (!shouldRun) return@collectLatest
                    while (true) {
                        val ageMs = System.currentTimeMillis() - lastHomeFeedRefreshAtMs
                        if (ageMs >= SILENT_REFRESH_INTERVAL_MS) {
                            refreshNetwork(showLoading = false)
                        }
                        delay(SILENT_REFRESH_TICK_MS)
                    }
                }
        }
    }

    private fun filterRemovedListenAgain(source: List<BookSummary>): List<BookSummary> {
        if (removedListenAgainBookIds.isEmpty()) return source
        return source.filterNot { removedListenAgainBookIds.contains(it.id) }
    }

    private suspend fun refreshIfCacheMissing(showLoading: Boolean) {
        if (homeVisibilityRefreshInFlight) return
        homeVisibilityRefreshInFlight = true
        try {
            val activeLibraryId = activeLibraryIdState.value
            if (activeLibraryId.isNullOrBlank()) return
            val cached = sessionRepository.fetchCachedHomeFeed(maxAgeMs = HOME_FEED_CACHE_MAX_AGE_MS)
            if (cached is AppResult.Success && cached.value != null) return
            refreshNetwork(showLoading = showLoading)
        } finally {
            homeVisibilityRefreshInFlight = false
        }
    }

    private fun applyBookFinishedState(bookId: String, finished: Boolean) {
        mutableUiState.update { state ->
            fun BookSummary.withFinished(updated: Boolean): BookSummary {
                return copy(
                    isFinished = updated,
                    progressPercent = if (updated) 1.0 else progressPercent?.takeIf { it < 0.995 }
                )
            }

            val updatedRecentlyAdded = state.recentlyAdded.map { book ->
                if (book.id == bookId) book.withFinished(finished) else book
            }
            val updatedDiscover = state.discoverBooks.map { book ->
                if (book.id == bookId) book.withFinished(finished) else book
            }
            val updatedContinue = state.continueListening.map { item ->
                if (item.book.id == bookId) {
                    item.copy(
                        book = item.book.withFinished(finished),
                        progressPercent = if (finished) 1.0 else item.progressPercent
                    )
                } else {
                    item
                }
            }
            val updatedRecentSeries = state.recentSeries.map { series ->
                if (series.leadBook.id == bookId) {
                    series.copy(leadBook = series.leadBook.withFinished(finished))
                } else {
                    series
                }
            }
            val updatedListenAgainBase = if (finished) {
                state.listenAgain
            } else {
                state.listenAgain.filterNot { it.id == bookId }
            }
            val updatedListenAgain = if (finished) {
                val sourceBook = updatedRecentlyAdded.firstOrNull { it.id == bookId }
                    ?: updatedDiscover.firstOrNull { it.id == bookId }
                    ?: updatedContinue.firstOrNull { it.book.id == bookId }?.book
                    ?: updatedRecentSeries.firstOrNull { it.leadBook.id == bookId }?.leadBook
                if (sourceBook == null) {
                    updatedListenAgainBase
                } else {
                    (listOf(sourceBook.withFinished(true)) + updatedListenAgainBase)
                        .distinctBy { it.id }
                }
            } else {
                updatedListenAgainBase
            }

            state.copy(
                continueListening = updatedContinue,
                recentlyAdded = updatedRecentlyAdded,
                discoverBooks = updatedDiscover,
                recentSeries = updatedRecentSeries,
                listenAgain = updatedListenAgain
            )
        }
    }

    private fun applyBookResetState(bookId: String) {
        mutableUiState.update { state ->
            fun BookSummary.withResetProgress(): BookSummary {
                return copy(
                    isFinished = false,
                    progressPercent = 0.0,
                    currentTimeSeconds = 0.0
                )
            }

            val updatedRecentlyAdded = state.recentlyAdded.map { book ->
                if (book.id == bookId) book.withResetProgress() else book
            }
            val updatedDiscover = state.discoverBooks.map { book ->
                if (book.id == bookId) book.withResetProgress() else book
            }
            val updatedRecentSeries = state.recentSeries.map { series ->
                if (series.leadBook.id == bookId) {
                    series.copy(leadBook = series.leadBook.withResetProgress())
                } else {
                    series
                }
            }

            state.copy(
                continueListening = state.continueListening.filterNot { item -> item.book.id == bookId },
                recentlyAdded = updatedRecentlyAdded,
                discoverBooks = updatedDiscover,
                recentSeries = updatedRecentSeries,
                listenAgain = state.listenAgain.filterNot { it.id == bookId }
            )
        }
    }

    private fun normalizeHomeSections(
        continueListening: List<ContinueListeningItem>,
        recentlyAdded: List<BookSummary>,
        listenAgain: List<BookSummary>,
        recentSeries: List<SeriesStackSummary>,
        discoverBooks: List<BookSummary>
    ): NormalizedHomeSections {
        val normalizedContinueInput = continueListening.filter { item ->
            hasMeaningfulStartedProgress(
                currentTimeSeconds = item.currentTimeSeconds ?: item.book.currentTimeSeconds,
                durationSeconds = item.book.durationSeconds,
                progressPercent = item.progressPercent ?: item.book.progressPercent,
                isFinished = item.book.isFinished
            )
        }
        val finishedIds = buildSet {
            normalizedContinueInput.forEach { item ->
                if (item.book.isFinished || (item.progressPercent ?: 0.0) >= 0.995) {
                    add(item.book.id)
                }
            }
            recentlyAdded.forEach { if (it.isFinished) add(it.id) }
            listenAgain.forEach { if (it.isFinished) add(it.id) }
            recentSeries.forEach { if (it.leadBook.isFinished) add(it.leadBook.id) }
            discoverBooks.forEach { if (it.isFinished) add(it.id) }
        }

        fun normalizeBook(book: BookSummary): BookSummary {
            if (!finishedIds.contains(book.id)) return book
            val mergedProgress = max(book.progressPercent ?: 0.0, 1.0)
            return book.copy(
                isFinished = true,
                progressPercent = mergedProgress
            )
        }

        val normalizedContinue = normalizedContinueInput.map { item ->
            val normalizedBook = normalizeBook(item.book)
            if (!finishedIds.contains(item.book.id)) {
                item
            } else {
                item.copy(
                    book = normalizedBook,
                    progressPercent = max(item.progressPercent ?: 0.0, 1.0)
                )
            }
        }

        return NormalizedHomeSections(
            continueListening = normalizedContinue,
            recentlyAdded = recentlyAdded.map(::normalizeBook),
            listenAgain = listenAgain.map(::normalizeBook),
            recentSeries = recentSeries.map { series ->
                series.copy(leadBook = normalizeBook(series.leadBook))
            },
            discoverBooks = discoverBooks.map(::normalizeBook)
        )
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processLifecycleObserver)
        super.onCleared()
    }
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val libraryName: String = "Library",
    val continueListening: List<ContinueListeningItem> = emptyList(),
    val recentlyAdded: List<BookSummary> = emptyList(),
    val listenAgain: List<BookSummary> = emptyList(),
    val recentSeries: List<SeriesStackSummary> = emptyList(),
    val discoverBooks: List<BookSummary> = emptyList(),
    val authorImageUrls: Map<String, String> = emptyMap(),
    val downloadedBookIds: Set<String> = emptySet(),
    val downloadProgressByBookId: Map<String, Int> = emptyMap(),
    val actionMessage: String? = null,
    val errorMessage: String? = null
)
