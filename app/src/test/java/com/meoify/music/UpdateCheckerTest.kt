package com.meoify.music

import com.meoify.music.data.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun parsesBulletChangelog() {
        val body = """
            ## Meoify 1.0.7

            - New update popup on the home screen
            * Fixed a playback crash
            Some paragraph that is not a bullet
            - **Bold** and `code` markers are stripped
        """.trimIndent()

        assertEquals(
            listOf(
                "New update popup on the home screen",
                "Fixed a playback crash",
                "Bold and code markers are stripped",
            ),
            UpdateChecker.parseNotes(body),
        )
    }

    @Test
    fun ignoresEmptyBody() {
        assertTrue(UpdateChecker.parseNotes(null).isEmpty())
        assertTrue(UpdateChecker.parseNotes("").isEmpty())
        assertTrue(UpdateChecker.parseNotes("no bullets here").isEmpty())
    }

    @Test
    fun comparesDottedVersions() {
        assertTrue(UpdateChecker.isNewer("1.0.7", "1.0.6"))
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.9"))
        assertFalse(UpdateChecker.isNewer("1.0.6", "1.0.6"))
        assertFalse(UpdateChecker.isNewer("1.0.5", "1.0.6"))
    }
}
