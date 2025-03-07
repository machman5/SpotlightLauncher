package kenneth.app.starlightlauncher

import android.Manifest
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.appwidget.AppWidgetHost
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import kenneth.app.starlightlauncher.api.StarlightLauncherApi
import kenneth.app.starlightlauncher.api.utils.BlurHandler
import kenneth.app.starlightlauncher.databinding.ActivityMainBinding
import kenneth.app.starlightlauncher.extension.ExtensionManager
import kenneth.app.starlightlauncher.prefs.appearance.AppearancePreferenceManager
import kenneth.app.starlightlauncher.prefs.appearance.InstalledIconPack
import kenneth.app.starlightlauncher.searching.Searcher
import kenneth.app.starlightlauncher.utils.BindingRegister
import kenneth.app.starlightlauncher.utils.calculateDominantColor
import javax.inject.Inject
import kotlin.concurrent.thread

/**
 * Called when back button is pressed.
 * If handler returns true, subsequent handlers will not be called.
 */
typealias BackPressHandler = () -> Boolean

@Module
@InstallIn(ActivityComponent::class)
internal object MainActivityModule {
    @Provides
    fun provideMainActivity(@ActivityContext context: Context): MainActivity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is MainActivity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}

@AndroidEntryPoint
internal class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var searcher: Searcher

    @Inject
    lateinit var extensionManager: ExtensionManager

    @Inject
    lateinit var blurHandler: BlurHandler

    @Inject
    lateinit var appState: AppState

    @Inject
    lateinit var appearancePreferenceManager: AppearancePreferenceManager

    @Inject
    lateinit var inputMethodManager: InputMethodManager

    @Inject
    lateinit var permissionHandler: PermissionHandler

    @Inject
    lateinit var launcherApi: StarlightLauncherApi

    @Inject
    internal lateinit var appWidgetHost: AppWidgetHost

    private lateinit var binding: ActivityMainBinding

    private var currentWallpaper: Bitmap? = null

    private var backPressedCallbacks = mutableListOf<BackPressHandler>()

    private var isDarkModeActive = false

    internal fun addBackPressListener(handler: BackPressHandler) {
        backPressedCallbacks.add(handler)
    }

    override fun onStart() {
        super.onStart()
        appWidgetHost.startListening()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        getCurrentWallpaper()
        updateAdaptiveColors()

        launcherApi.let {
            if (it is StarlightLauncherApiImpl) it.setContext(this)
        }

        extensionManager.loadExtensions()

        // enable edge-to-edge app experience
        WindowCompat.setDecorFitsSystemWindows(window, false)

        appState.apply {
            screenWidth = resources.displayMetrics.widthPixels
            screenHeight = resources.displayMetrics.heightPixels
        }

        binding = ActivityMainBinding.inflate(layoutInflater).also {
            BindingRegister.apply {
                activityMainBinding = it
            }
        }
        isDarkModeActive =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                resources.configuration.isNightModeActive
            else
                resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        setContentView(binding.root)
        setWallpaper()
        appearancePreferenceManager.iconPack.let {
            if (it is InstalledIconPack) it.load()
        }
        permissionHandler.handlePermissionRequestsForActivity(this)
        attachListeners()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        isDarkModeActive =
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) newConfig.isNightModeActive
            else newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        if (binding.widgetsPanel.isExpanded) {
            if (isDarkModeActive) {
                disableLightStatusBar()
            } else {
                enableLightStatusBar()
            }
        }
    }

    override fun onBackPressed() {
        val isHandled = backPressedCallbacks.fold(false) { _, cb -> cb() }
        if (!isHandled) {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        checkWallpaper()
    }

    override fun onStop() {
        cleanup()
        super.onStop()
    }

    override fun onPause() {
        cleanup()
        super.onPause()
    }

    private fun cleanup() {
        appWidgetHost.stopListening()
    }

    private fun attachListeners() {
        with(binding.root) {
            setOnApplyWindowInsetsListener { _, insets ->
                appState.statusBarHeight = WindowInsetsCompat.toWindowInsetsCompat(insets)
                    .getInsets(WindowInsetsCompat.Type.systemBars()).top

                insets
            }
        }
    }

    /**
     * Update the current adaptive color scheme by finding the dominant color of the current wallpaper.
     */
    private fun updateAdaptiveColors() {
        val currentWallpaper = this.currentWallpaper
        if (
            currentWallpaper != null &&
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            appState.apply {
                val adaptiveBackgroundColor = currentWallpaper.calculateDominantColor()
                val white = getColor(android.R.color.white)

                setTheme(
                    if (ColorUtils.calculateContrast(white, adaptiveBackgroundColor) > 1.5f)
                        R.style.DarkLauncherTheme
                    else
                        R.style.LightLauncherTheme
                )
            }
        } else {
            setTheme(R.style.DarkLauncherTheme)
        }
    }

    @SuppressLint("InlinedApi")
    private fun enableLightStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window
                .insetsController
                ?.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
        } else if (Build.VERSION.SDK_INT in (Build.VERSION_CODES.M..Build.VERSION_CODES.Q) && !isDarkModeActive) {
            binding.root.systemUiVisibility =
                binding.root.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    private fun disableLightStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window
                .insetsController
                ?.setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
        } else
            binding.root.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    /**
     * Checks if the wallpaper is updated. If it is, recreate activity.
     */
    private fun checkWallpaper() {
        val currentWallpaper = this.currentWallpaper ?: return
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            thread(start = true) {
                val wallpaper = WallpaperManager.getInstance(this).fastDrawable.toBitmap()
                if (!wallpaper.sameAs(currentWallpaper)) {
                    runOnUiThread { recreate() }
                }
            }
        }
    }

    private fun getCurrentWallpaper() {
        if (
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            currentWallpaper = WallpaperManager.getInstance(this).fastDrawable.toBitmap()
        }
    }

    private fun setWallpaper() {
        currentWallpaper?.let {
            binding.wallpaperImage.setImageBitmap(it)
            blurHandler.changeWallpaper(it)
        }
    }
}
