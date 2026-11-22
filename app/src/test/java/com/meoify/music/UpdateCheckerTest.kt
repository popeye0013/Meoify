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

    @Test
    fun parsesAtomFeedFallback() {
        val feed = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Release notes from Meoify</title>
              <entry>
                <updated>2026-11-21T14:24:52Z</updated>
                <link rel="alternate" type="text/html" href="https://github.com/popeye0013/Meoify/releases/tag/v3.5.1"/>
                <title>Meoify 3.5.1</title>
                <content type="html">&lt;ul&gt;
            &lt;li&gt;Fixed removing songs from YouTube Music playlists.&lt;/li&gt;
            &lt;li&gt;Artwork &amp;amp; history fixes.&lt;/li&gt;
            &lt;/ul&gt;</content>
              </entry>
              <entry>
                <updated>2026-11-21T02:36:31Z</updated>
                <link rel="alternate" type="text/html" href="https://github.com/popeye0013/Meoify/releases/tag/v3.5.0"/>
                <title>Meoify 3.5.0</title>
                <content type="html">&lt;ul&gt;
            &lt;li&gt;Redesigned audio quality tiers.&lt;/li&gt;
            &lt;/ul&gt;</content>
              </entry>
            </feed>
        """.trimIndent()

        val releases = UpdateChecker.parseAtomFeed(feed)

        assertEquals(listOf("3.5.1", "3.5.0"), releases.map { it.version })
        assertEquals(
            listOf(
                "Fixed removing songs from YouTube Music playlists.",
                "Artwork & history fixes.",
            ),
            releases.first().notes,
        )
        assertEquals(
            "https://github.com/popeye0013/Meoify/releases/tag/v3.5.1",
            releases.first().url,
        )
        assertEquals("2026-11-21T14:24:52Z", releases.first().published)
        assertEquals(1, UpdateChecker.parseAtomFeed(feed, limit = 1).size)
    }
}
