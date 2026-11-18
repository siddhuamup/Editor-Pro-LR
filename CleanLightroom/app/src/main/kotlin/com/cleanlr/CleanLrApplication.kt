package com.cleanlr

import android.app.Application

class CleanLrApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Preload complete 531-preset color-science catalog from assets/mined_presets.json
        com.cleanlr.preset.MinedPresetLibrary.init(this)
    }
}
