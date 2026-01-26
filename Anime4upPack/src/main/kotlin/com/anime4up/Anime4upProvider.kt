package com.anime4up

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.utils.ExtractorLink
import okio.ByteString.Companion.decodeBase64
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URL

private fun String.getIntFromText(): Int? {
    return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
}
class WitAnime : Anime4up() {
    override var name = "WitAnime"
    override var mainUrl = "https://witanime.you/"
}
open class Anime4up : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://witaanime.com/" // Updated to actual site since aniime4up.com redirects
    override var name = "Anime4up"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes =
        setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA, TvType.Others )

    
    private fun Element.toSearchResponse(): SearchResponse {
        val imgElement = select("div.hover > img")
        val url = select("div.hover > a").attr("href")
            .replace("-%d8%a7%d9%84%d8%ad%d9%84%d9%82%d8%a9-.*".toRegex(), "")
            .replace("episode", "anime")
        val title = imgElement.attr("alt")
        val posterUrl = imgElement.attr("src")
        val typeText = select("div.anime-card-type > a").text()
        val type =
            if (typeText.contains("TV|Special".toRegex())) TvType.Anime
            else if(typeText.contains("OVA|ONA".toRegex())) TvType.OVA
            else if(typeText.contains("Movie")) TvType.AnimeMovie
            else TvType.Others
        return newAnimeSearchResponse(
            title,
            url,
            type,
        ) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get("$mainUrl/").document
        val homeList = mutableListOf<HomePageList>()
        
        // Updated selectors to match current website structure
        val featuredSection = doc.select("section.home-slider, .featured-animes, .slider-section").firstOrNull()
        if(featuredSection != null) {
            val featuredAnimes = featuredSection.select("div.anime-card, .slide-item, .featured-item").mapNotNull {
                try {
                    it.toSearchResponse()
                } catch(e: Exception) {
                    null
                }
            }.distinct()
            if(featuredAnimes.isNotEmpty()) {
                homeList.add(HomePageList("Featured Animes", featuredAnimes))
            }
        }
        
        // Handle other sections with flexible selectors
        val sections = doc.select("section, .section, .home-section, .content-section")
        sections.forEach { section ->
            val titleElement = section.selectFirst("h2, h3, .section-title, .title") 
            if(titleElement != null) {
                val title = titleElement.text().ifEmpty { "More Animes" }
                val list = section.select("div.anime-card, div.card, .anime-item, .item").mapNotNull {
                    try {
                        it.toSearchResponse()
                    } catch(e: Exception) {
                        null
                    }
                }.distinct()
                
                if(list.isNotEmpty()) {
                    homeList.add(HomePageList(title, list, isHorizontalImages = title.contains("حلقات") || title.contains("Episodes")))
                }
            }
        }
        
        // Fallback: try to get any anime cards if no sections were found
        if(homeList.isEmpty()) {
            val fallbackList = doc.select("div.anime-card, div.card, .anime-item, .item").mapNotNull {
                try {
                    it.toSearchResponse()
                } catch(e: Exception) {
                    null
                }
            }.distinct()
            
            if(fallbackList.isNotEmpty()) {
                homeList.add(HomePageList("Animes", fallbackList))
            }
        }
        
        return newHomePageResponse(homeList, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?search_param=animes&s=$query"
        val document = app.get(searchUrl).document
        
        // Try multiple possible selectors for search results
        var searchResults = document.select("div.row.display-flex > div").mapNotNull {
            try {
                it.toSearchResponse()
            } catch(e: Exception) {
                null
            }
        }
        
        if(searchResults.isEmpty()) {
            searchResults = document.select("div.anime-card, div.search-result, .result-item, .anime-item").mapNotNull {
                try {
                    it.toSearchResponse()
                } catch(e: Exception) {
                    null
                }
            }
        }
        
        return searchResults.distinct()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        // Flexible selectors to handle potential website changes
        val title = doc.select("h1.anime-details-title, h1.title, .anime-title").text().ifEmpty { 
            doc.select("title").text().substringBeforeLast("-").trim() 
        }
        
        val poster = doc.select("div.anime-thumbnail img, .anime-poster img, .thumbnail img").attr("src").ifEmpty {
            doc.select("img[src*=poster], img[src*=thumb]").attr("src")
        }
        
        val description = doc.select("p.anime-story, .anime-description, .story, .plot").text()
        
        val yearText = doc.select("div.anime-info:contains(بداية العرض), .anime-info:contains(Year), .year-info").text()
        val year = if(yearText.isNotEmpty()) {
            yearText.replace("بداية العرض: ", "").replace(Regex("[^0-9]"), "").toIntOrNull()
        } else {
            null
        }

        val typeText = doc.select(".anime-info:contains(النوع) a, .anime-type a, .type a").text()
        val type =
            if (typeText.contains("TV|Special".toRegex())) TvType.Anime
            else if(typeText.contains("OVA|ONA".toRegex())) TvType.OVA
            else if(typeText.contains("Movie")) TvType.AnimeMovie
            else TvType.Others

        val malIdString = doc.select("a.anime-mal, a[href*=myanimelist], a[href*=mal]").attr("href")
        val malId = if(malIdString.isNotEmpty()) {
            malIdString.replace(".*e\\/|\\/.*".toRegex(),"").toIntOrNull()
        } else {
            null
        }

        // Flexible selector for episodes
        var episodes = doc.select("div#DivEpisodesList > div, .episodes-list div, .episode-item").apmap {
            try {
                val episodeElement = it.select("h3 a, .episode-title a, a.episode-link").firstOrNull()
                if(episodeElement != null) {
                    val episodeUrl = episodeElement.attr("href")
                    val episodeTitle = episodeElement.text()
                    val posterUrl = it.select(".hover img, .episode-thumb img, img").firstOrNull()?.attr("src")
                    Episode(
                        episodeUrl,
                        episodeTitle,
                        episode = episodeTitle.getIntFromText(),
                        posterUrl = posterUrl
                    )
                } else {
                    null
                }
            } catch(e: Exception) {
                null
            }
        }.filterNotNull()

        // Alternative selector for episodes if the primary one fails
        if(episodes.isEmpty()) {
            episodes = doc.select("a[href*=episode], .episode-link").apmap {
                try {
                    val episodeUrl = it.attr("href")
                    val episodeTitle = it.text()
                    val episodeNumber = episodeTitle.getIntFromText()
                    Episode(
                        episodeUrl,
                        episodeTitle,
                        episode = episodeNumber
                    )
                } catch(e: Exception) {
                    null
                }
            }.filterNotNull()
        }

        return newAnimeLoadResponse(title, url, type) {
            this.apiName = this@Anime4up.name
            addMalId(malId)
            engName = title
            posterUrl = poster.ifEmpty { null }
            this.year = year
            addEpisodes(if(title.contains("مدبلج")) DubStatus.Dubbed else DubStatus.Subbed, episodes)
            plot = description.ifEmpty { null }
        }
    }
    data class sources (
        @JsonProperty("hd"  ) var hd  : Map<String, String>? = null,
        @JsonProperty("fhd" ) var fhd : Map<String, String>? = null,
        @JsonProperty("sd"  ) var sd  : Map<String, String>?  = null
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        if(data.contains("anime4up")) {
            val watchJSON = parseJson<sources>(doc.select("input[name=\"wl\"]").attr("value").decodeBase64()?.utf8() ?: "")
            watchJSON.let { source ->
                source.fhd?.apmap {
                    loadExtractor(it.value, data, subtitleCallback, callback)
                }
                source.hd?.apmap {
                    loadExtractor(it.value, data, subtitleCallback, callback)
                }
                source.sd?.apmap {
                    loadExtractor(it.value, data, subtitleCallback, callback)
                }
            }
            val moshahdaID =  doc.select("input[name=\"moshahda\"]").attr("value").decodeBase64()?.utf8()  ?: ""
            if(moshahdaID.isNotEmpty()) {
                mapOf(
                    "Original" to "download_o",
                    "720" to "download_x",
                    "480" to "download_h",
                    "360" to "download_n",
                    "240" to "download_l"
                ).apmap { (quality, qualityCode) ->
                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            this.name + " Moshahda",
                            "https://moshahda.net/$moshahdaID.html?${qualityCode}",
                            "https://moshahda.net",
                            quality.toIntOrNull() ?: 1080
                        )
                    ) }
            }
        } else if(data.contains("witanime")) { // witanime
            doc.select("ul#episode-servers li a").apmap {
                loadExtractor(it.attr("data-ep-url"), data, subtitleCallback, callback)
            }
        }
        return true
    }
}
