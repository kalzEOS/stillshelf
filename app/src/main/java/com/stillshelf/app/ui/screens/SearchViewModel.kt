package com.stillshelf.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.NamedEntitySummary
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val isLoading: Boolean = false,
    val query: String = "",
    val books: List<BookSummary> = emptyList(),
    val authors: List<NamedEntitySummary> = emptyList(),
    val series: List<NamedEntitySummary> = emptyList(),
    val narrators: List<NamedEntitySummary> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = mutableUiState.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            sessionRepository.observeSessionState()
                .map { it.activeLibraryId }
                .distinctUntilChanged()
                .collect {
                    if (uiState.value.query.isNotBlank()) {
                        search(uiState.value.query, debounceMs = 0L)
                    }
                }
        }
    }

    fun onQueryChange(value: String) {
        mutableUiState.update { it.copy(query = value, errorMessage = null) }
        if (value.isBlank()) {
            searchJob?.cancel()
            mutableUiState.update {
                it.copy(
                    isLoading = false,
                    books = emptyList(),
                    authors = emptyList(),
                    series = emptyList(),
                    narrators = emptyList()
                )
            }
            return
        }
        search(value)
    }

    fun clearQuery() {
        searchJob?.cancel()
        mutableUiState.update {
            it.copy(
                query = "",
                isLoading = false,
                books = emptyList(),
                authors = emptyList(),
                series = emptyList(),
                narrators = emptyList(),
                errorMessage = null
            )
        }
    }

    private fun search(query: String, debounceMs: Long = 220L) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            val currentQuery = uiState.value.query.trim()
            if (currentQuery.isBlank()) return@launch

            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = sessionRepository.searchActiveLibrary(currentQuery)) {
                is AppResult.Success -> {
                    val rankedBooks = rankBooks(currentQuery, result.value.books)
                    val rankedAuthors = rankEntities(currentQuery, result.value.authors)
                    val rankedSeries = rankEntities(currentQuery, result.value.series)
                    val rankedNarrators = rankEntities(currentQuery, result.value.narrators)
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            books = rankedBooks,
                            authors = rankedAuthors,
                            series = rankedSeries,
                            narrators = rankedNarrators
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            books = emptyList(),
                            authors = emptyList(),
                            series = emptyList(),
                            narrators = emptyList(),
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    private fun rankBooks(
        query: String,
        books: List<BookSummary>
    ): List<BookSummary> {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()

        val ranked = books.mapNotNull { book ->
            val score = listOfNotNull(
                matchScore(queryTokens, book.title),
                matchScore(queryTokens, book.authorName)?.plus(1),
                matchScore(queryTokens, book.seriesName).let { score ->
                    if (score == null) null else score + 1
                }
            ).minOrNull() ?: return@mapNotNull null

            book to score
        }
        if (ranked.isEmpty()) return emptyList()

        val bestScore = ranked.minOf { it.second }
        return ranked
            .asSequence()
            .filter { it.second == bestScore }
            .sortedBy { it.first.title.lowercase() }
            .map { it.first }
            .take(20)
            .toList()
    }

    private fun rankEntities(
        query: String,
        entities: List<NamedEntitySummary>
    ): List<NamedEntitySummary> {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()

        val ranked = entities.mapNotNull { entity ->
            val score = matchScore(queryTokens, entity.name) ?: return@mapNotNull null
            entity to score
        }
        if (ranked.isEmpty()) return emptyList()

        val bestScore = ranked.minOf { it.second }
        return ranked
            .asSequence()
            .filter { it.second == bestScore }
            .sortedBy { it.first.name.lowercase() }
            .map { it.first }
            .take(20)
            .toList()
    }

    private fun matchScore(
        queryTokens: List<String>,
        text: String?
    ): Int? {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return null

        if (tokens == queryTokens) return 0

        if (hasTokenSequence(tokens, queryTokens, allowLastPrefix = false)) {
            return 1
        }

        if (hasTokenSequence(tokens, queryTokens, allowLastPrefix = true)) {
            return 2
        }

        if (queryTokens.all { token -> tokens.contains(token) }) {
            return 3
        }

        val lastQueryToken = queryTokens.lastOrNull().orEmpty()
        if (
            lastQueryToken.isNotBlank() &&
            queryTokens.dropLast(1).all { token -> tokens.contains(token) } &&
            tokens.any { candidate -> candidate.startsWith(lastQueryToken) }
        ) {
            return 4
        }

        return null
    }

    private fun hasTokenSequence(
        tokens: List<String>,
        queryTokens: List<String>,
        allowLastPrefix: Boolean
    ): Boolean {
        if (queryTokens.isEmpty() || queryTokens.size > tokens.size) return false
        val lastIndex = queryTokens.lastIndex
        for (start in 0..(tokens.size - queryTokens.size)) {
            var matched = true
            for (index in queryTokens.indices) {
                val queryToken = queryTokens[index]
                val token = tokens[start + index]
                val isLast = index == lastIndex
                val isMatch = if (allowLastPrefix && isLast) {
                    token.startsWith(queryToken)
                } else {
                    token == queryToken
                }
                if (!isMatch) {
                    matched = false
                    break
                }
            }
            if (matched) return true
        }
        return false
    }

    private fun tokenize(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return value
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }
    }
}
