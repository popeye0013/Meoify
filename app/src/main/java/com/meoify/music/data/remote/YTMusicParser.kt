package com.meoify.music.data.remote

import com.meoify.music.data.model.DetailPage
import com.meoify.music.data.model.DetailSection
import com.meoify.music.data.model.MediaCard
import com.meoify.music.data.model.MediaKind
import com.meoify.music.data.model.Track
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tolerant parsers for YouTube Music's InnerTube responses.
 *
 * The web client wraps almost everything in nested renderers. We keep parsing
 * deliberately defensive: a missing optional field should never break a screen.
 */
object YTMusicParser {

    // ---------------------------------------------------------------- account

    /**
     * Pulls the signed-in account out of an account/account_menu response.
     * YouTube has shipped at least two layouts for this page, so we look for
     * both the per-item renderer (new) and the header renderer (old).
     */
    fun parseAccount(root: JsonObject): com.meoify.music.data.model.AccountInfo? {
        val candidates = buildList {
            addAll(root.findAll("accountItem").mapNotNull { it.asObjectOrNull() })
            addAll(root.findAll("activeAccountHeaderRenderer").mapNotNull { it.asObjectOrNull() })
        }
        val holder = candidates.firstOrNull { it["accountName"]?.readText() != null }
            ?: return null

        val email = holder["email"]?.readText()
        val name = holder["accountName"]?.readText()
        val handle = holder["channelHandle"]?.readText()
        val avatar = holder["accountPhoto"]?.asObjectOrNull()?.findThumbnail()
            ?: holder["avatar"]?.asObjectOrNull()?.findThumbnail()

        if (name.isNullOrBlank() && email.isNullOrBlank() && avatar.isNullOrBlank()) return null
        return com.meoify.music.data.model.AccountInfo(
            name = name,
            email = email,
            handle = handle,
            avatarUrl = avatar,
        )
    }

    // ------------------------------------------------------------------ home

    fun parseHome(root: JsonObject): Pair<List<Track>, List<MediaCard>> {
        val carousels = root.findAll("musicCarouselShelfRenderer").mapNotNull { it.asObjectOrNull() }.toList()
        val quickPicks = mutableListOf<Track>()
        val cards = mutableListOf<MediaCard>()

        carousels.forEach { carousel ->
            val items = carousel["contents"]?.asArrayOrNull() ?: return@forEach
            val tracks = items.mapNotNull { item ->
                val renderer = item.asObjectOrNull()
                    ?.get("musicResponsiveListItemRenderer")
                    ?.asObjectOrNull()
                renderer?.let { parseTrack(it) }
            }
            if (tracks.size > quickPicks.size) {
                quickPicks.clear()
                quickPicks.addAll(tracks)
            }
            items.forEach { item ->
                item.asObjectOrNull()?.get("musicTwoRowItemRenderer")?.asObjectOrNull()?.let { twoRow ->
                    parseCard(twoRow)?.let { card ->
                        if (card.kind != MediaKind.TRACK) cards.add(card)
                    }
                }
            }
        }

        return quickPicks.take(30) to cards.distinctBy { it.id }.take(20)
    }

    fun parseNewReleases(root: JsonObject): List<MediaCard> {
        val cards = mutableListOf<MediaCard>()
        root.findAll("musicTwoRowItemRenderer").forEach { element ->
            val renderer = element.asObjectOrNull() ?: return@forEach
            parseCard(renderer)?.let { if (it.kind == MediaKind.ALBUM) cards.add(it) }
        }
        root.findAll("gridRenderer").forEach { grid ->
            grid.asObjectOrNull()?.get("items")?.asArrayOrNull()?.forEach { item ->
                item.asObjectOrNull()?.get("musicTwoRowItemRenderer")?.asObjectOrNull()?.let { renderer ->
                    parseCard(renderer)?.let { if (it.kind == MediaKind.ALBUM) cards.add(it) }
                }
            }
        }
        return cards.distinctBy { it.id }.take(24)
    }

    /**
     * Playlists saved to the signed-in person's YouTube Music library.
     *
     * `FEmusic_liked_playlists` normally answers with a grid of
     * musicTwoRowItemRenderer cards, but some accounts get playlist rows
     * (musicResponsiveListItemRenderer) instead, so both shapes are read.
     * Albums, artists and bare browse shortcuts are dropped.
     */
    fun parseLibraryPlaylists(root: JsonObject): List<MediaCard> {
        val cards = mutableListOf<MediaCard>()

        root.findAll("musicTwoRowItemRenderer").forEach { element ->
            val renderer = element.asObjectOrNull() ?: return@forEach
            parseCard(renderer)?.let { card ->
                if (card.kind == MediaKind.PLAYLIST || card.kind == MediaKind.MIX) cards.add(card)
            }
        }

        root.findAll("musicResponsiveListItemRenderer").forEach { element ->
            val renderer = element.asObjectOrNull() ?: return@forEach
            parsePlaylistRow(renderer)?.let { cards.add(it) }
        }

        return cards
            .filterNot { it.id.startsWith("FEmusic_") }
            .distinctBy { it.id }
            .take(50)
    }

    private fun parsePlaylistRow(renderer: JsonObject): MediaCard? {
        val columns = renderer["flexColumns"]?.asArrayOrNull()?.mapNotNull { item ->
            item.asObjectOrNull()?.get("musicResponsiveListItemFlexColumnRenderer")?.asObjectOrNull()
        }.orEmpty()
        val title = columns.firstOrNull()?.get("text")?.readText()?.trim().orEmpty()
        if (title.isBlank()) return null

        val endpoint = renderer["navigationEndpoint"]?.asObjectOrNull() ?: return null
        val browseId = endpoint.findAll("browseEndpoint").firstOrNull()?.asObjectOrNull()
            ?.get("browseId")?.readText() ?: return null
        val pageType = endpoint.browsePageType()

        val kind = when {
            pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPREb") -> return null
            pageType == "MUSIC_PAGE_TYPE_ARTIST" || pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" -> return null
            browseId.startsWith("UC") -> return null
            browseId.startsWith("RD") -> MediaKind.MIX
            pageType == "MUSIC_PAGE_TYPE_PLAYLIST" ||
                browseId.startsWith("VL") || browseId.startsWith("PL") -> MediaKind.PLAYLIST
            else -> return null
        }

        val subtitle = columns.getOrNull(1)?.get("text")?.readText()?.trim()?.takeIf { it.isNotBlank() }
        return MediaCard(
            id = browseId,
            kind = kind,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = renderer.findThumbnail(),
        )
    }

    fun parseCharts(root: JsonObject): Pair<List<MediaCard>, List<MediaCard>> {
        val artists = mutableListOf<MediaCard>()
        val playlists = mutableListOf<MediaCard>()

        root.findAll("musicCarouselShelfRenderer").forEach { element ->
            val carousel = element.asObjectOrNull() ?: return@forEach
            val header = carousel["header"]?.asObjectOrNull()
                ?.get("musicCarouselShelfBasicHeaderRenderer")?.asObjectOrNull()
            val title = header?.get("title")?.readText().orEmpty()
            val items = carousel["contents"]?.asArrayOrNull() ?: return@forEach
            when {
                title.contains("artist", ignoreCase = true) -> items.forEach { item ->
                    item.asObjectOrNull()?.get("musicResponsiveListItemRenderer")?.asObjectOrNull()?.let { renderer ->
                        parseArtistCard(renderer)?.let { artists.add(it) }
                    }
                    item.asObjectOrNull()?.get("musicTwoRowItemRenderer")?.asObjectOrNull()?.let { renderer ->
                        parseCard(renderer)?.let { if (it.kind == MediaKind.ARTIST) artists.add(it) }
                    }
                }

                else -> items.forEach { item ->
                    item.asObjectOrNull()?.get("musicTwoRowItemRenderer")?.asObjectOrNull()?.let { renderer ->
                        parseCard(renderer)?.let { card ->
                            if (card.kind == MediaKind.PLAYLIST || card.kind == MediaKind.MIX) {
                                playlists.add(card)
                            }
                        }
                    }
                }
            }
        }

        return artists.distinctBy { it.id }.take(20) to playlists.distinctBy { it.id }.take(10)
    }

    // ---------------------------------------------------------------- search

    fun parseSearch(root: JsonObject, filter: SearchFilter): SearchPageResult {
        val tracks = mutableListOf<Track>()
        val albums = mutableListOf<MediaCard>()
        val artists = mutableListOf<MediaCard>()
        val playlists = mutableListOf<MediaCard>()

        // Search responses with a filter usually contain one musicShelfRenderer.
        root.findAll("musicShelfRenderer").forEach { element ->
            val shelf = element.asObjectOrNull() ?: return@forEach
            val items = shelf["contents"]?.asArrayOrNull() ?: return@forEach
            items.forEach { item ->
                val obj = item.asObjectOrNull() ?: return@forEach
                obj["musicResponsiveListItemRenderer"]?.asObjectOrNull()?.let { renderer ->
                    when (filter) {
                        SearchFilter.SONGS -> parseTrack(renderer)?.let { tracks.add(it) }
                        SearchFilter.ARTISTS -> parseArtistCard(renderer)?.let { artists.add(it) }
                        SearchFilter.ALBUMS, SearchFilter.PLAYLISTS -> Unit
                    }
                }
                obj["musicTwoRowItemRenderer"]?.asObjectOrNull()?.let { renderer ->
                    parseCard(renderer)?.let { card ->
                        when (card.kind) {
                            MediaKind.ALBUM -> albums.add(card)
                            MediaKind.ARTIST -> artists.add(card)
                            MediaKind.PLAYLIST, MediaKind.MIX -> playlists.add(card)
                            else -> Unit
                        }
                    }
                }
            }
        }

        // Some queries only return an itemSectionRenderer list.
        if (tracks.isEmpty() && filter == SearchFilter.SONGS) {
            root.findAll("itemSectionRenderer").forEach { element ->
                val section = element.asObjectOrNull() ?: return@forEach
                val title = section["header"]?.asObjectOrNull()?.get("itemSectionHeaderRenderer")
                    ?.asObjectOrNull()?.get("title")?.readText()
                    ?: section["header"]?.asObjectOrNull()?.get("sectionHeaderRenderer")
                        ?.asObjectOrNull()?.get("title")?.readText()
                    ?: ""
                if (!title.contains("song", ignoreCase = true) && title.isNotBlank()) return@forEach
                section["contents"]?.asArrayOrNull()?.forEach { item ->
                    item.asObjectOrNull()?.get("musicResponsiveListItemRenderer")?.asObjectOrNull()?.let { renderer ->
                        parseTrack(renderer)?.let { tracks.add(it) }
                    }
                }
            }
        }

        return SearchPageResult(
            tracks = tracks.distinctBy { it.id },
            albums = albums.distinctBy { it.id },
            artists = artists.distinctBy { it.id },
            playlists = playlists.distinctBy { it.id },
        )
    }

    fun parseSuggestions(root: JsonObject): List<String> {
        val out = mutableListOf<String>()
        root.findAll("searchSuggestionRenderer").forEach { element ->
            val renderer = element.asObjectOrNull() ?: return@forEach
            val suggestion = renderer["suggestion"]?.readText()
                ?: renderer.findAll("searchEndpoint").firstOrNull()?.asObjectOrNull()
                    ?.get("query")?.readText()
            if (!suggestion.isNullOrBlank()) out.add(suggestion)
        }
        return out.distinct().take(8)
    }

    // ---------------------------------------------------------------- detail

    fun parseAlbum(root: JsonObject, albumId: String): DetailPage {
        val header = root.findAll("musicResponsiveHeaderRenderer").firstOrNull()?.asObjectOrNull()
        val title = header?.get("title")?.readText().orEmpty().ifBlank { "Unknown album" }
        val subtitle = header?.get("subtitle")?.readText().orEmpty()
        val secondSubtitle = header?.get("secondSubtitle")?.readText().orEmpty()
        val thumbnail = header?.findThumbnail()
        val description = header?.get("description")?.readText()

        val tracks = mutableListOf<Track>()
        root.findAll("musicShelfRenderer").forEach { element ->
            val shelf = element.asObjectOrNull() ?: return@forEach
            shelf["contents"]?.asArrayOrNull()?.forEach { item ->
                item.asObjectOrNull()?.get("musicResponsiveListItemRenderer")?.asObjectOrNull()?.let { renderer ->
                    parseTrack(renderer)?.let { tracks.add(it) }
                }
            }
        }

        val cleaned = tracks.distinctBy { it.id }.map { track ->
            track.copy(
                album = track.album ?: title,
                albumId = track.albumId ?: albumId,
                thumbnailUrl = track.thumbnailUrl ?: thumbnail,
                artist = track.artist.ifBlank { subtitle },
            )
        }

        return DetailPage(
            id = albumId,
            kind = MediaKind.ALBUM,
            title = title,
            subtitle = listOf(subtitle, secondSubtitle).filter { it.isNotBlank() }.joinToString(" • "),
            thumbnailUrl = thumbnail,
            description = description,
            tracks = cleaned,
        )
    }

    fun parsePlaylist(root: JsonObject, playlistId: String): DetailPage {
        val header = root.findAll("musicResponsiveHeaderRenderer").firstOrNull()?.asObjectOrNull()
        val title = header?.get("title")?.readText().orEmpty().ifBlank { "Playlist" }
        val subtitle = header?.get("subtitle")?.readText().orEmpty()
        val secondSubtitle = header?.get("secondSubtitle")?.readText().orEmpty()
        val thumbnail = header?.findThumbnail()
        val description = root.findAll("musicDescriptionShelfRenderer").firstOrNull()?.asObjectOrNull()
            ?.get("description")?.readText()

        val tracks = mutableListOf<Track>()
        root.findAll("musicPlaylistShelfRenderer").forEach { element ->
            val shelf = element.asObjectOrNull() ?: return@forEach
            shelf["contents"]?.asArrayOrNull()?.forEach { item ->
                item.asObjectOrNull()?.get("musicResponsiveListItemRenderer")?.asObjectOrNull()?.let { renderer ->
                    parseTrack(renderer)?.let { tracks.add(it) }
                }
            }
        }

        return DetailPage(
            id = playlistId,
            kind = MediaKind.PLAYLIST,
            title = title,
            subtitle = listOf(subtitle, secondSubtitle).filter { it.isNotBlank() }.joinToString(" • "),
            thumbnailUrl = thumbnail,
            description = description,
            tracks = tracks.distinctBy { it.id }.map { it.copy(thumbnailUrl = it.thumbnailUrl ?: thumbnail) },
        )
    }

    fun parseArtist(root: JsonObject, artistId: String): DetailPage {
        val header = root.findAll("musicImmersiveHeaderRenderer").firstOrNull()?.asObjectOrNull()
            ?: root.findAll("musicVisualHeaderRenderer").firstOrNull()?.asObjectOrNull()
        val title = header?.get("title")?.readText().orEmpty().ifBlank { "Artist" }
        val thumbnail = header?.findThumbnail()
        val subscribers = header?.findAll("subscriberCountText")?.firstOrNull()?.readText()
        val description = header?.get("description")?.readText()
        val subtitle = if (!subscribers.isNullOrBlank()) "$subscribers subscribers" else null

        val tracks = mutableListOf<Track>()
        root.findAll("musicShelfRenderer").forEach { element ->
            val shelf = element.asObjectOrNull() ?: return@forEach
            val shelfTitle = shelf["title"]?.readText().orEmpty()
            if (shelfTitle.isNotBlank() && !shelfTitle.contains("song", ignoreCase = true)) return@forEach
            shelf["contents"]?.asArrayOrNull()?.forEach { item ->
                item.asObjectOrNull()?.get("musicResponsiveListItemRenderer")?.asObjectOrNull()?.let { renderer ->
                    parseTrack(renderer)?.let { tracks.add(it) }
                }
            }
        }

        val sections = mutableListOf<DetailSection>()
        root.findAll("musicCarouselShelfRenderer").forEach { element ->
            val carousel = element.asObjectOrNull() ?: return@forEach
            val headerRenderer = carousel["header"]?.asObjectOrNull()
                ?.get("musicCarouselShelfBasicHeaderRenderer")?.asObjectOrNull()
            val sectionTitle = headerRenderer?.get("title")?.readText().orEmpty()
            if (sectionTitle.isBlank()) return@forEach
            val cards = mutableListOf<MediaCard>()
            carousel["contents"]?.asArrayOrNull()?.forEach { item ->
                val obj = item.asObjectOrNull() ?: return@forEach
                obj["musicTwoRowItemRenderer"]?.asObjectOrNull()?.let { parseCard(it) }?.let { card ->
                    if (card.kind == MediaKind.ALBUM || card.kind == MediaKind.PLAYLIST ||
                        card.kind == MediaKind.MIX || card.kind == MediaKind.ARTIST
                    ) {
                        cards.add(card)
                    }
                }
            }
            if (cards.isNotEmpty()) {
                sections.add(DetailSection(title = sectionTitle, items = cards.distinctBy { it.id }))
            }
        }

        return DetailPage(
            id = artistId,
            kind = MediaKind.ARTIST,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumbnail,
            description = description,
            tracks = tracks.distinctBy { it.id },
            sections = sections.take(6),
            roundArt = true,
        )
    }

    fun parseLyrics(root: JsonObject): String? {
        val shelf = root.findAll("musicDescriptionShelfRenderer").firstOrNull()?.asObjectOrNull()
            ?: return null
        val text = shelf["description"]?.readText()?.trim()
        return text?.takeIf { it.isNotBlank() }
    }

    // --------------------------------------------------------------- tracks

    fun parseTrack(renderer: JsonObject): Track? {
        val columns = renderer["flexColumns"]?.asArrayOrNull()?.mapNotNull { item ->
            item.asObjectOrNull()?.get("musicResponsiveListItemFlexColumnRenderer")?.asObjectOrNull()
        }.orEmpty()
        if (columns.isEmpty()) return null

        val title = columns.firstOrNull()?.get("text")?.readText()?.trim().orEmpty()
        if (title.isBlank()) return null

        val subtitleRuns = columns.getOrNull(1)?.get("text")?.asObjectOrNull()
            ?.get("runs")?.asArrayOrNull().orEmpty()

        fun runText(run: JsonObject): String =
            run["text"]?.readText()?.trim().orEmpty()

        val artistRun = subtitleRuns.firstOrNull { run ->
            run.asObjectOrNull()?.browsePageType() == "MUSIC_PAGE_TYPE_ARTIST"
        }?.asObjectOrNull()
        val albumRun = subtitleRuns.firstOrNull { run ->
            run.asObjectOrNull()?.browsePageType() == "MUSIC_PAGE_TYPE_ALBUM"
        }?.asObjectOrNull()

        val subtitleParts = subtitleRuns.mapNotNull { run ->
            val obj = run.asObjectOrNull() ?: return@mapNotNull null
            runText(obj).takeIf { it.isNotBlank() && it != "•" && !DURATION_REGEX.matches(it) }
        }
        val artist = artistRun?.let(::runText)
            ?: subtitleParts.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: ""
        val album = albumRun?.let(::runText)
            ?: subtitleParts.getOrNull(1)?.takeIf { it != artist }

        var durationText = subtitleParts.lastOrNull { DURATION_REGEX.matches(it) }
        if (durationText == null) {
            renderer["fixedColumns"]?.asArrayOrNull()?.forEach { column ->
                val text = column.asObjectOrNull()
                    ?.get("musicResponsiveListItemFixedColumnRenderer")
                    ?.asObjectOrNull()?.get("text")?.readText()
                if (text != null && DURATION_REGEX.matches(text)) durationText = text
            }
        }
        val durationMs = durationText?.let(::parseTimestamp) ?: 0L

        val videoId = renderer["playlistItemData"]?.asObjectOrNull()?.get("videoId")?.readText()
            ?: renderer.findAll("watchEndpoint").firstOrNull()?.asObjectOrNull()?.get("videoId")?.readText()
        if (videoId.isNullOrBlank()) return null

        val plays = columns.getOrNull(2)?.get("text")?.readText()
            ?.takeIf { it.contains("play", true) || it.contains("view", true) }

        return Track(
            id = videoId,
            title = title,
            artist = artist,
            artistId = artistRun?.findBrowseId(),
            album = album,
            albumId = albumRun?.findBrowseId(),
            durationMs = durationMs,
            thumbnailUrl = renderer.findThumbnail(),
            plays = plays,
            explicit = renderer.toString().contains("MUSIC_BADGE_STYLE_EXPLICIT"),
        )
    }

    private fun parseArtistCard(renderer: JsonObject): MediaCard? {
        val columns = renderer["flexColumns"]?.asArrayOrNull()?.mapNotNull { item ->
            item.asObjectOrNull()?.get("musicResponsiveListItemFlexColumnRenderer")?.asObjectOrNull()
        }.orEmpty()
        val title = columns.firstOrNull()?.get("text")?.readText()?.trim().orEmpty()
        if (title.isBlank()) return null
        val subtitle = columns.getOrNull(1)?.get("text")?.readText()?.trim()
        val browseId = renderer.findAll("browseEndpoint").firstOrNull()?.asObjectOrNull()?.get("browseId")?.readText()
            ?: return null
        return MediaCard(
            id = browseId,
            kind = MediaKind.ARTIST,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = renderer.findThumbnail(),
            round = true,
        )
    }

    private fun parseCard(renderer: JsonObject): MediaCard? {
        val title = renderer["title"]?.readText()?.trim().orEmpty().ifBlank { return null }
        val subtitle = renderer["subtitle"]?.readText()?.trim()?.takeIf { it.isNotBlank() }
        val watchId = renderer["navigationEndpoint"]?.asObjectOrNull()
            ?.findAll("watchEndpoint")?.firstOrNull()?.asObjectOrNull()?.get("videoId")?.readText()
        val browseId = renderer["navigationEndpoint"]?.asObjectOrNull()
            ?.findAll("browseEndpoint")?.firstOrNull()?.asObjectOrNull()?.get("browseId")?.readText()
        val pageType = renderer["navigationEndpoint"]?.asObjectOrNull()?.browsePageType()
        val id = browseId ?: watchId ?: return null
        val kind = when {
            pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId?.startsWith("MPREb") == true -> MediaKind.ALBUM
            pageType == "MUSIC_PAGE_TYPE_ARTIST" || pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" ||
                browseId?.startsWith("UC") == true -> MediaKind.ARTIST
            browseId?.startsWith("VL") == true || browseId?.startsWith("PL") == true -> MediaKind.PLAYLIST
            browseId?.startsWith("RD") == true -> MediaKind.MIX
            watchId != null -> MediaKind.TRACK
            else -> MediaKind.PLAYLIST
        }
        return MediaCard(
            id = id,
            kind = kind,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = renderer.findThumbnail(),
            round = kind == MediaKind.ARTIST,
        )
    }

    // ---------------------------------------------------------------- utils

    private val DURATION_REGEX = Regex("""^\d{1,2}:\d{2}(:\d{2})?$""")

    fun parseTimestamp(value: String): Long {
        val parts = value.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]) * 1000
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
            else -> 0L
        }
    }

    private fun JsonObject.browsePageType(): String? {
        val endpoint = this["browseEndpoint"]?.asObjectOrNull() ?: return null
        return endpoint["browseEndpointContextSupportedConfigs"]?.asObjectOrNull()
            ?.get("browseEndpointContextMusicConfig")?.asObjectOrNull()
            ?.get("pageType")?.readText()
    }

    fun JsonObject.findBrowseId(): String? =
        findAll("browseEndpoint").firstOrNull()?.asObjectOrNull()
            ?.get("browseId")?.readText()

    fun JsonObject.findThumbnail(): String? {
        val urls = findAll("thumbnails").mapNotNull { element ->
            element.asArrayOrNull()?.mapNotNull { thumb ->
                thumb.asObjectOrNull()?.let { candidate ->
                    val url = candidate["url"]?.readText() ?: return@mapNotNull null
                    val width = candidate["width"]?.readText()?.toIntOrNull() ?: 0
                    url to width
                }
            }
        }.flatten()
        val best = urls.maxByOrNull { it.second }?.first ?: return null
        return upgradeThumbnail(best)
    }

    private fun upgradeThumbnail(url: String): String {
        if (url.contains("ytimg.com/vi/")) return url
        return url
            .replace(Regex("=w\\d+-h\\d+"), "=w900-h900")
            .replace(Regex("=s\\d+"), "=s900")
    }

    private fun findKey(element: JsonElement?, key: String): Sequence<JsonElement> = sequence {
        when (element) {
            is JsonObject -> {
                element[key]?.let { yield(it) }
                element.values.forEach { value -> yieldAll(findKey(value, key)) }
            }

            is JsonArray -> element.forEach { value -> yieldAll(findKey(value, key)) }
            else -> Unit
        }
    }

    fun JsonElement.findAll(key: String): Sequence<JsonElement> = findKey(this, key)

    fun JsonElement.readText(): String? = when (this) {
        is JsonPrimitive -> contentOrNull?.takeIf { it.isNotBlank() }
        is JsonObject -> {
            val runs = this["runs"]?.asArrayOrNull()
                ?.mapNotNull { run -> run.asObjectOrNull()?.get("text")?.readText() }?.joinToString("")
            runs?.takeIf { it.isNotBlank() }
                ?: this["simpleText"]?.readText()
                ?: this["text"]?.readText()
        }

        else -> null
    }

    fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject

    fun JsonElement.asArrayOrNull(): JsonArray? = this as? JsonArray

    enum class SearchFilter { SONGS, ALBUMS, ARTISTS, PLAYLISTS }

    data class SearchPageResult(
        val tracks: List<Track>,
        val albums: List<MediaCard>,
        val artists: List<MediaCard>,
        val playlists: List<MediaCard>,
    )
}
