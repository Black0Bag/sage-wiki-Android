package com.sagewiki.android

import android.app.Application
import android.content.Context

class SageWikiApp : Application() {
    companion object {
        lateinit var instance: SageWikiApp
            private set
        val context: Context get() = instance.applicationContext
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
