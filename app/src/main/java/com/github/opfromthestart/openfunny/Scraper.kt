package com.github.opfromthestart.openfunny

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import arrow.core.Tuple10
import java.util.concurrent.Callable
import java.util.function.Function


object Scraper {
    init {
        System.loadLibrary("rust")
    }

     fun inspect(o: Any) {
        val cl = o.javaClass;
         Log.i("Inspect", "Inspecting")
         Log.i("Inspect",cl.canonicalName!!)
        for (m in cl.declaredMethods) {
            Log.i("Inspect",m.name)
            Log.i("Inspect","(")
            for (c in m.parameterTypes) {
                Log.i("Inspect",c.canonicalName!!)
            }
            Log.i("Inspect",")")
            Log.i("Inspect",m.returnType.canonicalName!!)
        }
    }

    external fun initImages(callback: Function<Int, Unit>)

    fun initImagesWrap(callback: Function<Int, Unit>) {
        println("InitImages")
        inspect(callback)
        initImages(callback)
    }

    @Deprecated("Should be dynamic now")
    external fun imageCount(): Int


    external fun getImage(i: Int, callback: Function<ByteArray, Unit>)

    external fun getComments(i: Int, callback: Function<Array<String>, Unit>)

    @Composable
    external fun text(s: String)
}