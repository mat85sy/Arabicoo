package com.ristoanime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.nicehttp.requestCreator
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.nodes.Element

class RistoAnime : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://ristoanime.org"
    override var name = "RistoAnime"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Anime)

    private val cfKiller = CloudflareKiller()

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = select("a").attr("href") ?: return null
        val posterUrl = select("img").attr("data-src") ?: select("img").attr("src")
        val title = select("img").attr("alt") ?: select(".title").text()
        
        return AnimeSearchResponse(
            title,
            url,
            this@RistoAnime.name,
            TvType.Anime,
            posterUrl,
            null,
            null,
            posterHeaders = cfKiller.getCookieHeaders(mainUrl).toMap()
        )
    }

    override val mainPage = mainPageOf(
        "$mainUrl/category/anime/page/" to "أقسام الأنمي",
        "$mainUrl/genre/ ongoing/page/" to "المكتملة",
        "$mainUrl/genre/ completed/page/" to "المستمرة",
        "$mainUrl/trending/page/" to "شائع",
        "$mainUrl/latest_additions/page/" to "المضافة حديثاً"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}$page", interceptor = cfKiller).document
        
        val items = document.select("article, .PostList article, .Bpage article").mapNotNull { element ->
            val anchor = element.selectFirst("a") ?: return@mapNotNull null
            val link = anchor.attr("href")
            val image = element.selectFirst("img") ?: return@mapNotNull null
            val imageUrl = image.attr("data-src").ifEmpty { image.attr("src") }
            val title = image.attr("alt").ifEmpty { element.selectFirst(".title")?.text() ?: return@mapNotNull null }
            
            AnimeSearchResponse(
                title,
                link,
                this@RistoAnime.name,
                TvType.Anime,
                imageUrl,
                null,
                null,
                posterHeaders = cfKiller.getCookieHeaders(mainUrl).toMap()
            )
        }
        
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = query.replace(" ", "+")
        val document = app.get("$mainUrl/?s=$encodedQuery", interceptor = cfKiller).document
        
        return document.select(".SearchInnerList article, .PostList article, article").mapNotNull { element ->
            val anchor = element.selectFirst("a") ?: return@mapNotNull null
            val link = anchor.attr("href")
            val image = element.selectFirst("img") ?: return@mapNotNull null
            val imageUrl = image.attr("data-src").ifEmpty { image.attr("src") }
            val title = image.attr("alt").ifEmpty { element.selectFirst(".title")?.text() ?: return@mapNotNull null }
            
            AnimeSearchResponse(
                title,
                link,
                this@RistoAnime.name,
                TvType.Anime,
                imageUrl,
                null,
                null,
                posterHeaders = cfKiller.getCookieHeaders(mainUrl).toMap()
            )
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, interceptor = cfKiller).document
        
        val title = document.selectFirst("h1.Title, .Title")?.text() ?: throw Error("No title found")
        val poster = document.selectFirst("div.Image img, .Image img")?.attr("data-src")
            ?.ifEmpty { document.selectFirst("div.Image img, .Image img")?.attr("src") }
        
        val description = document.selectFirst("div.Description p, .Description p")?.text()
        
        val episodes = mutableListOf<Episode>()
        
        // Check if it's a series with seasons/episodes
        val seasonElements = document.select(".SeasonsList li a, .season-item a")
        if (seasonElements.isNotEmpty()) {
            // Multiple seasons
            seasonElements.forEach { seasonElement ->
                val seasonUrl = seasonElement.attr("href")
                val seasonNumber = seasonElement.attr("data-season").toIntOrNull() ?: 1
                
                // Get episodes for this season
                try {
                    val seasonDoc = app.get(seasonUrl, interceptor = cfKiller).document
                    seasonDoc.select(".EpisodesList a, .episode-item a").forEach { epElement ->
                        val epLink = epElement.attr("href")
                        val epNum = epElement.text().getIntFromText() ?: 0
                        
                        episodes.add(
                            Episode(
                                epLink,
                                "الموسم $seasonNumber الحلقة $epNum",
                                seasonNumber,
                                epNum
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Handle case where we can't fetch individual season
                    // Just add placeholder episodes
                }
            }
        } else {
            // Single episodes or movie
            document.select(".EpisodesList a, .episode-item a, #watch li a").forEach { epElement ->
                val epLink = epElement.attr("href")
                val epTitle = epElement.text()
                val epNum = epTitle.getIntFromText() ?: 0
                
                episodes.add(
                    Episode(
                        epLink,
                        epTitle,
                        1, // Season 1
                        epNum
                    )
                )
            }
        }
        
        return if (episodes.isEmpty()) {
            // It's likely a movie
            newMovieLoadResponse(title, url, TvType.Anime, url) {
                this.posterUrl = poster
                this.plot = description
                this.posterHeaders = cfKiller.getCookieHeaders(mainUrl).toMap()
            }
        } else {
            // It's a series
            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes.sortedBy { it.episode }) {
                this.posterUrl = poster
                this.plot = description
                this.posterHeaders = cfKiller.getCookieHeaders(mainUrl).toMap()
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, interceptor = cfKiller).document
        
        // Look for embedded iframes or direct video links
        val iframeSrc = document.selectFirst("iframe[src*=watch], .WatchIframe iframe, #player iframe")?.attr("src")
        
        if (!iframeSrc.isNullOrEmpty()) {
            try {
                val webView = WebViewResolver(
                    Regex("""master\.m3u8""")
                ).resolveUsingWebView(
                    requestCreator(
                        "GET", iframeSrc, referer = data
                    )
                ).firstOrNull
            
                if (webView != null && !webView.url.isNullOrEmpty()) {
                    M3u8Helper.generateM3u8(
                        this.name,
                        webView.url!!,
                        referer = data
                    ).toList().forEach(callback)
                } else {
                    // If WebView didn't work, try getting the iframe content directly
                    val iframeDoc = app.get(iframeSrc, referer = data, interceptor = cfKiller).document
                    val videoSrc = iframeDoc.selectFirst("video source[src], #player video source")?.attr("src")
                    
                    if (!videoSrc.isNullOrEmpty()) {
                        callback.invoke(
                            ExtractorLink(
                                this.name,
                                this.name,
                                videoSrc,
                                referer = data,
                                quality = Qualities.Unknown.value
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Handle exception and try alternative methods
            }
        }
        
        // Look for direct download links
        document.select("a[href*=download], a[href*=dl], .DownloadLink").forEach { linkElement ->
            val link = linkElement.attr("href")
            if (link.contains("download") || link.contains(".mp4") || link.contains(".mkv")) {
                callback.invoke(
                    ExtractorLink(
                        this.name,
                        this.name + " Direct",
                        link,
                        referer = data,
                        quality = Qualities.Unknown.value
                    )
                )
            }
        }
        
        return true
    }
}