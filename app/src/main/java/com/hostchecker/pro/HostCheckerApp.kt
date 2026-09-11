package com.hostchecker.pro

import android.app.Application
import com.hostchecker.pro.di.AppModule

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class HiltAndroidApp

@HiltAndroidApp
class HostCheckerApp : Application() {

    lateinit var appModule: AppModule
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        appModule = AppModule(this)
    }

    companion object {
        lateinit var instance: HostCheckerApp
            private set
    }
}
