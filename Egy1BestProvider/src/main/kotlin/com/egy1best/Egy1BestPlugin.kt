package com.egy1best

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Egy1BestPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Egy1Best())
    }
}