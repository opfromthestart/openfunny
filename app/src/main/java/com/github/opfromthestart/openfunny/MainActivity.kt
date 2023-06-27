package com.github.opfromthestart.openfunny

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontLoadingStrategy.Companion.Async
import androidx.compose.ui.tooling.preview.Preview
import arrow.core.Either
import com.github.opfromthestart.openfunny.ui.theme.OpenFunnyTheme
import java.util.concurrent.Callable
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

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

                setContent {
                    OpenFunnyTheme {
                        // A surface container using the 'background' color from the theme
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Text(text = html.toString())
                        }
                    }
                }
            }
        }
    }
}