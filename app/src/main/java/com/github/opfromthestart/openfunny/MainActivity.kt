package com.github.opfromthestart.openfunny

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.FloatRange
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.SwipeableState
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.material.swipeable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.github.opfromthestart.openfunny.ui.theme.Purple40
import com.github.opfromthestart.openfunny.ui.theme.Purple80
import com.github.opfromthestart.openfunny.ui.theme.PurpleGrey40
import com.github.opfromthestart.openfunny.ui.theme.PurpleGrey80
import com.github.opfromthestart.openfunny.ui.theme.Typography
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async

class MainActivity : ComponentActivity() {
    companion object {
        var bounds = Rect()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            bounds = windowManager.currentWindowMetrics.bounds
        } else {
            val disp = windowManager.defaultDisplay
            bounds = Rect(0, 0, disp.width, disp.height)
        }
        println(bounds)
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
                start = false
                images = it
            }
        }
    }
    if (start) {
        OpenFunnyTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
            ) {
                Text(text = "iFunny is loading")
            }
        }
    } else {
        OpenFunnyTheme() {

        Surface(
            modifier = Modifier.fillMaxSize(),
        ) {
            ImageCarousel()
        }
    }
    }
}

val sizePx: Float = MainActivity.bounds.right.toFloat()

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun ImageCarousel() {
    val curInd = remember {
        SwipeableState(0, tween())
    }
    var curImg: ImageBitmap? by remember {
        mutableStateOf(null)
    }
    var curImg_p: ImageBitmap? by remember {
        mutableStateOf(null)
    }
    var curImg_n: ImageBitmap? by remember {
        mutableStateOf(null)
    }
    var comments: Array<String> by remember {
        mutableStateOf(Array(0) { "" })
    }

    val imgs = 1000
    // println(sizePx)

//    Log.i(null, "Test ${curInd.currentValue}")
    if (curInd.currentValue in 0 until imgs) {
        LaunchedEffect(curInd.currentValue) {
            GlobalScope.async {
                Scraper.getImage(curInd.currentValue) {
                    Log.i("OpenFunny", "cur img gotten")
                    var b = BitmapFactory.decodeByteArray(it, 0, it.size)
                    b = Bitmap.createBitmap(b, 0, 0, b.width, b.height - 20)
                    val ib = b?.asImageBitmap()
                    if (curInd.currentValue % 3 == 0) {
                        curImg = ib
                    } else if (curInd.currentValue % 3 == 1) {
                        curImg_n = ib
                    } else if (curInd.currentValue % 3 == 2) {
                        curImg_p = ib
                    }
                }
                Scraper.getImage(curInd.currentValue + 1) {
                    Log.i("OpenFunny", "cur imgn gotten")
                    var b = BitmapFactory.decodeByteArray(it, 0, it.size)
                    b = Bitmap.createBitmap(b, 0, 0, b.width, b.height - 20)
                    val ib = b?.asImageBitmap()
                    if (curInd.currentValue % 3 == 2) {
                        curImg = ib
                    } else if (curInd.currentValue % 3 == 0) {
                        curImg_n = ib
                    } else if (curInd.currentValue % 3 == 1) {
                        curImg_p = ib
                    }
                }
                Scraper.getImage(curInd.currentValue - 1) {
                    Log.i("OpenFunny", "cur imgp gotten")
                    var b = BitmapFactory.decodeByteArray(it, 0, it.size)
                    b = Bitmap.createBitmap(b, 0, 0, b.width, b.height - 20)
                    val ib = b?.asImageBitmap()
                    if (curInd.currentValue % 3 == 1) {
                        curImg = ib
                    } else if (curInd.currentValue % 3 == 2) {
                        curImg_n = ib
                    } else if (curInd.currentValue % 3 == 0) {
                        curImg_p = ib
                    }
                }
                Scraper.getComments(curInd.currentValue) {
                    Log.i("OpenFunny", "comments gotten")
                    comments = it
                }
            }
        }
    }

    val posMap = (0 until imgs).associateBy { -it * sizePx }

    val comment = remember {
        SwipeableState(false, tween())
    }
    val cmap = mapOf(0f to false, -1000f to true)
    Box(modifier = Modifier
        .swipeable(
            curInd,
            posMap,
            Orientation.Horizontal
        )
        .swipeable(comment, cmap, Orientation.Vertical))
    IFunnyImage(
        bitmap = curImg_p,
        pos = ((curInd.offset.value - 2 * sizePx) % (3 * sizePx) + sizePx).toInt(),
        map = posMap,
        swipe = curInd
    )
    IFunnyImage(
        bitmap = curImg,
        pos = ((curInd.offset.value - sizePx) % (3 * sizePx) + sizePx).toInt(),
        map = posMap,
        swipe = curInd
    )
    IFunnyImage(
        bitmap = curImg_n,
        pos = ((curInd.offset.value) % (3 * sizePx) + sizePx).toInt(),
        map = posMap,
        swipe = curInd
    )
    Column(modifier = Modifier.offset {
        IntOffset(
            ((curInd.offset.value) % (sizePx)).toInt(),
            2000 + comment.offset.value.toInt()
        )
    }
        ) {
        for (i in comments) {
//            Log.i("test", "comment")
            Scraper.text(s = i)
//            Text(
//                i,
//                modifier = mod
//            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun IFunnyImage(
    bitmap: ImageBitmap?,
    pos: Int,
    swipe: SwipeableState<Int>,
    map: Map<Float, Int>
) {
    if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "thing",
                modifier = Modifier
                    .offset { IntOffset(pos, 0) }
                    .fillMaxWidth())
    } else {
        Text(text = "Image loading",modifier = Modifier
            .offset { IntOffset(pos, 0) })
    }
}