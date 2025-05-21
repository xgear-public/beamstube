package com.awebo.ytext

import android.app.Application
import com.awebo.ytext.di.initKoin
import org.koin.android.ext.koin.androidContext

class YTExtApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@YTExtApp)
        }
    }

}
