package com.stillshelf.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetailCacheDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: DetailCacheDao

    private val serverId = "srv1"
    private val libraryId = "lib1"
    private val bookId = "book1"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.detailCacheDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // region BookSummary

    @Test
    fun upsertBookSummary_and_getBookSummary_roundtrips() = runTest {
        val entity = testBookSummary()
        dao.upsertBookSummary(entity)
        assertEquals(entity, dao.getBookSummary(serverId, libraryId, bookId))
    }

    @Test
    fun getBookSummary_nonExistent_returnsNull() = runTest {
        assertNull(dao.getBookSummary(serverId, libraryId, "missing"))
    }

    @Test
    fun upsertBookSummary_replacesExistingOnConflict() = runTest {
        dao.upsertBookSummary(testBookSummary(title = "Old Title"))
        dao.upsertBookSummary(testBookSummary(title = "New Title"))
        assertEquals("New Title", dao.getBookSummary(serverId, libraryId, bookId)?.title)
    }

    @Test
    fun upsertBookSummaries_insertsBatch() = runTest {
        val entities = listOf(
            testBookSummary(id = "b1", title = "First"),
            testBookSummary(id = "b2", title = "Second"),
            testBookSummary(id = "b3", title = "Third")
        )
        dao.upsertBookSummaries(entities)
        assertEquals(3, dao.getBookSummariesForLibrary(serverId, libraryId).size)
    }

    @Test
    fun getBookSummariesForLibrary_returnsSortedByTitle() = runTest {
        dao.upsertBookSummaries(
            listOf(
                testBookSummary(id = "b1", title = "Zebra"),
                testBookSummary(id = "b2", title = "Apple"),
                testBookSummary(id = "b3", title = "Mango")
            )
        )
        val result = dao.getBookSummariesForLibrary(serverId, libraryId)
        assertEquals("Apple", result[0].title)
        assertEquals("Mango", result[1].title)
        assertEquals("Zebra", result[2].title)
    }

    @Test
    fun observeBookSummary_emitsCurrentValue() = runTest {
        val entity = testBookSummary()
        dao.upsertBookSummary(entity)
        val result = dao.observeBookSummary(serverId, libraryId, bookId).first()
        assertEquals(entity, result)
    }

    @Test
    fun observeBookSummary_missingBook_emitsNull() = runTest {
        val result = dao.observeBookSummary(serverId, libraryId, "missing").first()
        assertNull(result)
    }

    @Test
    fun deleteBookSummariesForServer_removesAllForServer() = runTest {
        dao.upsertBookSummaries(
            listOf(
                testBookSummary(id = "b1"),
                testBookSummary(id = "b2")
            )
        )
        dao.deleteBookSummariesForServer(serverId)
        assertTrue(dao.getBookSummariesForLibrary(serverId, libraryId).isEmpty())
    }

    // endregion

    // region BookDetail

    @Test
    fun upsertBookDetail_and_getBookDetail_roundtrips() = runTest {
        val entity = testBookDetail()
        dao.upsertBookDetail(entity)
        assertEquals(entity, dao.getBookDetail(serverId, libraryId, bookId))
    }

    @Test
    fun getBookDetail_nonExistent_returnsNull() = runTest {
        assertNull(dao.getBookDetail(serverId, libraryId, "missing"))
    }

    @Test
    fun observeBookDetail_emitsCurrentValue() = runTest {
        val entity = testBookDetail()
        dao.upsertBookDetail(entity)
        val result = dao.observeBookDetail(serverId, libraryId, bookId).first()
        assertEquals(entity, result)
    }

    // endregion

    // region BookChapters

    @Test
    fun insertBookChapters_and_getBookChapters_roundtrips() = runTest {
        val chapters = listOf(
            testBookChapter(index = 0, title = "Intro"),
            testBookChapter(index = 1, title = "Chapter 1"),
            testBookChapter(index = 2, title = "Chapter 2")
        )
        dao.insertBookChapters(chapters)
        val result = dao.getBookChapters(serverId, libraryId, bookId)
        assertEquals(3, result.size)
        assertEquals("Intro", result[0].title)
        assertEquals("Chapter 1", result[1].title)
    }

    @Test
    fun getBookChapters_returnsSortedByIndex() = runTest {
        dao.insertBookChapters(
            listOf(
                testBookChapter(index = 2, title = "Last"),
                testBookChapter(index = 0, title = "First"),
                testBookChapter(index = 1, title = "Middle")
            )
        )
        val result = dao.getBookChapters(serverId, libraryId, bookId)
        assertEquals("First", result[0].title)
        assertEquals("Middle", result[1].title)
        assertEquals("Last", result[2].title)
    }

    @Test
    fun deleteBookChapters_removesChaptersForBook() = runTest {
        dao.insertBookChapters(listOf(testBookChapter(index = 0, title = "Ch 1")))
        dao.deleteBookChapters(serverId, libraryId, bookId)
        assertTrue(dao.getBookChapters(serverId, libraryId, bookId).isEmpty())
    }

    @Test
    fun observeBookChapters_emitsCurrentChapters() = runTest {
        dao.insertBookChapters(listOf(testBookChapter(index = 0, title = "Intro")))
        val result = dao.observeBookChapters(serverId, libraryId, bookId).first()
        assertEquals(1, result.size)
        assertEquals("Intro", result[0].title)
    }

    // endregion

    // region BookBookmarks

    @Test
    fun insertBookBookmarks_and_getBookBookmarks_roundtrips() = runTest {
        val bookmarks = listOf(
            testBookBookmark(id = "bm1", title = "Favourite Part", timeSeconds = 120.0),
            testBookBookmark(id = "bm2", title = "Cliffhanger", timeSeconds = 300.0)
        )
        dao.insertBookBookmarks(bookmarks)
        val result = dao.getBookBookmarks(serverId, libraryId, bookId)
        assertEquals(2, result.size)
    }

    @Test
    fun getBookBookmarks_ordersByTimeAscending() = runTest {
        dao.insertBookBookmarks(
            listOf(
                testBookBookmark("bm2", "Later", timeSeconds = 600.0),
                testBookBookmark("bm1", "Earlier", timeSeconds = 60.0)
            )
        )
        val result = dao.getBookBookmarks(serverId, libraryId, bookId)
        assertEquals(60.0, result[0].timeSeconds)
        assertEquals(600.0, result[1].timeSeconds)
    }

    @Test
    fun deleteBookBookmarks_removesBookmarksForBook() = runTest {
        dao.insertBookBookmarks(listOf(testBookBookmark("bm1", "Note", 100.0)))
        dao.deleteBookBookmarks(serverId, libraryId, bookId)
        assertTrue(dao.getBookBookmarks(serverId, libraryId, bookId).isEmpty())
    }

    @Test
    fun observeBookBookmarks_emitsCurrentBookmarks() = runTest {
        dao.insertBookBookmarks(listOf(testBookBookmark("bm1", "Note", 60.0)))
        val result = dao.observeBookBookmarks(serverId, libraryId, bookId).first()
        assertEquals(1, result.size)
        assertEquals("bm1", result[0].id)
    }

    // endregion

    // region DetailSyncState

    @Test
    fun upsertDetailSyncState_and_getDetailSyncState_roundtrips() = runTest {
        val entity = DetailSyncStateEntity(
            serverId = serverId,
            libraryId = libraryId,
            resourceType = "book_detail",
            resourceId = bookId,
            resourceVariant = "default",
            lastSuccessfulSyncAtMs = 1_000_000L,
            lastAttemptedSyncAtMs = 1_000_000L
        )
        dao.upsertDetailSyncState(entity)
        val result = dao.getDetailSyncState(serverId, libraryId, "book_detail", bookId, "default")
        assertNotNull(result)
        assertEquals(1_000_000L, result?.lastSuccessfulSyncAtMs)
    }

    @Test
    fun getDetailSyncState_nonExistent_returnsNull() = runTest {
        assertNull(dao.getDetailSyncState(serverId, libraryId, "book_detail", "no-book", "default"))
    }

    // endregion

    // region Helpers

    private fun testBookSummary(
        id: String = bookId,
        title: String = "Test Book"
    ) = BookSummaryEntity(
        serverId = serverId,
        libraryId = libraryId,
        id = id,
        title = title,
        authorName = "Author",
        narratorName = null,
        durationSeconds = 3600.0,
        coverUrl = null,
        seriesName = null,
        seriesNamesJson = "[]",
        seriesIdsJson = "[]",
        seriesSequence = null,
        genresJson = "[]",
        publishedYear = null,
        addedAtMs = null,
        progressPercent = null,
        currentTimeSeconds = null,
        isFinished = false,
        updatedAtMs = 1_700_000_000L
    )

    private fun testBookDetail() = BookDetailEntity(
        serverId = serverId,
        libraryId = libraryId,
        bookId = bookId,
        description = "A great book.",
        publishedYear = "2021",
        sizeBytes = 104857600L,
        updatedAtMs = 1_700_000_000L
    )

    private fun testBookChapter(index: Int, title: String) = BookChapterEntity(
        serverId = serverId,
        libraryId = libraryId,
        bookId = bookId,
        chapterIndex = index,
        title = title,
        startSeconds = index * 600.0,
        endSeconds = (index + 1) * 600.0
    )

    private fun testBookBookmark(id: String, title: String, timeSeconds: Double) = BookBookmarkEntity(
        serverId = serverId,
        libraryId = libraryId,
        bookId = bookId,
        id = id,
        title = title,
        timeSeconds = timeSeconds,
        createdAtMs = 1_700_000_000L
    )

    // endregion
}
