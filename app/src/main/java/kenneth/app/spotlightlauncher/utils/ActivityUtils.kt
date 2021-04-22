package kenneth.app.spotlightlauncher.utils

import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity

fun AppCompatActivity.addBackPressedCallback(callback: () -> Unit) {
    onBackPressedDispatcher.addCallback(this) { callback() }
}
