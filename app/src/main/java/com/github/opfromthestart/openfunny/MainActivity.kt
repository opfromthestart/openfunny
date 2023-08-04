package com.github.opfromthestart.openfunny

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.compose.material.Text
import androidx.compose.material.swipeable
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.core.view.WindowCompat
import com.github.opfromthestart.openfunny.ui.theme.Purple40
import com.github.opfromthestart.openfunny.ui.theme.Purple80
import com.github.opfromthestart.openfunny.ui.theme.PurpleGrey40
import com.github.opfromthestart.openfunny.ui.theme.PurpleGrey80
import com.github.opfromthestart.openfunny.ui.theme.Typography
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    companion object {
        var bounds = Rect()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            MainActivity.bounds = windowManager.currentWindowMetrics.bounds
        } else {
            val disp = windowManager.defaultDisplay
            MainActivity.bounds = Rect(0, 0, disp.width, disp.height)
        }
        println(MainActivity.bounds)
        setContent { OpenFunnyMain() }
    }

}

private val DarkColorScheme = darkColors(
    primary = Purple80,
    secondary = PurpleGrey80,
)

private val LightColorScheme = lightColors(
    primary = Purple40,
    secondary = PurpleGrey40,
)

/* Other default colors to override
background = Color(0xFFFFFBFE),
surface = Color(0xFFFFFBFE),
onPrimary = Color.White,
onSecondary = Color.White,
onTertiary = Color.White,
onBackground = Color(0xFF1C1B1F),
onSurface = Color(0xFF1C1B1F),
*/

@Composable
fun OpenFunnyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colors = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun OpenFunnyMain() {
    var start by remember { mutableStateOf(true) }
    var images by remember { mutableStateOf(0) }

    LaunchedEffect(true) {
        GlobalScope.async {
            Scraper.initImages {
                Log.i(null, "$it images loaded")
                start = false;
                images = it
            }
        }
    }
    if (start) {
        OpenFunnyTheme {
            // A surface container using the 'background' color from the theme
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colors.background
            ) {
                Text(text = "iFunny is loading")
            }
        }
    } else {
        // A surface container using the 'background' color from the theme
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            ImageCarousel()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun ImageCarousel() {
    val curInd = remember {
        SwipeableState(0, tween())
    }
    var curImg by remember {
        mutableStateOf(ByteArray(0))
    }
    var curImg_p by remember {
        mutableStateOf(ByteArray(0))
    }
    var curImg_n by remember {
        mutableStateOf(ByteArray(0))
    }

    val imgs = 1000

    val sizePx: Float = MainActivity.bounds.right.toFloat()
    println(sizePx)

    Log.i(null, "Test ${curInd.currentValue}")
    if (curInd.currentValue in 0 until imgs) {
        LaunchedEffect(curInd.currentValue) {
            GlobalScope.async {
                Scraper.getImage(curInd.currentValue) {
                    Log.i("OpenFunny", "cur img gotten")
                    if (curInd.currentValue%3 == 0) {
                        curImg = it
                    }
                    else if (curInd.currentValue%3 == 1) {
                        curImg_n = it
                    }
                    else if (curInd.currentValue%3 == 2) {
                        curImg_p = it
                    }
                }
                Scraper.getImage(curInd.currentValue+1) {
                    Log.i("OpenFunny", "cur imgn gotten")
                    if (curInd.currentValue%3 == 2) {
                        curImg = it
                    }
                    else if (curInd.currentValue%3 == 0) {
                        curImg_n = it
                    }
                    else if (curInd.currentValue%3 == 1) {
                        curImg_p = it
                    }
                }
                Scraper.getImage(curInd.currentValue-1) {
                    Log.i("OpenFunny", "cur imgp gotten")
                    if (curInd.currentValue%3 == 1) {
                        curImg = it
                    }
                    else if (curInd.currentValue%3 == 2) {
                        curImg_n = it
                    }
                    else if (curInd.currentValue%3 == 0) {
                        curImg_p = it
                    }
                }
            }
        }
    }

    val posMap = (0 until imgs).map { -it *sizePx to it }.toMap()

    if (curImg.isNotEmpty()) {
        var b = BitmapFactory.decodeByteArray(curImg, 0, curImg.size)
        val ib = b?.asImageBitmap()
        b = BitmapFactory.decodeByteArray(curImg_p, 0, curImg_p.size)
        val ibp = b?.asImageBitmap()
        b = BitmapFactory.decodeByteArray(curImg_n, 0, curImg_n.size)
        val ibn = b?.asImageBitmap()
        if (ib != null && ibp != null && ibn != null) {
            Image(
                bitmap = ibp,
                contentDescription = "thing",
                modifier = Modifier
                    .swipeable(
                        curInd,
                        posMap,
                        Orientation.Horizontal
                    )
                    .offset {
                        IntOffset(
                            ((curInd.offset.value - 2*sizePx) % (3 * sizePx) + sizePx ).toInt(),
                            0
                        )
                    })
                Image(
                    bitmap = ib,
                    contentDescription = "thing",
                    modifier = Modifier
                        .swipeable(
                            curInd,
                            posMap,
                            Orientation.Horizontal
                        )
                        .offset {
                            IntOffset(
                                ((curInd.offset.value - sizePx) % (3 * sizePx) + sizePx).toInt(),
                                0
                            )
                        })
                Image(
                    bitmap = ibn,
                    contentDescription = "thing",
                    modifier = Modifier
                        .swipeable(
                            curInd,
                            posMap,
                            Orientation.Horizontal
                        )
                        .offset { IntOffset(((curInd.offset.value ) % (3 * sizePx)+ sizePx).toInt(), 0) })

        } else if (ib != null || ibn != null) {
            val ibv = ib ?: ibn // This guarantees it is not null
            Image(
                bitmap = ibv!!,
                contentDescription = "thing",
                modifier = Modifier
                    .swipeable(
                        curInd,
                        posMap,
                        Orientation.Horizontal
                    )
                    .offset {
                        IntOffset(
                            ((curInd.offset.value ) % sizePx).toInt(),
                            0
                        )
                    })
        }
            else
        {
            Text(text = "Image not found")
        }
    } else {
        Text(text = "Image loading")
    }
}