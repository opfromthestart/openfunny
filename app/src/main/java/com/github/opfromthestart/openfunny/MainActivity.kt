package com.github.opfromthestart.openfunny

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import arrow.core.Either
import com.github.opfromthestart.openfunny.ui.theme.OpenFunnyTheme
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        runBlocking {
            super.onCreate(savedInstanceState)
            var html: Either<String, String>? = null
            launch(newSingleThreadContext("web")) {
                val html_a = GlobalScope.async { Scraper.getString() }
                while (html_a.isActive) {
                    delay(200)
                }
                println("finished")
                html = Either.Left(html_a.await())
//                println(html)
            }

            launch(newSingleThreadContext("view")) {
                setContent {
                    OpenFunnyTheme {
                        // A surface container using the 'background' color from the theme
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Text(text = "iFunny is loading")
                        }
                    }
                }
                while (html == null) {
                    delay(200)
                }

                val ba = Scraper.getImage("https://images.nintendolife.com/fc2d0b25ab9ce/zelda-tears-of-the-kingdom-all-zonai-devices-how-to-use-where-to-get-6.large.jpg")
                val b = BitmapFactory.decodeByteArray(ba, 0, ba.size)
                val ib = b?.asImageBitmap()

                setContent {
                    OpenFunnyTheme {
                        // A surface container using the 'background' color from the theme
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            if (ib != null) {
                                Image(bitmap = ib, contentDescription = "thing")
                            } else {
                                Text(text = "e")
                            }
                            Text(text = html.toString())
                        }
                    }
                }
            }
        }
    }
}