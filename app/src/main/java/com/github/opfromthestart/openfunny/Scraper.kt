package com.github.opfromthestart.openfunny


object Scraper {
    init {
        System.loadLibrary("rust")
    }

    external fun getString(): String

    external fun getImage(s: String): ByteArray
}