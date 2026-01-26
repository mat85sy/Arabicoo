package com.animeblkom


import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.CloudflareKiller
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import android.util.Log
import kotlinx.coroutines.delay

class AnimeBlkom : MainAPI() {
    override var mainUrl = "https://animeblkom.net"
    override var name = "AnimeBlkom"
    override var lang = "ar"
    override val hasMainPage = true
    
    private val cfKiller = CloudflareKiller()
    
    // Enhanced headers to better mimic a real browser
    private val headersBuilder = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.5",
        "Accept-Encoding" to "gzip, deflate",
        "Connection" to "keep-alive",
        "Upgrade-Insecure-Requests" to "1",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "none",
        "Cache-Control" to "max-age=0"
    )
    
    // Helper method to perform requests with retry logic for Cloudflare challenges
    private suspend fun requestWithRetry(url: String, referer: String? = null): Document {
        val requestHeaders = if (referer != null) {
            headersBuilder + mapOf("Referer" to referer)
        } else {
            headersBuilder
        }
        
        // Try initial request
        var response = app.get(url, headers = requestHeaders, interceptor = cfKiller)
        
        // If we get a Cloudflare challenge, wait and retry
        var attempts = 0
        while (response.code in 400..500 && !response.isSuccessful && attempts < 3) {
            delay((attempts + 1) * 2000L) // Wait progressively longer
            response = app.get(url, headers = requestHeaders, interceptor = cfKiller)
            attempts++
        }
        
        return response.document
    }

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA,
    )
	
    private fun Element.toSearchResponse(): SearchResponse {
        val url = select("div.poster a").attr("href")
        val name = select("div.name a").text()
        val poster = mainUrl + select("div.poster img").attr("data-original")
        val year = select("div[title=\"سنة الانتاج\"]").text().toIntOrNull()
        val episodesNumber = select("div[title=\"عدد الحلقات\"]").text().toIntOrNull()
        val tvType = select("div[title=\"النوع\"]").text().let { if(it.contains("فيلم|خاصة".toRegex())) TvType.AnimeMovie else if(it.contains("أوفا|أونا".toRegex())) TvType.OVA else TvType.Anime }
        return newAnimeSearchResponse(
            name,
            url,
            tvType,
        ) {
            addDubStatus(false, episodesNumber)
            this.year = year
            this.posterUrl = poster
        }
    }
    override val mainPage = mainPageOf(
        "$mainUrl/anime-list?sort_by=rate&page=" to "Most rated",
        "$mainUrl/anime-list?sort_by=created_at&page=" to "Recently added",
        "$mainUrl/anime-list?states=finished&page=" to "Completed"
    )
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = requestWithRetry(request.data + page)
        val list = document.select("div.content-inner")
            .mapNotNull { element ->
                element.toSearchResponse()
            }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ","+")
        return requestWithRetry("$mainUrl/search?query=$q").select("div.contents.text-center .content").map {
            it.toSearchResponse()
        }
    }
    override suspend fun load(url: String): LoadResponse {
        val document = requestWithRetry(url)

        val title = document.select("span h1").text().replace("\\(.*".toRegex(),"")
        val poster = mainUrl + document.select("div.poster img").attr("data-original")
        val description = document.select(".story p").text()
        val genre = document.select("p.genres a").map {
            it.text()
        }
        val year = document.select(".info-table div:contains(تاريخ الانتاج) span.info").text().split("-")[0].toIntOrNull()
        val status = document.select(".info-table div:contains(حالة الأنمي) span.info").text().let { if(it.contains("مستمر")) ShowStatus.Ongoing else ShowStatus.Completed }
        val nativeName = document.select("span[title=\"الاسم باليابانية\"]").text().replace(".*:".toRegex(),"")
        val type = document.select("h1 small").text().let {
            if (it.contains("movie")) TvType.AnimeMovie
            if (it.contains("ova|ona".toRegex())) TvType.OVA
            else TvType.Anime
        }

        val malId = document.select("a.blue.cta:contains(المزيد من المعلومات)").attr("href").replace(".*e\\/|\\/.*".toRegex(),"").toInt()
        val episodes = arrayListOf<Episode>()
        val episodeElements = document.select(".episode-link")
        if(episodeElements.isEmpty()) {
            episodes.add(Episode(
                url,
                "Watch",
            ))
        } else {
            Log.d("episodeElements","${episodeElements}")
            episodeElements.map {
                val a = it.select("a")
                Log.d("aUrls","${a}")
                episodes.add(Episode(
                    a.attr("href"),
                    a.text().replace(":"," "),
                    episode = a.select("span").not(".pull-left").last()?.text()?.toIntOrNull()
                ))
            }
        }
        return newAnimeLoadResponse(title, url, type) {
            addMalId(malId)
            japName = nativeName
            engName = title
            posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes) // TODO CHECK
            plot = description
            tags = genre

            showStatus = status
        }
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = requestWithRetry(data)
        Log.d("selectUrls","${document.select("div.item a[data-src]")}")
        document.select("div.item a[data-src]").map {
            it.attr("data-src").let { url ->
                Log.d("normalUrls","${url}")
                if(url.startsWith("https://animetitans.net/")) {
                    val iframe = requestWithRetry(url)
                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            "Animetitans " + it.text(),
                            iframe.select("script").last()?.data()?.substringAfter("source: \"")?.substringBefore("\"").toString(),
                            this.mainUrl,
                            Qualities.Unknown.value,
                            isM3u8 = true
                        )
                    )
                } else if(it.text() == "Blkom") {
                    Log.d("blUrls","${url}")
                    val iframe = requestWithRetry(url)
                    iframe.select("source").forEach { source ->
                        callback.invoke(
                            ExtractorLink(
                                this.name,
                                it.text(),
                                source.attr("src"),
                                this.mainUrl,
                                source.attr("res").toInt()
                            )
                        )
                    }
                } else {
                    var sourceUrl = url
                    Log.d("google","${url}")
                    if(it.text().contains("Google")) sourceUrl = "http://gdriveplayer.to/embed2.php?link=$url"
                    loadExtractor(sourceUrl, mainUrl, subtitleCallback, callback)
                }
            }
        }
        document.select(".panel .panel-body a").apmap {
            Log.d("it text","${it.text()}")
            callback.invoke(
                ExtractorLink(
                    this.name,
                    it.attr("title") + " " + it.select("small").text() + " Download Source",
                    it.attr("href"),
                    this.mainUrl,
                    it.text().replace("<.*?>| MiB".toRegex(),"").toDouble().toInt(),
                )
            )
        }
        return true
    }
}