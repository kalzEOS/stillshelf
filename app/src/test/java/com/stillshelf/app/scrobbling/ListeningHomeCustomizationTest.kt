package com.stillshelf.app.scrobbling

import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.datastore.SessionPreferenceState
import com.stillshelf.app.ui.screens.CustomizeViewModel
import com.stillshelf.app.ui.screens.HomeSectionIds
import com.stillshelf.app.ui.screens.navidrome.NavidromeCustomizeViewModel
import com.stillshelf.app.ui.screens.navidrome.NavidromeHomeSectionIds
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.After
import org.junit.Before

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ListeningHomeCustomizationTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun existingAbsOrderAndHiddenChoicesSurviveNewSectionAndReorderPersists() = runTest {
        val state = MutableStateFlow(SessionPreferenceState(activeServerId = null, activeLibraryId = null, homeSectionOrder = listOf(HomeSectionIds.DISCOVER, HomeSectionIds.CONTINUE),
            hiddenHomeSectionIds = setOf(HomeSectionIds.RECENT_SERIES)))
        val preferences = mockk<SessionPreferences> {
            every { this@mockk.state } returns state
            coEvery { setHomeSectionOrder(any()) } returns Unit
            coEvery { setHiddenHomeSectionIds(any()) } returns Unit
        }
        val vm = CustomizeViewModel(preferences)
        assertEquals(listOf(HomeSectionIds.DISCOVER, HomeSectionIds.CONTINUE), vm.uiState.value.personalizedSections.take(2).map { it.id })
        assertEquals(HomeSectionIds.LISTENING_STATISTICS, vm.uiState.value.personalizedSections.last().id)
        assertTrue(vm.uiState.value.hiddenPersonalizedSectionIds.contains(HomeSectionIds.RECENT_SERIES))
        vm.setHiddenPersonalizedSectionIds(setOf(HomeSectionIds.LISTENING_STATISTICS))
        coVerify { preferences.setHiddenHomeSectionIds(setOf(HomeSectionIds.LISTENING_STATISTICS)) }
    }

    @Test fun navidromeHasIndependentOrderAndListeningStatisticsVisibility() = runTest {
        val state = MutableStateFlow(SessionPreferenceState(activeServerId = null, activeLibraryId = null, navidromeHomeSectionOrder = listOf(NavidromeHomeSectionIds.ARTISTS, NavidromeHomeSectionIds.CONTINUE),
            navidromeHiddenHomeSectionIds = setOf(NavidromeHomeSectionIds.LISTENING_STATISTICS)))
        val preferences = mockk<SessionPreferences> { every { this@mockk.state } returns state }
        val vm = NavidromeCustomizeViewModel(preferences)
        assertEquals(listOf(NavidromeHomeSectionIds.ARTISTS, NavidromeHomeSectionIds.CONTINUE), vm.uiState.value.personalizedSections.take(2).map { it.id })
        assertEquals(1, vm.uiState.value.personalizedSections.count { it.id == NavidromeHomeSectionIds.LISTENING_STATISTICS })
        assertTrue(vm.uiState.value.hiddenPersonalizedSectionIds.contains(NavidromeHomeSectionIds.LISTENING_STATISTICS))
    }
}
