package com.egibest

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class EgiBest : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://egibest.net"
    override var name = "EgiBest"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/category/movies/" to "Movies",
        "$mainUrl/category/series/" to "Series",
        "$mainUrl/trending/page/" to "Trending",
        "$mainUrl/popular/page/" to "Popular"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("article.item").mapNotNull {
            it.toSearchResponse()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val title = select("h3 a").text().ifEmpty { select("h2 a").text() }
        val href = selectFirst("h3 a, h2 a")?.attr("href") ?: return null
        val posterUrl = selectFirst("img")?.attr("data-lazy-src") ?: selectFirst("img")?.attr("src")

        // Determine if it's a movie or series based on category
        val isSeries = select(".post-type").text().lowercase().contains("series") ||
                      href.lowercase().contains("series") ||
                      select(".post-type").text().lowercase().contains("tv")

        return MovieSearchResponse(
            title,
            href,
            this@EgiBest.name,
            if (isSeries) TvType.TvSeries else TvType.Movie,
            posterUrl,
            null,
            null
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/${query.replace(" ", "+")}/"
        val document = app.get(url).document
        return document.select("article.item").mapNotNull {
            it.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        
        val title = document.selectFirst("h1")?.text() ?: document.title()
        val poster = document.selectFirst("figure.single-post-thumbnail img")?.attr("src")
        
        val year = Regex("\\d{4}").find(
            document.select(".post-meta span").text()
        )?.value?.toIntOrNull()

        val tvType = if (document.select(".seasons-and-episodes").isNotEmpty()) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }

        val description = document.selectFirst(".entry-content p")?.text()

        if (tvType == TvType.TvSeries) {
            // Handle TV Series
            val episodes = mutableListOf<Episode>()
            
            // Look for seasons and episodes
            document.select(".seasons-and-episodes .season").forEachIndexed { seasonIndex, season ->
                val seasonNum = seasonIndex + 1
                season.select(".episode-item").forEach { ep ->
                    val epTitle = ep.selectFirst("a")?.text() ?: ""
                    val epHref = ep.selectFirst("a")?.attr("href") ?: return@forEach
                    val epNum = Regex("\\d+").find(epTitle)?.value?.toIntOrNull()
                    
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
                document.select("a[href*='/episode/']").forEach { ep ->
                    val epHref = ep.attr("href")
                    val epTitle = ep.text()
                    val seasonMatch = Regex("S(\\d+)E(\\d+)").find(epTitle)
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
        document.select("a[href*='download'], a[href*='embed'], a[href*='player']").forEach { link ->
            val href = link.attr("href")
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
        document.select("iframe, script").forEach { element ->
            val src = element.attr("src")
            if (src.isNotBlank() && !src.startsWith("javascript:")) {
                loadExtractor(src, data, subtitleCallback, callback)
            }
            
            // Look for JavaScript containing video URLs
            val content = element.html()
            val jsRegex = Regex("\"(https?://[^\"\\s]*\\.(?:mp4|mkv|avi|mov|m3u8|webm)[^\"\\s]*)\"")
            jsRegex.findAll(content).forEach { match ->
                val videoUrl = match.groupValues[1]
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
        
        return true
    }
}