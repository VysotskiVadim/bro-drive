package com.mapbox.hackathon.bro_drive_app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp

class BroDriveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapboxNavigationApp.setup {
            NavigationOptions.Builder(this).build()
        }
        MapboxNavigationApp.attach(ProcessLifecycleOwner.get())
    }
}