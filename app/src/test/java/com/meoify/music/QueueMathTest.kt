package com.meoify.music

import com.meoify.music.playback.QueueMath
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueMathTest {

    @Test
    fun movingPlayingItemFollowsIt() {
        assertEquals(3, QueueMath.indexAfterMove(current = 1, from = 1, to = 3))
    }

    @Test
    fun movingItemFromBeforePlayingItemShiftsPlayingItemBack() {
        assertEquals(2, QueueMath.indexAfterMove(current = 3, from = 0, to = 4))
        assertEquals(2, QueueMath.indexAfterMove(current = 3, from = 1, to = 3))
        assertEquals(3, QueueMath.indexAfterMove(current = 3, from = 1, to = 2))
    }

    @Test
    fun movingItemFromAfterPlayingItemShiftsPlayingItemForward() {
        assertEquals(3, QueueMath.indexAfterMove(current = 2, from = 4, to = 0))
        assertEquals(3, QueueMath.indexAfterMove(current = 2, from = 4, to = 2))
        assertEquals(2, QueueMath.indexAfterMove(current = 2, from = 4, to = 3))
    }

    @Test
    fun playNextTargetAccountsForRemovalBeforePlayingItem() {
        assertEquals(3, QueueMath.playNextTarget(current = 3, from = 0))
        assertEquals(4, QueueMath.playNextTarget(current = 3, from = 5))
    }

    @Test
    fun removingItemsMovesPlayingIndexCorrectly() {
        assertEquals(1, QueueMath.indexAfterRemove(current = 2, removed = 0, sizeAfter = 3))
        assertEquals(2, QueueMath.indexAfterRemove(current = 2, removed = 3, sizeAfter = 3))
        assertEquals(1, QueueMath.indexAfterRemove(current = 2, removed = 1, sizeAfter = 3))
        assertEquals(-1, QueueMath.indexAfterRemove(current = 0, removed = 0, sizeAfter = 0))
    }
}
