package com.egy1best

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Egy1Best : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://egy1best.cimawbas.tv/egy1/index.php"
    override var name = "Egy1Best"
    override val usesWebView = true  // This site might need webview for JS rendering
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl" to "Home",
        "$mainUrl?page=movies" to "Movies",
        "$mainUrl?page=series" to "Series",
        "$mainUrl?page=trending" to "Trending"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = if (request.data.contains("?")) {
            "${request.data}&page=$page"
        } else {
            "${request.data}?page=$page"
        }
        val document = app.get(url, timeout = 120).document
        val home = document.select("div.movie-item, article, .item").mapNotNull {
            it.toSearchResponse()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val titleElement = selectFirst("h3 a, h2 a, .title a, .movie-title a") ?: return null
        val title = titleElement.text().ifEmpty { this.text() }
        val href = fixUrlNull(titleElement.attr("href")) ?: return null
        val posterElement = selectFirst("img[src], img[data-src], img[data-lazy-src]")
        val posterUrl = fixUrlNull(posterElement?.attr("src") ?: posterElement?.attr("data-src") ?: posterElement?.attr("data-lazy-src"))

        // Determine if it's a movie or series based on various indicators
        val isSeries = select(".series-badge, .tv-series, [class*='series']").isNotEmpty() ||
                       href.contains("series", ignoreCase = true) ||
                       select(".episode-list, .seasons-container").isNotEmpty()

        return MovieSearchResponse(
            title,
            href,
            this@Egy1Best.name,
            if (isSeries) TvType.TvSeries else TvType.Movie,
            posterUrl,
            null,
            null
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl?search=${query.replace(" ", "%20")}"
        val document = app.get(url).document
        return document.select("div.movie-item, article, .item").mapNotNull {
            it.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        
        val title = document.selectFirst("h1, .movie-title, .title")?.text() ?: document.title()
        val poster = fixUrlNull(
            document.selectFirst("img[src*='poster'], .poster img, .thumbnail img")?.attr("src") ?:
            document.selectFirst("img[src]")?.attr("src")
        )
        
        val year = Regex("\\b(19|20)\\d{2}\\b").find(
            document.select(".year, .release-date, .date, .info").text()
        )?.value?.toIntOrNull()

        val tvType = if (document.select(".episodes-list, .seasons, .episode-container").isNotEmpty()) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }

        val description = document.selectFirst(".plot, .description, .synopsis, .story")?.text()

        if (tvType == TvType.TvSeries) {
            // Handle TV Series
            val episodes = mutableListOf<Episode>()
            
            // Look for seasons and episodes
            document.select(".seasons-container .season, .episodes-container .season, .episode-list").forEachIndexed { seasonIndex, season ->
                val seasonNum = seasonIndex + 1
                season.select(".episode, .episode-item").forEach { ep ->
                    val epTitle = ep.selectFirst("a, .episode-title")?.text() ?: ""
                    val epHref = fixUrlNull(ep.selectFirst("a")?.attr("href")) ?: return@forEach
                    val epNumText = ep.selectFirst(".episode-number, .num")?.text() ?: epTitle
                    val epNum = Regex("\\d+").find(epNumText)?.value?.toIntOrNull()
                    
                    episodes.add(
                        Episode(
                            data = epHref,
                            name = epTitle,
                            season = seasonNum,
                            episode = epNum
                        )
                    )
                }
            }
            
            // If no structured episodes found, try alternative selectors
            if (episodes.isEmpty()) {
                document.select("a[href*='episode'], .episode-link").forEach { ep ->
                    val epHref = fixUrlNull(ep.attr("href")) ?: return@forEach
                    val epTitle = ep.text()
                    val seasonMatch = Regex("[sS](\\d+)[eE](\\d+)").find(epTitle)
                    val season = seasonMatch?.groups?.get(1)?.value?.toIntOrNull()
                    val episode = seasonMatch?.groups?.get(2)?.value?.toIntOrNull()
                    
                    episodes.add(
                        Episode(
                            data = epHref,
                            name = epTitle,
                            season = season,
                            episode = episode
                        )
                    )
                }
            }
            
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.distinctBy { it.data }) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
            }
        } else {
            // Handle Movie
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // Look for download links or embedded videos
        document.select("a[href*='download'], a[href*='watch'], a[href*='player'], .mirror a, .server a").forEach { link ->
            val href = fixUrlNull(link.attr("href")) ?: return@forEach
            if (href.isNotBlank()) {
                // Try to extract video links from various sources
                loadExtractor(href, data, subtitleCallback, callback)
                
                // Check for direct video files
                if (Regex("\\.(mp4|mkv|avi|mov|m3u8|webm)\$").containsMatchIn(href.lowercase())) {
                    callback.invoke(
                        ExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = href,
                            referer = data,
                            quality = Qualities.Unknown.value
                        )
                    )
                }
            }
        }
        
        // Check for inline player URLs
        document.select("iframe[src], frame[src], object[data]").forEach { element ->
            val src = fixUrlNull(element.attr("src") ?: element.attr("data"))
            if (!src.isNullOrBlank() && !src.startsWith("javascript:") && !src.startsWith("data:")) {
                loadExtractor(src, data, subtitleCallback, callback)
            }
        }
        
        // Look for JavaScript containing video URLs
        document.select("script").forEach { script ->
            val content = script.html()
            // Look for common patterns of video URLs in JavaScript
            val jsRegex = Regex("\"(https?://[^\"\\s]*\\.(?:mp4|mkv|avi|mov|m3u8|webm|json|php)[^\"\\s]*)\"|'(https?://[^'\\s]*\\.(?:mp4|mkv|avi|mov|m3u8|webm|json|php)[^'\\s]*)'")
            jsRegex.findAll(content).forEach { match ->
                val videoUrl = match.groupValues[1].ifEmpty { match.groupValues[2] }
                if (videoUrl.isNotBlank()) {
                    callback.invoke(
                        ExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = videoUrl,
                            referer = data,
                            quality = Qualities.Unknown.value
                        )
                    )
                }
            }
            
            // Look for specific player configurations
            val playerRegex = Regex("src\\s*:\\s*[\"']([^\"']*)[\"']")
            playerRegex.findAll(content).forEach { match ->
                val videoUrl = match.groupValues[1]
                if (videoUrl.isNotBlank() && Regex("\\.(mp4|m3u8|mkv|avi|mov)\$").containsMatchIn(videoUrl.lowercase())) {
                    callback.invoke(
                        ExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = videoUrl,
                            referer = data,
                            quality = Qualities.Unknown.value
                        )
                    )
                }
            }
        }
        
        return true
    }
}