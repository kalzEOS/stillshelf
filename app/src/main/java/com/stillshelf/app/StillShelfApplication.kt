package com.stillshelf.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.stillshelf.app.core.diagnostics.DiagnosticLoggingEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

@HiltAndroidApp
class StillShelfApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        val diagnosticLogManager = EntryPointAccessors.fromApplication(
            this,
            DiagnosticLoggingEntryPoint::class.java
        ).diagnosticLogManager()
        diagnosticLogManager.initialize()
        diagnosticLogManager.logLifecycle("AppLifecycle", "process_created")
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                diagnosticLogManager.logLifecycle("AppLifecycle", "foreground")
            }

            override fun onStop(owner: LifecycleOwner) {
                diagnosticLogManager.logLifecycle("AppLifecycle", "background")
            }
        })
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024L * 1024L)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(false)
            .build()
    }
}
