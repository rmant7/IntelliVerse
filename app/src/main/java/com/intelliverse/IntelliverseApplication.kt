package com.intelliverse

import android.app.Application
import android.os.Build
import android.webkit.WebView
import com.intelliverse.BuildConfig
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.example.shared.ads.InterstitialAdUseCase
import com.example.shared.ads.OpenAdUseCase
import dagger.hilt.android.HiltAndroidApp
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class IntelliverseApplication : Application() {

    @Inject
    lateinit var openAdUseCase: OpenAdUseCase

    @Inject
    lateinit var interstitialAdUseCase: InterstitialAdUseCase


    private val adsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            /*
            // Initialize the logger
            Logger.initialize(applicationContext)

            // Preserve the original handler
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

            // Set up a global crash handler
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    Logger.log("CrashHandler", "Uncaught exception in thread ${thread.name}: ${throwable.message}")
                    Logger.log("CrashHandler", throwable.stackTraceToString())
                } catch (e: Exception) {
                    // If logging fails, print the stack trace to standard error
                    e.printStackTrace()
                } finally {
                    // Use the original handler to let the app crash
                    defaultHandler?.uncaughtException(thread, throwable)
                }
            }*/

            // Take out logs of the release version with this set. logs decrease performance
            Timber.plant(Timber.DebugTree())
        }

        clearCache()

        // For WebView if sdk >= 28
        setWebViewDataDirectorySuffix()

        // Initialize the Google Mobile Ads SDK on a background thread.
        adsScope.launch(Dispatchers.IO + SupervisorJob()) {
            MobileAds.initialize(this@IntelliverseApplication) {}
            MobileAds.setAppMuted(true)
        }

        // preloading ads
        openAdUseCase.load()
        interstitialAdUseCase.load()


        // Initialize AppMetrica in a background thread.
        initializeAppMetrica()

    }

    private fun clearCache() {
        try {
            val cacheDir = applicationContext.cacheDir
            if (cacheDir.isDirectory) {
                cacheDir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setWebViewDataDirectorySuffix() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val processName = getProcessName()
        if (!packageName.equals(processName)) {
            WebView.setDataDirectorySuffix(processName)
        }
    }

    private fun initializeAppMetrica() = CoroutineScope(Dispatchers.IO).launch {
        // Init FirebaseApp for all processes
        FirebaseApp.initializeApp(this@IntelliverseApplication)

        Timber.d("Creating an extended library configuration.")
        val config = AppMetricaConfig
            .newConfigBuilder(BuildConfig.app_metrica_api_key)
            .withLocationTracking(true)
            .withSessionsAutoTrackingEnabled(true)
            .build()

        Timber.d("Initializing the AppMetrica SDK.")
        AppMetrica.activate(applicationContext, config)
        // Automatic tracking of user activity.
        // Probably doesn't work for older api.
        AppMetrica.enableActivityAutoTracking(this@IntelliverseApplication)
        /* Devs recommended to send events manually if metrica data isn't
           updated consistently, however seems like it doesn't work. */
        //AppMetrica.sendEventsBuffer()
    }
}