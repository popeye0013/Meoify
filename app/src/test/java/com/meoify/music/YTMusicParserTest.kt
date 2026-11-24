package com.meoify.music

import com.meoify.music.data.model.AccountInfo
import com.meoify.music.data.remote.YTMusicParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YTMusicParserTest {

    @Test
    fun parsesNestedAccountName() {
        val root = Json.parseToJsonElement(
            """
            {
              "actions": [
                {
                  "activeAccountHeaderRenderer": {
                    "accountName": { "simpleText": "Akash Lal" },
                    "email": { "simpleText": "akash@example.com" },
                    "accountPhoto": {
                      "thumbnails": [
                        { "url": "https://example.com/avatar.png", "width": 72 }
                      ]
                    }
                  }
                }
              ]
            }
            """.trimIndent(),
        ).jsonObject

        val account = YTMusicParser.parseAccount(root)

        assertEquals("Akash Lal", account?.displayName)
        assertEquals("akash@example.com", account?.email)
        assertEquals("https://example.com/avatar.png", account?.avatarUrl)
    }

    @Test
    fun fallsBackToEmailWhenNameIsMissing() {
        val root = Json.parseToJsonElement(
            """
            {
              "contents": {
                "accountItem": {
                  "email": { "simpleText": "popeye@example.com" }
                }
              }
            }
            """.trimIndent(),
        ).jsonObject

        assertEquals("popeye", YTMusicParser.parseAccount(root)?.displayName)
    }

    @Test
    fun ignoresGenericPlaceholderName() {
        assertNull(AccountInfo.realName("YouTube Music account"))
        assertNull(AccountInfo.realName(" youtube music ACCOUNT "))
        assertEquals("Akash", AccountInfo.realName("Akash"))
        assertEquals(
            "popeye",
            AccountInfo.realName(AccountInfo(email = "popeye@example.com")),
        )
    }
}
