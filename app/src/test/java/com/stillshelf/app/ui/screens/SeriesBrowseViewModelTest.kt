package com.stillshelf.app.ui.screens

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BookDetail
import com.stillshelf.app.core.model.BookProgressMutation
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.Library
import com.stillshelf.app.core.model.NamedEntitySummary
import com.stillshelf.app.core.model.SeriesDetailEntry
import com.stillshelf.app.core.model.Server
import com.stillshelf.app.core.model.SessionState
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SERVER_LIBRARY_PAGE_SIZE
import com.stillshelf.app.data.repo.SessionRepository
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesBrowseViewModelTest {
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
    fun init_hidesNestedSubseriesWhenServerContentsAreEmptyButBookDetailsRecoverMatch() = runTest(dispatcher) {
        val parent = NamedEntitySummary(id = "parent", name = "Aesop's Fables", subtitle = "2 books")
        val child = NamedEntitySummary(id = "child", name = "Sub-Series", subtitle = "1 books")
        val bookOne = testBook(
            id = "book-1",
            title = "Volume 1",
            seriesName = "Aesop's Fables",
            seriesNames = listOf("Aesop's Fables"),
            seriesIds = listOf("parent")
        )
        val bookTwo = testBook(
            id = "book-2",
            title = "Volume 6 - The Old Hound",
            seriesName = "Aesop's Fables",
            seriesNames = listOf("Aesop's Fables"),
            seriesIds = listOf("parent")
        )
        val childDetail = bookTwo.copy(
            seriesName = "Sub-Series",
            seriesNames = listOf("Sub-Series", "Aesop's Fables"),
            seriesIds = listOf("child", "parent")
        )
        val repository = testSessionRepository(
            series = listOf(parent, child),
            booksByPage = mapOf(
                0 to listOf(bookOne, bookTwo),
                1 to emptyList()
            ),
            seriesContentsById = mapOf(
                "child" to emptyList()
            ),
            bookDetailsById = mapOf(
                "book-1" to bookOne,
                "book-2" to childDetail
            )
        )
        val sessionPreferences = testSessionPreferences(
            backgroundDirectory = createTempDirectory(prefix = "series-browse-prefs").toFile()
        )

        val viewModel = SeriesBrowseViewModel(
            sessionRepository = repository,
            sessionPreferences = sessionPreferences
        )
        advanceUntilIdle()

        assertEquals(listOf("Aesop's Fables"), viewModel.uiState.value.series.map { it.name })
    }

    @Test
    fun init_scansPastLegacySeriesBookPageCap() = runTest(dispatcher) {
        val parent = NamedEntitySummary(id = "parent", name = "Long Tail Series", subtitle = "1 books")
        val lateMatch = testBook(
            id = "book-101",
            title = "Volume 101",
            seriesName = "Long Tail Series",
            seriesNames = listOf("Long Tail Series"),
            seriesIds = listOf("parent")
        )
        val booksByPage = buildMap {
            repeat(100) { page ->
                put(
                    page,
                    List(SERVER_LIBRARY_PAGE_SIZE) { index ->
                        testBook(
                            id = "filler-$page-$index",
                            title = "Filler $page-$index"
                        )
                    }
                )
            }
            put(100, listOf(lateMatch))
            put(101, emptyList())
        }
        val repository = testSessionRepository(
            series = listOf(parent),
            booksByPage = booksByPage,
            seriesContentsById = emptyMap(),
            bookDetailsById = emptyMap()
        )
        val sessionPreferences = testSessionPreferences(
            backgroundDirectory = createTempDirectory(prefix = "series-browse-prefs").toFile()
        )

        val viewModel = SeriesBrowseViewModel(
            sessionRepository = repository,
            sessionPreferences = sessionPreferences
        )
        advanceUntilIdle()

        assertEquals(listOf("cover:book-101"), viewModel.uiState.value.series.single().coverUrls)
    }

    private fun testSessionPreferences(backgroundDirectory: File): SessionPreferences {
        backgroundDirectory.deleteOnExit()
        val preferencesFile = File(backgroundDirectory, "session.preferences_pb")
        return SessionPreferences(
            dataStore = PreferenceDataStoreFactory.create(
                produceFile = { preferencesFile }
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun testSessionRepository(
        series: List<NamedEntitySummary>,
        booksByPage: Map<Int, List<BookSummary>>,
        seriesContentsById: Map<String, List<SeriesDetailEntry>>,
        bookDetailsById: Map<String, BookSummary>
    ): SessionRepository {
        return Proxy.newProxyInstance(
            SessionRepository::class.java.classLoader,
            arrayOf(SessionRepository::class.java)
        ) { _, method, args ->
            when (method.name) {
                "fetchSeriesForActiveLibrary" -> AppResult.Success(series)
                "fetchBooksForActiveLibrary" -> {
                    val page = args?.getOrNull(1) as? Int ?: 0
                    AppResult.Success(booksByPage[page].orEmpty())
                }
                "fetchSeriesContentsForActiveLibrary" -> {
                    val seriesId = args?.getOrNull(0) as String
                    AppResult.Success(seriesContentsById[seriesId].orEmpty())
                }
                "fetchBookDetail" -> {
                    val bookId = args?.getOrNull(0) as String
                    val book = bookDetailsById.getValue(bookId)
                    AppResult.Success(
                        BookDetail(
                            book = book,
                            description = null,
                            publishedYear = null,
                            sizeBytes = null,
                            chapters = emptyList(),
                            bookmarks = emptyList()
                        )
                    )
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

    private fun testBook(
        id: String,
        title: String,
        seriesName: String? = null,
        seriesNames: List<String> = emptyList(),
        seriesIds: List<String> = emptyList()
    ) = BookSummary(
        id = id,
        libraryId = "library",
        title = title,
        authorName = "Author",
        narratorName = null,
        durationSeconds = 60.0,
        coverUrl = "cover:$id",
        seriesName = seriesName,
        seriesNames = seriesNames,
        seriesIds = seriesIds
    )
}
