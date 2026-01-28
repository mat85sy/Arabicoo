package com.faselhd


import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.nicehttp.requestCreator
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.nodes.Element

class FaselHD : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://www.faselhd.cloud"
    private  val alternativeUrl = "https://www.faselhd.club"
    override var name = "FaselHD"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.AsianDrama, TvType.Anime)
    private  val cfKiller = CloudflareKiller()

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = select("div.postDiv a").attr("href") ?: select("a").attr("href") ?: return null
        var posterUrl = select("div.postDiv a div img").attr("data-src") ?:
        select("div.postDiv a div img").attr("src") ?:
        select("img").attr("data-src") ?:
        select("img").attr("src") ?: ""
        
        // Ensure the poster URL is complete
        if (posterUrl.startsWith("//")) {
            posterUrl = "https:$posterUrl"
        } else if (posterUrl.startsWith("/")) {
            posterUrl = mainUrl + posterUrl
        }
        
        val title = select("div.postDiv a div img").attr("alt") ?: 
        select("img").attr("alt") ?: 
        select(".title").text().ifEmpty { select("a").text() }
        
        val quality = select(".quality").first()?.text()?.replace("1080p |-".toRegex(), "")
        val type = if(title.contains("فيلم")) TvType.Movie else TvType.TvSeries
        
        return if (type == TvType.Movie) {
            MovieSearchResponse(
                title.replace("الموسم الأول|برنامج|فيلم|مترجم|اون لاين|مسلسل|مشاهدة|انمي|أنمي".toRegex(),"").trim(),
                url,
                this@FaselHD.name,
                type,
                posterUrl,
                null,
                null,
                quality = getQualityFromString(quality),
                posterHeaders = cfKiller.getCookieHeaders(mainUrl).toMap()
            )
        } else {
            TvSeriesSearchResponse(
                title.replace("الموسم الأول|برنامج|فيلم|مترجم|اون لاين|مسلسل|مشاهدة|انمي|أنمي".toRegex(),"").trim(),
                url,
                this@FaselHD.name,
                type,
                posterUrl,
                null,
                null,
                quality = getQualityFromString(quality),
                posterHeaders = cfKiller.getCookieHeaders(mainUrl).toMap()
            )
        }
    }
    override val mainPage = mainPageOf(
            "$mainUrl/all-movies/page/0" to "جميع الافلام",
            "$mainUrl/movies_top_views/page/0" to "الافلام الاعلي مشاهدة",
            "$mainUrl/dubbed-movies/page/0" to "الأفلام المدبلجة",
            "$mainUrl/movies_top_imdb/page/0" to "الافلام الاعلي تقييما IMDB",
            "$mainUrl/series/page/0" to "مسلسلات",
            "$mainUrl/recent_series/page/" to "المضاف حديثا",
            "$mainUrl/anime/page/0" to "الأنمي",
        )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        var doc = app.get(request.data + page).document
        if(doc.select("title").text() == "Just a moment...") {
            doc = app.get(request.data.replace(mainUrl, alternativeUrl) + page, interceptor = cfKiller, timeout = 120).document
        }
        val list = doc.select("div[id=\"postList\"] div[class=\"col-xl-2 col-lg-2 col-md-3 col-sm-3\"]")
            .mapNotNull { element ->
                element.toSearchResponse()
            }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ","+")
        var d = app.get("$mainUrl/?s=$q").document
        if(d.select("title").text() == "Just a moment...") {
            d = app.get("$alternativeUrl/?s=$q", interceptor = cfKiller, timeout = 120).document
        }
        return d.select("div[id=\"postList\"] div[class=\"col-xl-2 col-lg-2 col-md-3 col-sm-3\"]")
            .mapNotNull {
                it.toSearchResponse()
            }
    }


    override suspend fun load(url: String): LoadResponse {
        var doc = app.get(url).document
        if(doc.select("title").text() == "Just a moment...") {
            doc = app.get(url, interceptor = cfKiller, timeout = 120).document
        }
        val isMovie = doc.select("div.epAll").isEmpty()
        var posterUrl = doc.select("div.posterImg img").attr("src")
            .ifEmpty { doc.select("div.seasonDiv.active img").attr("data-src") }
            .ifEmpty { doc.select("img[itemprop=image]").attr("src") }
            .ifEmpty { doc.select("meta[property=og:image]").attr("content") }
        
        // Ensure the poster URL is complete
        if (posterUrl.startsWith("//")) {
            posterUrl = "https:$posterUrl"
        } else if (posterUrl.startsWith("/") && !posterUrl.startsWith("//")) {
            posterUrl = mainUrl + posterUrl
        }

        val year = doc.select("div[id=\"singleList\"] div[class=\"col-xl-6 col-lg-6 col-md-6 col-sm-6\"]").firstOrNull {
            it.text().contains("سنة|موعد".toRegex())
        }?.text()?.getIntFromText()

        val title =
            doc.select("title").text().replace(" - فاصل إعلاني", "")
                .replace("الموسم الأول|برنامج|فيلم|مترجم|اون لاين|مسلسل|مشاهدة|انمي|أنمي|$year".toRegex(),"")
        // A bit iffy to parse twice like this, but it'll do.
        val duration = doc.select("div[id=\"singleList\"] div[class=\"col-xl-6 col-lg-6 col-md-6 col-sm-6\"]").firstOrNull {
            it.text().contains("مدة|توقيت".toRegex())
        }?.text()?.getIntFromText()

        val tags = doc.select("div[id=\"singleList\"] div[class=\"col-xl-6 col-lg-6 col-md-6 col-sm-6\"]:contains(تصنيف الفيلم) a").map {
            it.text()
        }
        val recommendations = doc.select("div#postList div.postDiv").mapNotNull {
            it.toSearchResponse()
        }
        val synopsis = doc.select("div.singleDesc p").text()
        return if (isMovie) {
            newMovieLoadResponse(
                title.trim(),
                url,
                TvType.Movie,
                url
            ) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = synopsis
                this.duration = duration
                this.tags = tags
                this.recommendations = recommendations
                this.posterHeaders = cfKiller.getCookieHeaders(mainUrl).toMap()
            }
        } else {
            val episodes = ArrayList<Episode>()
            doc.select("div.epAll a").map {
                episodes.add(
                    Episode(
                        it.attr("href"),
                        it.text(),
                        doc.select("div.seasonDiv.active div.title").text().getIntFromText() ?: 1,
                        it.text().getIntFromText(),
                    )
                )
            }
            doc.select("div[id=\"seasonList\"] div[class=\"col-xl-2 col-lg-3 col-md-6\"] div.seasonDiv")
                .not(".active").apmap { it ->
					val id = it.attr("onclick").replace(".*\\/\\?p=|'".toRegex(), "")
                    var s = app.get("$mainUrl/?p="+id).document
                    if(s.select("title").text() == "Just a moment...") {
                        s = app.get("$alternativeUrl/?p="+id, interceptor = cfKiller).document
                    }
                    s.select("div.epAll a").map {
                        episodes.add(
                            Episode(
                                it.attr("href"),
                                it.text(),
                                s.select("div.seasonDiv.active div.title").text().getIntFromText(),
                                it.text().getIntFromText(),
                            )
                        )
                    }
                }
            newTvSeriesLoadResponse(title.trim(), url, TvType.TvSeries, episodes.distinct().sortedBy { it.episode }) {
                this.duration = duration
                this.posterUrl = posterUrl
                this.year = year
                this.plot = synopsis
                this.tags = tags
                this.recommendations = recommendations
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
        var doc = app.get(data).document
        if(doc.select("title").text() == "Just a moment...") {
            doc = app.get(data, interceptor = cfKiller).document
        }
        
        // Get both download and iframe links
        val downloadLink = doc.select(".downloadLinks a").attr("href")
        val iframeSrc = doc.select("iframe[name=\"player_iframe\"]").attr("src")
        
        // Try download link if available
        if(downloadLink.isNotBlank()) {
            try {
                val player = app.post(downloadLink, interceptor = cfKiller, referer = data, timeout = 120).document
                val directLink = player.select("div.dl-link a").attr("href")
                if(directLink.isNotBlank()) {
                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            this.name + " Download Source",
                            directLink,
                            data,
                            Qualities.Unknown.value,
                            headers = cfKiller.getCookieHeaders(data).toMap()
                        )
                    )
                }
            } catch (e: Exception) {
                // Log error but continue with iframe method
                println("Error getting download link: ${e.message}")
            }
        }
        
        // Try iframe method if available
        if(iframeSrc.isNotBlank()) {
            try {
                val webView = WebViewResolver(
                    Regex("""master\.m3u8""")
                ).resolveUsingWebView(
                    requestCreator(
                        "GET", iframeSrc, referer = data
                    )
                ).firstOrNull // Use firstOrNull to avoid crashes if no match found
                
                if(webView != null && !webView.url.isNullOrEmpty()) {
                    M3u8Helper.generateM3u8(
                        this.name,
                        webView.url!!,
                        referer = data,
                        headers = cfKiller.getCookieHeaders(data).toMap()
                    ).toList().forEach(callback)
                } else {
                    // If WebView didn't work, try alternative approach
                    // Get the iframe content and extract sources
                    val iframeDoc = app.get(iframeSrc, referer = data, interceptor = cfKiller).document
                    
                    // Look for video sources in various formats
                    val sources = iframeDoc.select("source").map { it.attr("src") }.filter { it.isNotBlank() }
                    sources.forEach { source ->
                        callback.invoke(
                            ExtractorLink(
                                this.name,
                                this.name + " Video Source",
                                source,
                                data,
                                Qualities.Unknown.value,
                                headers = cfKiller.getCookieHeaders(data).toMap()
                            )
                        )
                    }
                    
                    // Also check for script tags that might contain video URLs
                    val scripts = iframeDoc.select("script")
                    for(script in scripts) {
                        val scriptText = script.html()
                        // Look for common patterns in JavaScript for video URLs
                        val urlPattern = Regex("""(?:src|file|video_url):\s*['"]([^'"]+\.(?:mp4|m3u8|mkv|webm))['"]""")
                        val matches = urlPattern.findAll(scriptText)
                        matches.forEach { match ->
                            val url = match.groupValues[1]
                            callback.invoke(
                                ExtractorLink(
                                    this.name,
                                    this.name + " JS Source",
                                    url,
                                    data,
                                    Qualities.Unknown.value,
                                    headers = cfKiller.getCookieHeaders(data).toMap()
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                println("Error getting iframe link: ${e.message}")
                // As a fallback, try to get the iframe src directly
                callback.invoke(
                    ExtractorLink(
                        this.name,
                        this.name + " Iframe Direct",
                        iframeSrc,
                        data,
                        Qualities.Unknown.value,
                        headers = cfKiller.getCookieHeaders(data).toMap()
                    )
                )
            }
        }
        
        return true
    }
}
