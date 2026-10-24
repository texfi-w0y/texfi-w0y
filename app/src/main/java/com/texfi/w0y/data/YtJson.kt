package com.texfi.w0y.data

import com.metrolist.innertubex.models.MusicResponsiveListItemRenderer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Разбор ответов YouTube Music обходом дерева, а не по точным путям.
 *
 * YouTube регулярно переставляет обёртки вокруг одних и тех же карточек
 * (вкладки, секции, «полки»). Поиск нужных рендереров по всему дереву
 * переживает такие перестановки, а жёсткий путь ломается и возвращает
 * пустой список — ровно это уже случилось на вкладках поиска.
 */
object YtJson {
    val json = Json { ignoreUnknownKeys = true }

    /** Рендереры, из которых собирается вся выдача YouTube Music. */
    const val ROW = "musicResponsiveListItemRenderer"
    const val TILE = "musicTwoRowItemRenderer"
    const val SHELF = "musicShelfRenderer"

    /** Все объекты с таким ключом на любой глубине. */
    fun JsonElement.findAll(key: String): List<JsonObject> {
        val found = mutableListOf<JsonObject>()
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject ->
                    element.forEach { (name, value) ->
                        if (name == key && value is JsonObject) found += value
                        walk(value)
                    }

                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(this)
        return found
    }

    /**
     * Все объекты сразу по нескольким ключам — за один обход.
     *
     * Ответ поиска — это дерево на пару мегабайт, и раньше по нему ходили
     * отдельно за треками, отдельно за альбомами, отдельно за артистами:
     * один и тот же обход три раза за один запрос. Теперь обход один.
     */
    fun JsonElement.findAll(keys: Set<String>): Map<String, List<JsonObject>> {
        val found = keys.associateWith { mutableListOf<JsonObject>() }
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject ->
                    element.forEach { (name, value) ->
                        if (value is JsonObject) found[name]?.add(value)
                        walk(value)
                    }

                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(this)
        return found
    }

    fun JsonElement.firstString(key: String): String? {
        var result: String? = null
        fun walk(element: JsonElement) {
            if (result != null) return
            when (element) {
                is JsonObject ->
                    element.forEach { (name, value) ->
                        if (result != null) return@forEach
                        if (name == key && value is JsonPrimitive && value.isString) {
                            result = value.content
                        } else {
                            walk(value)
                        }
                    }

                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(this)
        return result
    }

    /**
     * Строковое значение как есть. Через `jsonPrimitive` нельзя: на месте
     * строки у YouTube иногда оказывается объект, и обращение бросает
     * исключение вместо честного null.
     */
    fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.contentOrNull

    /** Треки из любой выдачи: поиска, плейлиста, лайков, рекомендаций. */
    fun songs(root: JsonElement): List<SongItem> = songsOf(root.findAll(ROW))

    fun songsOf(rows: List<JsonObject>): List<SongItem> =
        rows
            .mapNotNull { obj ->
                val song =
                    runCatching {
                        json.decodeFromJsonElement(MusicResponsiveListItemRenderer.serializer(), obj)
                    }.getOrNull()?.toSong() ?: return@mapNotNull null
                // Ссылки на артиста и альбом лежат в ранах подписи. Типовая
                // модель библиотеки их не разбирает, поэтому берём из сырого
                // объекта: без них не открыть карточку артиста из списка.
                // Ссылки собираем одним проходом: раньше поддерево строки
                // обходилось дважды — отдельно за артистом, отдельно за
                // альбомом, — и это на каждый трек выдачи.
                val links = obj.browseIds()
                song.copy(
                    artistId = links.firstOrNull { it.startsWith("UC") },
                    albumId = links.firstOrNull { it.startsWith("MPRE") },
                    explicit = obj.hasExplicitBadge(),
                    artists = obj.artistLinks(),
                )
            }.distinctBy { it.id }

    /** Артисты: и строкой в выдаче поиска, и плиткой в «похожих». */
    fun artistCards(root: JsonElement): List<ArtistCard> = artistCardsOf(root.findAll(setOf(ROW, TILE)))

    fun artistCardsOf(index: Map<String, List<JsonObject>>): List<ArtistCard> {
        val fromRows =
            index[ROW].orEmpty().mapNotNull { obj ->
                // У трека есть videoId, у артиста — только канал.
                if (obj.firstString("videoId") != null) return@mapNotNull null
                val browseId =
                    obj.browseIds().firstOrNull { it.startsWith("UC") } ?: return@mapNotNull null
                ArtistCard(
                    browseId = browseId,
                    name = obj["flexColumns"]?.firstString("text") ?: return@mapNotNull null,
                    subtitle = null,
                    thumbnailUrl = obj.bestThumbnail(),
                )
            }
        val fromTiles =
            index[TILE].orEmpty().mapNotNull { obj ->
                val browseId =
                    obj.browseIds().firstOrNull { it.startsWith("UC") } ?: return@mapNotNull null
                ArtistCard(
                    browseId = browseId,
                    name = obj["title"]?.firstString("text") ?: return@mapNotNull null,
                    subtitle = obj["subtitle"]?.firstString("text"),
                    thumbnailUrl = obj.bestThumbnail(),
                )
            }
        return (fromRows + fromTiles).distinctBy { it.browseId }
    }

    /**
     * Страница артиста.
     *
     * Секции не разбираются по их заголовкам: YouTube называет их на языке
     * выдачи и регулярно переименовывает, так что «Albums» ловилось бы, а
     * «Альбомы» — нет. Вместо этого берём со страницы всё по типу объекта:
     * треки, релизы и похожих артистов.
     */
    fun artistPage(root: JsonElement, browseId: String): ArtistPage {
        // Один обход на всю страницу: шапка, полки, строки и плитки
        // лежат в одном дереве, и ходить по нему шесть раз незачем.
        val index =
            root.findAll(
                setOf(
                    ROW,
                    TILE,
                    SHELF,
                    "musicImmersiveHeaderRenderer",
                    "musicVisualHeaderRenderer",
                    "musicResponsiveHeaderRenderer",
                ),
            )
        val header =
            index["musicImmersiveHeaderRenderer"]?.firstOrNull()
                ?: index["musicVisualHeaderRenderer"]?.firstOrNull()
                ?: index["musicResponsiveHeaderRenderer"]?.firstOrNull()
        // «Показать все» под полкой треков ведёт в плейлист со всеми
        // песнями артиста. Полка на странице не одна, поэтому берём ту,
        // в которой действительно лежат треки, а не релизы.
        val songsShelf =
            index[SHELF].orEmpty().firstOrNull { shelf ->
                shelf["contents"]?.firstString("videoId") != null
            }
        val allSongs =
            songsShelf?.get("bottomEndpoint")?.findAll("browseEndpoint")?.firstOrNull()
                ?: songsShelf?.get("title")?.findAll("browseEndpoint")?.firstOrNull()
        return ArtistPage(
            browseId = browseId,
            name = header?.get("title")?.firstString("text") ?: "",
            subtitle = header?.firstString("subscriberCountText") ?: header?.get("subtitle")?.firstString("text"),
            thumbnailUrl = header?.bestThumbnail() ?: root.bestThumbnail(),
            songs = songsOf(index[ROW].orEmpty()),
            releases = playlistCardsOf(index),
            similar = artistCardsOf(index).filterNot { it.browseId == browseId },
            allSongsBrowseId = allSongs?.get("browseId").asString(),
            allSongsParams = allSongs?.get("params").asString(),
        )
    }

    /**
     * Треки, альбомы и артисты одной выдачи — за один обход дерева.
     *
     * Для вкладки «всё» это не удобство, а разница в ощущении: обход
     * ответа поиска стоит заметно, и делать его трижды ради одного экрана
     * было бы той самой «оптимизацией потом».
     */
    fun mixed(root: JsonElement): MixedResults {
        val index = root.findAll(setOf(ROW, TILE))
        return MixedResults(
            songs = songsOf(index[ROW].orEmpty()),
            albums = playlistCardsOf(index),
            artists = artistCardsOf(index),
        )
    }

    /**
     * Продолжение длинного списка.
     *
     * YouTube отдаёт плейлисты порциями по сто треков; без токена
     * «все песни артиста» обрывались бы на сотой и выглядели бы
     * как неполный список, а не как порция.
     */
    fun continuation(root: JsonElement): String? =
        root.findAll("continuationItemRenderer").firstNotNullOfOrNull { it.firstString("token") }
            ?: root.findAll("nextContinuationData").firstNotNullOfOrNull { it.firstString("continuation") }

    /**
     * Подсказки поиска: полный запрос, а не то, что выделено жирным.
     *
     * Текст подсказки приходит разбитым на раны («eminem lo» + «se
     * yourself»), а целая строка лежит в её endpoint — оттуда и берём,
     * иначе в поле подставлялся бы обрубок.
     */
    fun searchSuggestions(root: JsonElement): List<String> =
        root
            .findAll("searchSuggestionRenderer")
            .mapNotNull { item ->
                item.findAll("searchEndpoint").firstNotNullOfOrNull { endpoint ->
                    endpoint["query"].asString()
                } ?: item["suggestion"]?.textRuns()?.joinToString("")?.takeIf { it.isNotBlank() }
            }.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /**
     * Пары «трек → его место в плейлисте».
     *
     * Удалить трек из плейлиста YouTube по одному videoId нельзя: один и
     * тот же трек может лежать в плейлисте дважды, и они различают их
     * через setVideoId. Значение приходит только при чтении своего
     * плейлиста с аккаунтом.
     */
    fun setVideoIds(root: JsonElement): Map<String, String> =
        root
            .findAll("playlistItemData")
            .mapNotNull { data ->
                val videoId = data["videoId"].asString() ?: return@mapNotNull null
                val setVideoId = data["playlistSetVideoId"].asString() ?: return@mapNotNull null
                videoId to setVideoId
            }.toMap()

    /**
     * Аватарка владельца из меню аккаунта.
     *
     * Миниатюры у неё бывают без ширины, поэтому если «самой крупной» нет —
     * берём последнюю в списке: YouTube кладёт их по возрастанию. Адрес
     * с размером 48 px растягиваем до 192: на круге в библиотеке он
     * иначе расплывается.
     */
    fun accountAvatar(menu: JsonElement): String? {
        val photo = menu.findAll("accountPhoto").firstOrNull() ?: return null
        val url =
            photo.bestThumbnail()
                ?: (photo["thumbnails"] as? JsonArray)
                    ?.lastOrNull()
                    ?.let { (it as? JsonObject)?.get("url").asString() }
        return url?.replace(Regex("=s\\d+"), "=s192")
    }

    /** Шапка плейлиста: своя обложка и можно ли его править. */
    data class PlaylistHeader(val cover: String?, val editable: Boolean, val found: Boolean)

    private val HEADER_KEYS =
        listOf(
            "musicEditablePlaylistDetailHeaderRenderer",
            "musicResponsiveHeaderRenderer",
            "musicDetailHeaderRenderer",
            "musicImmersiveHeaderRenderer",
        )

    /**
     * Правка доступна, если YouTube отдал редактируемую шапку или хотя бы
     * один трек с playlistSetVideoId — это место трека в плейлисте, и оно
     * приходит только владельцу. Чужой плейлист в библиотеке такого не
     * имеет: менять его нельзя, и кнопок правки для него быть не должно.
     */
    fun playlistHeader(root: JsonElement): PlaylistHeader {
        val index = root.findAll(HEADER_KEYS.toSet())
        val cover =
            HEADER_KEYS.firstNotNullOfOrNull { key ->
                index[key].orEmpty().firstNotNullOfOrNull { it["thumbnail"]?.bestThumbnail() }
            }
        val editable =
            index["musicEditablePlaylistDetailHeaderRenderer"].orEmpty().isNotEmpty() ||
                setVideoIds(root).isNotEmpty()
        return PlaylistHeader(cover, editable, found = index.values.any { it.isNotEmpty() })
    }

    /**
     * Треки страницы плейлиста. На первой странице у своего плейлиста
     * под списком идут «рекомендации» — такие же строки, но не из плейлиста,
     * поэтому берём только полку самого плейлиста, если она нашлась.
     */
    fun playlistTracks(root: JsonElement, first: Boolean): List<SongItem> = playlistPage(root, owned = null).songs

    /**
     * Страница плейлиста: треки, свой ли он и токен следующей страницы.
     *
     * [owned] — что стало ясно по первой странице: null на ней самой. У
     * своего плейлиста на следующих страницах берутся только строки с местом
     * в плейлисте (playlistSetVideoId), без исключений: страница
     * рекомендаций выглядит как продолжение, но мест у её строк нет, и
     * раньше именно так в плейлист попадали чужие треки — каждый раз
     * другие, отчего состав ещё и перемешивался.
     */
    fun playlistPage(root: JsonElement, owned: Boolean?): PlaylistPage {
        val shelves = root.findAll("musicPlaylistShelfRenderer")
        val rows = if (shelves.isNotEmpty()) shelves.flatMap { it.findAll(ROW) } else root.findAll(ROW)
        val own = owned ?: rows.any(::placed)
        val kept = if (own) rows.filter(::placed) else rows
        return PlaylistPage(
            songs = songsOf(kept),
            owned = own,
            continuation = if (owned == null) playlistContinuation(root) else continuation(root),
        )
    }

    data class PlaylistPage(val songs: List<SongItem>, val owned: Boolean, val continuation: String?)

    /**
     * Строка самого плейлиста: у трека своего плейлиста есть место в нём
     * (playlistSetVideoId), у рекомендованной строки — нет. По разметке
     * страницы отличать нельзя: YouTube её меняет.
     */
    private fun placed(row: JsonObject) =
        row.findAll("playlistItemData").any { it["playlistSetVideoId"].asString() != null }

    /**
     * Продолжение именно списка плейлиста, а не полки рекомендаций под ним:
     * у той свой токен, и общий поиск мог взять его первым.
     */
    fun playlistContinuation(root: JsonElement): String? {
        val shelves = root.findAll("musicPlaylistShelfRenderer")
        return shelves.firstNotNullOfOrNull { continuation(it) } ?: if (shelves.isEmpty()) continuation(root) else null
    }

    /** Идентификатор только что созданного в аккаунте плейлиста. */
    fun createdPlaylistId(root: JsonElement): String? =
        (root as? JsonObject)?.get("playlistId").asString()
            ?: root.firstString("playlistId")

    /** Страница альбома: шапка и треклист. */
    fun albumPage(root: JsonElement, browseId: String): AlbumPage {
        val header =
            root.findAll("musicDetailHeaderRenderer").firstOrNull()
                ?: root.findAll("musicResponsiveHeaderRenderer").firstOrNull()
        val cover = header?.bestThumbnail() ?: root.bestThumbnail()
        // Подпись альбома — это «исполнитель • год • треков»: раны приходят
        // вперемешку с разделителями, поэтому склеиваем их сами.
        val subtitle =
            header
                ?.get("subtitle")
                ?.textRuns()
                ?.filter { it.isNotBlank() && it.trim() != "•" }
                ?.joinToString(" · ")
        return AlbumPage(
            browseId = browseId,
            title = header?.get("title")?.firstString("text") ?: "",
            subtitle = subtitle?.takeIf { it.isNotBlank() },
            thumbnailUrl = cover,
            // На странице альбома у строк нет своих картинок: обложка одна
            // на всех. Подставляем её, иначе треклист выглядит пустым.
            songs =
                songs(root).map { song ->
                    if (song.thumbnailUrl == null) {
                        song.copy(thumbnailUrl = cover, albumId = browseId)
                    } else {
                        song.copy(albumId = song.albumId ?: browseId)
                    }
                },
        )
    }

    /**
     * Очередь из ответа `next`: радио и автоплейлисты приходят другим
     * рендерером, не тем, которым отдаются списки и выдача поиска.
     */
    fun queueSongs(root: JsonElement): List<SongItem> =
        root
            .findAll("playlistPanelVideoRenderer")
            .mapNotNull { obj ->
                val videoId = obj.firstString("videoId") ?: return@mapNotNull null
                val title = obj["title"]?.firstString("text") ?: return@mapNotNull null
                // Подпись — «исполнители • альбом • год», но соисполнители и их «&» / «,»
                // приходят отдельными ранами: режем по «•», иначе в альбом попадает «&».
                val byline = splitByBullet(obj["longBylineText"]?.textRuns().orEmpty())
                val links = obj.browseIds()
                SongItem(
                    id = videoId,
                    title = title,
                    artist = byline.firstOrNull().orEmpty(),
                    album = byline.getOrNull(1),
                    durationText = obj["lengthText"]?.firstString("text"),
                    thumbnailUrl = obj.bestThumbnail(),
                    artistId = links.firstOrNull { it.startsWith("UC") },
                    albumId = links.firstOrNull { it.startsWith("MPRE") },
                    explicit = obj.hasExplicitBadge(),
                    artists = obj.artistLinks(),
                )
            }.distinctBy { it.id }

    /**
     * Ленты главной страницы YouTube Music, каждая со своим заголовком.
     *
     * Заголовок берём как есть — он приходит на языке выдачи, и переводить
     * его самим значило бы врать о том, что именно рекомендовано.
     */
    fun shelves(root: JsonElement): List<Shelf> =
        (root.findAll("musicCarouselShelfRenderer") + root.findAll("musicShelfRenderer"))
            .mapNotNull { shelf ->
                val title =
                    shelf["header"]?.firstString("text")
                        ?: shelf["title"]?.firstString("text")
                        ?: return@mapNotNull null
                val index = shelf.findAll(setOf(ROW, TILE))
                val songs = songsOf(index[ROW].orEmpty())
                val cards = playlistCardsOf(index)
                val artists = artistCardsOf(index)
                if (songs.isEmpty() && cards.isEmpty() && artists.isEmpty()) return@mapNotNull null
                Shelf(title = title, songs = songs, cards = cards, artists = artists)
            }.distinctBy { it.title }

    /**
     * Есть ли у карточки значок «E».
     *
     * YouTube вешает его отдельным значком-иконкой, а не полем трека,
     * поэтому ищем сам тип иконки в поддереве карточки.
     */
    fun JsonElement.hasExplicitBadge(): Boolean {
        var found = false
        fun walk(element: JsonElement) {
            if (found) return
            when (element) {
                is JsonObject ->
                    element.forEach { (name, value) ->
                        if (found) return@forEach
                        if (name == "iconType" && value is JsonPrimitive && value.content == EXPLICIT_BADGE) {
                            found = true
                        } else {
                            walk(value)
                        }
                    }

                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(this)
        return found
    }

    private const val EXPLICIT_BADGE = "MUSIC_EXPLICIT_BADGE"

    /** Все browseId в поддереве — в порядке появления. */
    fun JsonElement.browseIds(): List<String> =
        findAll("browseEndpoint").mapNotNull { (it["browseId"] as? JsonPrimitive)?.content }

    /**
     * Исполнители из подписи: раны с текстом и ссылкой на канал (UC…), по
     * порядку и без повторов. У фита их несколько — каждый открывается сам.
     * Пункты меню («перейти к артисту») сюда не попадают: у них текст не
     * строкой, а ранами.
     */
    fun JsonElement.artistLinks(): List<ArtistLink> {
        val out = LinkedHashMap<String, ArtistLink>()
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    val text = (element["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    val id =
                        element["navigationEndpoint"]
                            ?.findAll("browseEndpoint")
                            ?.firstNotNullOfOrNull { (it["browseId"] as? JsonPrimitive)?.content }
                    if (text != null && id != null && id.startsWith("UC") && text.isNotBlank()) {
                        out.getOrPut(id) { ArtistLink(id, text.trim()) }
                    }
                    element.forEach { (_, value) -> walk(value) }
                }

                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(this)
        return out.values.toList()
    }

    /** Самая крупная картинка в поддереве. */
    fun JsonElement.bestThumbnail(): String? {
        var best: Pair<String, Int>? = null
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    val url = (element["url"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    val width = (element["width"] as? JsonPrimitive)?.content?.toIntOrNull()
                    if (url != null && width != null && width > (best?.second ?: 0)) {
                        best = url to width
                    }
                    element.forEach { (_, value) -> walk(value) }
                }

                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(this)
        return best?.first
    }

    /** Все текстовые раны поддерева подряд. */
    fun JsonElement.textRuns(): List<String> {
        val texts = mutableListOf<String>()
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject ->
                    element.forEach { (name, value) ->
                        if (name == "text" && value is JsonPrimitive && value.isString) {
                            texts += value.content
                        } else {
                            walk(value)
                        }
                    }

                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(this)
        return texts
    }

    /**
     * Плейлисты и альбомы.
     *
     * В карусели на странице артиста это плитки, а в выдаче поиска —
     * обычные строки списка. Разбираем оба вида: иначе раздел «альбомы»
     * в поиске оказывается пустым, хотя ответ пришёл полный.
     */
    fun playlistCards(root: JsonElement): List<PlaylistCard> =
        playlistCardsOf(root.findAll(setOf(ROW, TILE)))

    fun playlistCardsOf(index: Map<String, List<JsonObject>>): List<PlaylistCard> {
        val fromRows =
            index[ROW].orEmpty().mapNotNull { obj ->
                if (obj.firstString("videoId") != null) return@mapNotNull null
                val browseId =
                    obj.browseIds().firstOrNull {
                        it.startsWith("MPRE") || it.startsWith("VL")
                    } ?: return@mapNotNull null
                val columns = obj["flexColumns"]?.findAll("musicResponsiveListItemFlexColumnRenderer").orEmpty()
                PlaylistCard(
                    browseId = browseId,
                    title = columns.firstOrNull()?.firstString("text") ?: return@mapNotNull null,
                    subtitle =
                        columns
                            .drop(1)
                            .mapNotNull { it.firstString("text") }
                            .firstOrNull { it.isNotBlank() && it.trim() != "•" },
                    thumbnailUrl = obj.bestThumbnail(),
                )
            }
        return (tileCards(index[TILE].orEmpty()) + fromRows).distinctBy { it.browseId }
    }

    private fun tileCards(tiles: List<JsonObject>): List<PlaylistCard> =
        tiles
            .mapNotNull { obj ->
                val browseId =
                    obj["navigationEndpoint"]
                        ?.jsonObject
                        ?.get("browseEndpoint")
                        ?.jsonObject
                        ?.get("browseId")
                        ?.let { (it as? JsonPrimitive)?.content }
                        ?: return@mapNotNull null
                if (!browseId.startsWith("VL") && !browseId.startsWith("MPRE")) return@mapNotNull null
                val title = obj["title"]?.firstString("text") ?: return@mapNotNull null
                PlaylistCard(
                    browseId = browseId,
                    title = title,
                    subtitle = obj["subtitle"]?.firstString("text"),
                    thumbnailUrl = obj["thumbnailRenderer"]?.bestThumbnail(),
                )
            }.distinctBy { it.browseId }

    /** Режет раны подписи на куски по разделителю «•». */
    private fun splitByBullet(runs: List<String>): List<String> {
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        runs.forEach { run ->
            if (run.trim() == "•") {
                chunks += current.toString().trim()
                current.clear()
            } else {
                current.append(run)
            }
        }
        chunks += current.toString().trim()
        return chunks.filter { it.isNotBlank() }
    }

    /**
     * Похоже ли на счётчик прослушиваний.
     *
     * Слово зависит от языка выдачи, поэтому смотрим на все варианты, а не
     * только на английский: в русской выдаче это «прослушиваний».
     */
    /** Длительность: «3:19», «1:02:11». Год и число сюда не попадают. */
    private fun looksLikeDuration(text: String): Boolean =
        text.contains(':') && text.all { it.isDigit() || it == ':' }

    private fun looksLikePlays(text: String): Boolean {
        val lower = text.lowercase()
        return PLAY_WORDS.any { lower.contains(it) }
    }

    private val PLAY_WORDS =
        listOf("play", "прослуш", "просмотр", "view", "odtworze", "переглянь", "прослухов")

    private fun MusicResponsiveListItemRenderer.toSong(): SongItem? {
        val videoId =
            playlistItemData?.videoId
                ?: navigationEndpoint?.watchEndpoint?.videoId
                ?: return null
        val columns =
            flexColumns.mapNotNull { column ->
                column.musicResponsiveListItemFlexColumnRenderer.text?.runs
            }
        val title = columns.firstOrNull()?.firstOrNull()?.text ?: return null
        // Вторая колонка — «исполнитель • альбом • длительность», но это
        // не три рана, а произвольное их число: соисполнители разделяются
        // запятой и союзом, а сами разделители приходят отдельными ранами.
        // Поэтому режем колонку по «•» и разбираем уже куски, иначе в поле
        // альбома оседает то запятая, то второй исполнитель.
        val chunks = splitByBullet(columns.getOrNull(1).orEmpty().map { it.text })
        // Исполнитель — первый кусок, но только если он и правда похож на
        // имя. В лентах главной у части карточек подписи всего один кусок —
        // «5.2M plays», — и слепое «первый кусок и есть артист» рисовало
        // счётчик прослушиваний на месте имени прямо в быстром наборе.
        val duration = chunks.firstOrNull(::looksLikeDuration)
        val plays = chunks.firstOrNull(::looksLikePlays)
        val named = chunks.filter { it != duration && it != plays }
        val artists = named.firstOrNull().orEmpty()
        val album = named.drop(1).firstOrNull()

        return SongItem(
            id = videoId,
            title = title,
            artist = artists,
            album = album,
            durationText = duration,
            plays = plays,
            thumbnailUrl =
                thumbnail
                    ?.musicThumbnailRenderer
                    ?.thumbnail
                    ?.thumbnails
                    ?.maxByOrNull { it.width ?: 0 }
                    ?.url,
        )
    }
}

/** Карточка плейлиста или альбома из аккаунта или выдачи. */
data class PlaylistCard(
    val browseId: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
) {
    /** Альбомы и плейлисты открываются одинаково, но подписываются по-разному. */
    val isAlbum: Boolean get() = browseId.startsWith("MPRE")
}

/** Карточка артиста. */
data class ArtistCard(
    val browseId: String,
    val name: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
)

/** Страница артиста целиком. */
data class ArtistPage(
    val browseId: String,
    val name: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val songs: List<SongItem> = emptyList(),
    val releases: List<PlaylistCard> = emptyList(),
    val similar: List<ArtistCard> = emptyList(),
    /** Плейлист со всеми треками артиста — тот же, что за «Показать все». */
    val allSongsBrowseId: String? = null,
    val allSongsParams: String? = null,
)

/** Лента на главной: заголовок и то, что в ней лежит. */
data class Shelf(
    val title: String,
    val songs: List<SongItem> = emptyList(),
    val cards: List<PlaylistCard> = emptyList(),
    val artists: List<ArtistCard> = emptyList(),
)

/** Страница альбома целиком. */
data class AlbumPage(
    val browseId: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val songs: List<SongItem> = emptyList(),
)

/** Смешанная выдача одного запроса: всё, что нашлось, по типам. */
data class MixedResults(
    val songs: List<SongItem> = emptyList(),
    val albums: List<PlaylistCard> = emptyList(),
    val artists: List<ArtistCard> = emptyList(),
)
