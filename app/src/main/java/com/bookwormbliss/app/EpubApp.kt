package com.bookwormbliss.app

import android.app.Application

class EpubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: EpubApp
            private set
    }
}
