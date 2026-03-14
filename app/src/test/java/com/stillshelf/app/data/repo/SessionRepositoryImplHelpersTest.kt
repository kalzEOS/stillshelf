package com.stillshelf.app.data.repo

import com.stillshelf.app.core.model.BookProgressMutation
import com.stillshelf.app.core.util.AppResult
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryImplHelpersTest {
    @Test
    fun runDeduped_joinsConcurrentRefreshesForSameKey() = runTest {
        val deduper = DetailRefreshDeduper()
        val startedCount = AtomicInteger(0)
        val release = CompletableDeferred<Unit>()

        val first = async {
            deduper.runDeduped("book:1") {
                startedCount.incrementAndGet()
                release.await()
                AppResult.Success(Unit)
            }
        }
        val second = async {
            deduper.runDeduped("book:1") {
                startedCount.incrementAndGet()
                AppResult.Success(Unit)
            }
        }

        testScheduler.advanceUntilIdle()
        assertEquals(1, startedCount.get())

        release.complete(Unit)
        assertEquals(AppResult.Success(Unit), first.await())
        assertEquals(AppResult.Success(Unit), second.await())
        assertEquals(1, startedCount.get())
    }

    @Test
    fun selectLocalProgressOverride_keepsLocalMutationWhenServerIsOlder() {
        val mutation = BookProgressMutation(
            bookId = "book-1",
            progressPercent = 0.5,
            currentTimeSeconds = 300.0,
            durationSeconds = 600.0,
            isFinished = false
        )

        val selected = selectLocalProgressOverride(
            mutation = mutation,
            fetchedProgressPercent = 0.2,
            fetchedCurrentTimeSeconds = 120.0,
            fetchedDurationSeconds = 600.0,
            fetchedIsFinished = false,
            progressEpsilon = 0.005,
            timeEpsilonSeconds = 1.0
        )

        assertEquals(mutation, selected)
    }

    @Test
    fun selectLocalProgressOverride_dropsMutationWhenServerMatchesIt() {
        val mutation = BookProgressMutation(
            bookId = "book-1",
            progressPercent = 0.5,
            currentTimeSeconds = 300.0,
            durationSeconds = 600.0,
            isFinished = false
        )

        val selected = selectLocalProgressOverride(
            mutation = mutation,
            fetchedProgressPercent = 0.5,
            fetchedCurrentTimeSeconds = 300.5,
            fetchedDurationSeconds = 600.0,
            fetchedIsFinished = false,
            progressEpsilon = 0.005,
            timeEpsilonSeconds = 1.0
        )

        assertNull(selected)
    }
}
