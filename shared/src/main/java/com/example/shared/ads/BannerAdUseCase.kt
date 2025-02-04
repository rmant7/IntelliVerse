package com.example.shared.ads

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BannerAdUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var stretchedBanner: AdView? = null

    fun load() {

        if (stretchedBanner == null) {
            //val adSize = AdSize((maxWidth * 0.35).toInt(), (maxHeight * 0.2).toInt())

            /*val adaptiveSize = AdSize.getInlineAdaptiveBannerAdSize(
                maxWidth,
                (maxHeight * 0.2).toInt()
            )*/

            stretchedBanner = AdView(context).apply {
                this.adUnitId = AdId.BANNER_AD_ID.id
                this.setAdSize(AdSize.MEDIUM_RECTANGLE) // AdSize.MEDIUM_RECTANGLE
            }
        }

        loadBanner(stretchedBanner)
    }

    private fun loadBanner(
        adView: AdView?
    ) {

        adView?.apply {
            adListener = object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Timber.d("Adds server is not available${adError.message}")
                }

                override fun onAdLoaded() {
                    Timber.d("Ad loaded successfully")
                }
            }
        }

        adView?.loadAd(AdRequest.Builder().build())
    }

    fun getStretchedBannerAdView(): AdView? {
        return stretchedBanner
    }

}


