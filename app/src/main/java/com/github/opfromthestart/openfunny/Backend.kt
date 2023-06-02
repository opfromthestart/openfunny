package com.github.opfromthestart.openfunny

fun rustInit() {
    System.loadLibrary("rust")
}

external fun test(): String