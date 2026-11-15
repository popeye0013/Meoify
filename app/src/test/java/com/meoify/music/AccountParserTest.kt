package com.meoify.music

import com.meoify.music.data.remote.YTMusicParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountParserTest {

    private fun parse(json: String) =
        YTMusicParser.parseAccount(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun parsesAccountItemLayout() {
        val json = """
            {"actions":[{"openPopupAction":{"popup":{"multiPageMenuRenderer":{
              "sections":[{"accountItemSectionRenderer":{"contents":[{"accountItem":{
                "accountName":{"simpleText":"Akash"},
                "email":{"simpleText":"akash@example.com"},
                "channelHandle":{"simpleText":"@popeye0013"},
                "accountPhoto":{"thumbnails":[
                  {"url":"https://lh3.googleusercontent.com/a/avatar=s88","width":88},
                  {"url":"https://lh3.googleusercontent.com/a/avatar=s176","width":176}
                ]},
                "isSelected":true
              }}]}}]
            }}}}]}
        """.trimIndent()

        val account = parse(json)
        assertEquals("Akash", account?.name)
        assertEquals("akash@example.com", account?.email)
        assertEquals("@popeye0013", account?.handle)
        assertTrue(account?.avatarUrl.orEmpty().startsWith("https://lh3.googleusercontent.com/a/avatar"))
        assertEquals("A", account?.initials)
    }

    @Test
    fun parsesActiveAccountHeaderLayout() {
        val json = """
            {"actions":[{"openPopupAction":{"popup":{"multiPageMenuRenderer":{"header":{
              "activeAccountHeaderRenderer":{
                "accountName":{"simpleText":"Akash"},
                "email":{"simpleText":"akash@example.com"},
                "accountPhoto":{"thumbnails":[{"url":"https://example.com/p=s88","width":88}]}
              }
            }}}}}]}
        """.trimIndent()

        val account = parse(json)
        assertEquals("Akash", account?.name)
        assertEquals("akash@example.com", account?.email)
        assertTrue(account?.avatarUrl.orEmpty().startsWith("https://example.com/p"))
    }

    @Test
    fun returnsNullWhenSignedOut() {
        val json = """{"actions":[{"openPopupAction":{"popup":{"multiPageMenuRenderer":{"header":{"signInButton":{}}}}}}]}"""
        assertNull(parse(json))
    }
}
