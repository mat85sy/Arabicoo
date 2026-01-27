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
import kotlin.math.pow
import kotlin.random.Random

class AnimeBlkom : MainAPI() {
    override var mainUrl = "https://blkom.com"
    private val alternativeUrl = "https://www.animeblkom.net"
    override var name = "AnimeBlkom"
    override var lang = "ar"
    override val hasMainPage = true
    
    private val cfKiller = CloudflareKiller()
    
    // Helper function to get the correct image URL with fallback domain support
    private fun getImageUrl(imagePath: String): String {
        return if (imagePath.startsWith("http")) {
            imagePath
        } else {
            // Sometimes the image path is relative to the main domain
            mainUrl + imagePath
        }
    }
    
    // Enhanced headers with realistic browser fingerprint to bypass Cloudflare
    private val baseHeaders = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
        "Accept-Language" to "en-US,en;q=0.9",
        "Accept-Encoding" to "gzip, deflate, br",
        "Connection" to "keep-alive",
        "Upgrade-Insecure-Requests" to "1",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "none",
        "Sec-Fetch-User" to "?1",
        "Cache-Control" to "max-age=0",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    )
    
    // More realistic headers with updated Chrome version
    private val realisticHeaders = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9",
        "Accept-Encoding" to "gzip, deflate, br",
        "Cache-Control" to "no-cache",
        "Pragma" to "no-cache",
        "Priority" to "u=0, i",
        "Sec-Ch-Ua" to "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"",
        "Sec-Ch-Ua-Mobile" to "?0",
        "Sec-Ch-Ua-Platform" to "\"Windows\"",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "none",
        "Sec-Fetch-User" to "?1",
        "Upgrade-Insecure-Requests" to "1",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "X-Requested-With" to "XMLHttpRequest",
        "Connection" to "keep-alive"
    )
    
    // Helper method to perform requests with enhanced Cloudflare bypass
    private suspend fun requestWithRetry(url: String, referer: String? = null, isAjax: Boolean = false): Document {
        // First try with realistic headers
        var requestHeaders = if (isAjax) {
            realisticHeaders + mapOf("X-Requested-With" to "XMLHttpRequest")
        } else {
            realisticHeaders
        }
        
        if (referer != null) {
            requestHeaders = requestHeaders + mapOf("Referer" to referer)
        }
        
        // Make the initial request with CloudflareKiller interceptor
        var response = app.get(
            url = url,
            headers = requestHeaders,
            interceptor = cfKiller,
            timeout = 30.seconds
        )
        
        // If we get blocked, try with more sophisticated approach
        var attempts = 0
        while ((response.code in 400..599 || response.code == 503 || response.code == 403) && attempts < 5) {
            val delayMs = (2.0.pow(attempts.toDouble()) * 1000 + (1000..3000).random()).toLong() // Add some randomness
            Log.d("AnimeBlkom", "Cloudflare challenge detected (code: ${response.code}), attempt $attempts, waiting ${delayMs}ms")
            
            delay(delayMs)
            
            // Rotate through different header sets to avoid detection
            val alternateHeaders = when (attempts % 3) {
                0 -> realisticHeaders
                1 -> baseHeaders
                else -> realisticHeaders + mapOf("X-Requested-With" to "XMLHttpRequest")
            }
            
            val finalHeaders = if (referer != null) {
                alternateHeaders + mapOf("Referer" to referer)
            } else {
                alternateHeaders
            }
            
            response = app.get(
                url = url,
                headers = finalHeaders,
                interceptor = cfKiller,
                timeout = 45.seconds
            )
            
            attempts++
        }
        
        // If still unsuccessful, try with session persistence
        if (!response.isSuccessful && attempts >= 5) {
            Log.d("AnimeBlkom", "Standard retry failed, attempting session-based approach...")
            
            // Create a custom session to maintain cookies and state
            val customApp = app.newBuilder().apply {
                // Enable cookie persistence
            }.build()
            
            response = customApp.get(
                url = url,
                headers = realisticHeaders + (referer?.let { mapOf("Referer" to it) } ?: emptyMap()),
                interceptor = cfKiller,
                timeout = 60.seconds
            )
        }
        
        // Check if we're still getting Cloudflare protection
        if (response.code == 403 || response.code == 503) {
            Log.d("AnimeBlkom", "Still blocked by Cloudflare after retries. Response code: ${response.code}, trying alternative URL...")
            
            // Try with alternative URL
            val altUrl = if (url.contains("blkom.com")) {
                url.replace("blkom.com", "www.animeblkom.net")
            } else if (url.contains("animeblkom.net")) {
                url.replace("www.animeblkom.net", "blkom.com")
            } else {
                url // Return original if neither domain is matched
            }
            
            if (altUrl != url) {
                Log.d("AnimeBlkom", "Trying alternative URL: $altUrl")
                
                // Try the alternative URL with similar retry logic
                var altResponse = app.get(
                    url = altUrl,
                    headers = realisticHeaders + (referer?.let { mapOf("Referer" to it) } ?: emptyMap()),
                    interceptor = cfKiller,
                    timeout = 45.seconds
                )
                
                var altAttempts = 0
                while ((altResponse.code in 400..599 || altResponse.code == 503 || altResponse.code == 403) && altAttempts < 3) {
                    val delayMs = (2.0.pow(altAttempts.toDouble()) * 1000 + (2000..4000).random()).toLong()
                    Log.d("AnimeBlkom", "Alternative URL blocked (code: ${altResponse.code}), attempt $altAttempts, waiting ${delayMs}ms")
                    
                    delay(delayMs)
                    
                    val altHeaders = when (altAttempts % 2) {
                        0 -> realisticHeaders
                        else -> baseHeaders
                    }
                    
                    altResponse = app.get(
                        url = altUrl,
                        headers = altHeaders + (referer?.let { mapOf("Referer" to it) } ?: emptyMap()),
                        interceptor = cfKiller,
                        timeout = 60.seconds
                    )
                    
                    altAttempts++
                }
                
                if (altResponse.isSuccessful) {
                    return altResponse.document
                }
            }
            
            throw Error("Site is blocking requests with Cloudflare protection. Code: ${response.code}")
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
        val poster = getImageUrl(select("div.poster img").attr("data-original"))
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