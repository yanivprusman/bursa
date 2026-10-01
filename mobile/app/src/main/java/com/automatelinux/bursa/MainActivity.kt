package com.automatelinux.bursa

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.automatelinux.bursa.data.KeyValueStore
import com.automatelinux.bursa.data.Platform
import java.io.File
import java.security.MessageDigest

// Thin Android launcher — all UI lives in the shared commonMain App() composable.
class MainActivity : ComponentActivity() {

    /** True between onStart and onStop; the shared code only polls the server while it is. */
    private var active by mutableStateOf(false)

    private val platform = object : Platform {
        override val appVersion: String = BuildConfig.VERSION_NAME

        override fun openUrl(url: String): Boolean = try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /**
     * One small file per key in app-private storage: the user's list and holdings, and the
     * last good server responses. Written to a temp file and renamed, so a crash mid-write
     * never leaves a half-written list.
     */
    private val store by lazy {
        val dir = File(filesDir, "store").apply { mkdirs() }
        object : KeyValueStore {
            private fun file(key: String): File {
                val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
                return File(dir, "$hash.json")
            }

            override fun get(key: String): String? = file(key).takeIf { it.exists() }?.readText()

            override fun put(key: String, value: String) {
                val f = file(key)
                val tmp = File(dir, f.name + ".tmp")
                tmp.writeText(value)
                if (!tmp.renameTo(f)) throw IllegalStateException("could not save ${f.name}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Config changes are handled in-process (see the manifest), so re-apply the system
            // bar icon colours whenever the theme flips.
            val dark = isSystemInDarkTheme()
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            App(
                baseUrl = BuildConfig.API_BASE_URL,
                store = store,
                platform = platform,
                active = active,
                backHandler = { enabled, onBack -> BackHandler(enabled, onBack) },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        active = true
    }

    override fun onStop() {
        active = false
        super.onStop()
    }
}
