package com.stillshelf.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SessionPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ToggleSectionItem(
    val id: String,
    val title: String
)

data class CustomizeUiState(
    val listSections: List<ToggleSectionItem> = emptyList(),
    val personalizedSections: List<ToggleSectionItem> = emptyList(),
    val hiddenListSectionIds: Set<String> = emptySet(),
    val hiddenPersonalizedSectionIds: Set<String> = emptySet()
)

object ListSectionIds {
    const val BOOKS = "books"
    const val AUTHORS = "authors"
    const val NARRATORS = "narrators"
    const val SERIES = "series"
    const val COLLECTIONS = "collections"
    const val GENRES = "genres"
    const val BOOKMARKS = "bookmarks"
    const val PLAYLISTS = "playlists"
    const val DOWNLOADED = "downloaded"
}

object HomeSectionIds {
    const val CONTINUE = "continue_listening"
    const val LISTEN_AGAIN = "listen_again"
    const val RECENTLY_ADDED = "recently_added"
    const val RECENT_SERIES = "recent_series"
    const val DISCOVER = "discover"
    const val NEWEST_AUTHORS = "newest_authors"
}

@HiltViewModel
class CustomizeViewModel @Inject constructor(
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    private val defaultListSections = listOf(
        ToggleSectionItem(ListSectionIds.BOOKS, "Books"),
        ToggleSectionItem(ListSectionIds.AUTHORS, "Authors"),
        ToggleSectionItem(ListSectionIds.NARRATORS, "Narrators"),
        ToggleSectionItem(ListSectionIds.SERIES, "Series"),
        ToggleSectionItem(ListSectionIds.COLLECTIONS, "Collections"),
        ToggleSectionItem(ListSectionIds.GENRES, "Genres"),
        ToggleSectionItem(ListSectionIds.BOOKMARKS, "Bookmarks"),
        ToggleSectionItem(ListSectionIds.PLAYLISTS, "Playlists"),
        ToggleSectionItem(ListSectionIds.DOWNLOADED, "Downloaded")
    )
    private val defaultPersonalizedSections = listOf(
        ToggleSectionItem(HomeSectionIds.CONTINUE, "Continue Listening"),
        ToggleSectionItem(HomeSectionIds.LISTEN_AGAIN, "Listen Again"),
        ToggleSectionItem(HomeSectionIds.RECENTLY_ADDED, "Recently Added"),
        ToggleSectionItem(HomeSectionIds.RECENT_SERIES, "Recent Series"),
        ToggleSectionItem(HomeSectionIds.DISCOVER, "Discover"),
        ToggleSectionItem(HomeSectionIds.NEWEST_AUTHORS, "Newest Authors")
    )

    private val mutableUiState = MutableStateFlow(
        CustomizeUiState(
            listSections = defaultListSections,
            personalizedSections = defaultPersonalizedSections
        )
    )
    val uiState: StateFlow<CustomizeUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionPreferences.state.collect { state ->
                mutableUiState.update {
                    it.copy(
                        listSections = applySavedOrder(defaultListSections, state.browseSectionOrder),
                        personalizedSections = applySavedOrder(defaultPersonalizedSections, state.homeSectionOrder),
                        hiddenListSectionIds = state.hiddenBrowseSectionIds,
                        hiddenPersonalizedSectionIds = state.hiddenHomeSectionIds
                    )
                }
            }
        }
    }

    fun toggleListSection(id: String) {
        val hidden = uiState.value.hiddenListSectionIds.toMutableSet()
        if (!hidden.add(id)) hidden.remove(id)
        viewModelScope.launch {
            sessionPreferences.setHiddenBrowseSectionIds(hidden)
        }
    }

    fun togglePersonalizedSection(id: String) {
        val hidden = uiState.value.hiddenPersonalizedSectionIds.toMutableSet()
        if (!hidden.add(id)) hidden.remove(id)
        viewModelScope.launch {
            sessionPreferences.setHiddenHomeSectionIds(hidden)
        }
    }

    fun setHiddenListSectionIds(ids: Set<String>) {
        viewModelScope.launch {
            sessionPreferences.setHiddenBrowseSectionIds(ids)
        }
    }

    fun setHiddenPersonalizedSectionIds(ids: Set<String>) {
        viewModelScope.launch {
            sessionPreferences.setHiddenHomeSectionIds(ids)
        }
    }

    fun moveListSection(id: String) {
        val current = uiState.value.listSections
        val updated = moveDown(current, id)
        viewModelScope.launch {
            sessionPreferences.setBrowseSectionOrder(updated.map { it.id })
        }
    }

    fun movePersonalizedSection(id: String) {
        val current = uiState.value.personalizedSections
        val updated = moveDown(current, id)
        viewModelScope.launch {
            sessionPreferences.setHomeSectionOrder(updated.map { it.id })
        }
    }

    fun setListOrder(ids: List<String>) {
        viewModelScope.launch {
            sessionPreferences.setBrowseSectionOrder(ids)
        }
    }

    fun setPersonalizedOrder(ids: List<String>) {
        viewModelScope.launch {
            sessionPreferences.setHomeSectionOrder(ids)
        }
    }

    private fun applySavedOrder(
        defaults: List<ToggleSectionItem>,
        savedOrder: List<String>
    ): List<ToggleSectionItem> {
        if (savedOrder.isEmpty()) return defaults
        val byId = defaults.associateBy { it.id }
        val ordered = buildList {
            savedOrder.forEach { id ->
                val item = byId[id] ?: return@forEach
                add(item)
            }
            defaults.forEach { item ->
                if (none { it.id == item.id }) {
                    add(item)
                }
            }
        }
        return ordered
    }

    private fun moveDown(
        current: List<ToggleSectionItem>,
        id: String
    ): List<ToggleSectionItem> {
        val index = current.indexOfFirst { it.id == id }
        if (index == -1 || current.size <= 1) return current
        val mutable = current.toMutableList()
        val target = if (index == mutable.lastIndex) 0 else index + 1
        val item = mutable.removeAt(index)
        mutable.add(target, item)
        return mutable
    }
}
