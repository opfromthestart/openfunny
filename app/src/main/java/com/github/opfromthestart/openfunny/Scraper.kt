package com.github.opfromthestart.openfunny

import arrow.core.Tuple10
import java.util.concurrent.Callable
import java.util.function.Function


object Scraper {
    init {
        System.loadLibrary("rust")
    }

    private fun inspect(o: Any) {
        val cl = o.javaClass;
        for (m in cl.declaredMethods) {
            println(m.name)
            println("(")
            for (c in m.parameterTypes) {
                println(c.canonicalName)
            }
            println(")")
            println(m.returnType.canonicalName)
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
}