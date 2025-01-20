package com.example.shared.ads

enum class AdId(val id: String) {
    // Adds IDs
    OPEN_AD_SAMPLE_ID("ca-app-pub-3940256099942544/9257395921"),
    INTERSTITIAL_AD_SAMPLE_ID("ca-app-pub-3940256099942544/1033173712"),
    BANNER_AD_SAMPLE_ID("ca-app-pub-3940256099942544/9214589741"),

    // Current banner id is banned.
    BANNER_AD_ID("ca-app-pub-7574006463043131/3537319788"),
    // All other ids work.
    INTERSTITIAL_AD_ID("ca-app-pub-7574006463043131/3853642842"),
    OPEN_AD_ID("ca-app-pub-7574006463043131/5059112992")
}