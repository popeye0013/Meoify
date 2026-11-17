package com.meoify.music.playback

/**
 * Index math for queue edits. Kept free of Android/Media3 types so it can be
 * unit tested without a device.
 */
internal object QueueMath {

    /** Where the playing item ends up after moving [from] to [to]. */
    fun indexAfterMove(current: Int, from: Int, to: Int): Int = when {
        current == from -> to
        from < current && to >= current -> current - 1
        from > current && to <= current -> current + 1
        else -> current
    }

    /** Insertion target that puts [from] immediately after the playing item. */
    fun playNextTarget(current: Int, from: Int): Int =
        if (from < current) current else current + 1

    /** Where the playing item ends up after [removed] is deleted. */
    fun indexAfterRemove(current: Int, removed: Int, sizeAfter: Int): Int {
        if (sizeAfter <= 0) return -1
        return when {
            removed > current -> current
            removed < current -> current - 1
            else -> (current + 1).coerceAtMost(sizeAfter - 1).coerceAtLeast(0)
        }
    }
}
