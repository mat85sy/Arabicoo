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

class AnimeBlkom : MainAPI() {
    override var mainUrl = "https://animeblkom.net"
    override var name = "AnimeBlkom"
    override var lang = "ar"
    override val hasMainPage = true
    
    private val cfKiller = CloudflareKiller()

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
        val document = app.get(request.data + page, interceptor = cfKiller).document
        val list = document.select("div.content-inner")
            .mapNotNull { element ->
                element.toSearchResponse()
            }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ","+")
        return app.get("$mainUrl/search?query=$q", interceptor = cfKiller).document.select("div.contents.text-center .content").map {
            it.toSearchResponse()
        }
    }
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, interceptor = cfKiller).document

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
        val document = app.get(data, interceptor = cfKiller).document
        Log.d("selectUrls","${document.select("div.item a[data-src]")}")
        document.select("div.item a[data-src]").map {
            it.attr("data-src").let { url ->
                Log.d("normalUrls","${url}")
                if(url.startsWith("https://animetitans.net/")) {
                    val iframe = app.get(url, interceptor = cfKiller).document
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
                    val iframe = app.get(url, interceptor = cfKiller).document
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