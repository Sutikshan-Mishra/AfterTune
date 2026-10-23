package dev.aftertune

import android.app.Application

class CompanionApp : Application() {
    lateinit var controller: AppController
        private set

    override fun onCreate() {
        super.onCreate()
        controller = AppController(this)
    }
}
