package kenneth.app.spotlightlauncher

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import dagger.hilt.android.AndroidEntryPoint
import kenneth.app.spotlightlauncher.prefs.SettingsActivity
import kenneth.app.spotlightlauncher.searching.SearchType
import kenneth.app.spotlightlauncher.searching.Searcher
import kenneth.app.spotlightlauncher.searching.display_adapters.ResultAdapter
import kenneth.app.spotlightlauncher.utils.BlurHandler
import kenneth.app.spotlightlauncher.utils.KeyboardAnimationCallback
import kenneth.app.spotlightlauncher.utils.calculateBitmapBrightness
import kenneth.app.spotlightlauncher.utils.viewToBitmap
import kenneth.app.spotlightlauncher.views.BlurView
import kenneth.app.spotlightlauncher.views.DateTimeView
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var searcher: Searcher

    @Inject
    lateinit var resultAdapter: ResultAdapter

    @Inject
    lateinit var blurHandler: BlurHandler

    @Inject
    lateinit var wallpaperManager: WallpaperManager

    @Inject
    lateinit var appState: AppState

    private lateinit var rootView: ConstraintLayout
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var keyboardAnimationCallback: KeyboardAnimationCallback
    private lateinit var sectionCardList: LinearLayout
    private lateinit var searchBox: EditText
    private lateinit var searchBoxContainer: LinearLayout
    private lateinit var searchBoxBlurBackground: BlurView
    private lateinit var wallpaperImage: ImageView
    private lateinit var dateTimeView: DateTimeView

    private lateinit var searchBoxAnimationInterpolator: PathInterpolator

    private var isSearchScreenActive = false
    private var isDarkModeActive = false
    private var searchBoxContainerPaddingPx: Int = 0
    private var statusBarHeight: Int = 0

    /**
     * Right before requesting a permission, it is stored in this variable, so that when
     * the request result comes back, we know what permission is being requested, and we
     * can hide the corresponding button.
     */
    private var requestedPermission: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        updateLauncherTheme()
        setTheme(appState.themeStyleId)
        setContentView(R.layout.activity_main)

        appState.apply {
            screenWidth = resources.displayMetrics.widthPixels
            screenHeight = resources.displayMetrics.heightPixels
        }

        rootView = findViewById(R.id.root)
        sectionCardList = findViewById(R.id.section_card_list)
        searchBox = findViewById(R.id.search_box)
        searchBoxContainer = findViewById(R.id.search_box_container)
        searchBoxBlurBackground = findViewById(R.id.search_box_blur_background)
        wallpaperImage = findViewById(R.id.wallpaper_image)
        dateTimeView = findViewById<DateTimeView>(R.id.date_time_view)

        // enable edge-to-edge app experience

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            rootView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        } else {
            window.setDecorFitsSystemWindows(false)
        }

        searchBoxContainerPaddingPx =
            resources.getDimensionPixelSize(R.dimen.search_box_container_padding)

        requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission(),
                ::handlePermissionResult
            )

        isDarkModeActive =
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) resources.configuration.isNightModeActive
            else resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        attachListeners()
        askForReadExternalStoragePermission()
    }

    override fun onBackPressed() {
        if (searchBox.text.toString() == "") {
            toggleSearchBoxAnimation(isActive = false)
            searchBox.clearFocus()
        } else {
            super.onBackPressed()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        isDarkModeActive =
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) newConfig.isNightModeActive
            else newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        if (isSearchScreenActive) {
            if (isDarkModeActive) {
                disableLightStatusBar()
            } else {
                enableLightStatusBar()
            }
        }
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
        resultAdapter.cleanup()
    }

    /**
     * a temporary function to ask for READ_EXTERNAL_STORAGE permission
     */
    private fun askForReadExternalStoragePermission() {
        // TODO: a temporary function to ask for READ_EXTERNAL_STORAGE permission
        // TODO: in prod this should be done during setup
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestedPermission = Manifest.permission.READ_EXTERNAL_STORAGE
            requestPermissionLauncher.launch(requestedPermission)
        }
    }

    private fun attachListeners() {
        findViewById<Button>(R.id.open_settings_button)
            .setOnClickListener { openSettings() }

        with(searchBox) {
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) searcher.refreshAppList()
                onSearchBoxFocusChanged(hasFocus)
            }

            setOnEditorActionListener { _, actionID, _ ->
                if (actionID == EditorInfo.IME_ACTION_DONE) {
                    clearFocus()
                }
                false
            }

            addTextChangedListener { text -> handleSearchQuery(text) }
        }

        with(rootView) {
            setOnApplyWindowInsetsListener { _, insets ->
                statusBarHeight =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        insets
                            .getInsets(WindowInsets.Type.systemBars())
                            .top
                    else
                        insets.systemWindowInsetTop
                insets
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            keyboardAnimationCallback = KeyboardAnimationCallback(this).also {
                rootView.setWindowInsetsAnimationCallback(it)
            }
        }

        with(searcher) {
            setSearchResultListener { result, type ->
                runOnUiThread {
                    resultAdapter.displayResult(result, type)
                }
            }

            setWebResultListener { result ->
                runOnUiThread {
                    resultAdapter.displayWebResult(result)
                }
            }
        }

        rootView.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    updateWallpaper()
                    startBlurs()
                }
            }
        )
    }

    private fun openSettings() {
        val settingsIntent = Intent(this, SettingsActivity::class.java)
        startActivity(settingsIntent)
    }

    /**
     * Update the current launcher theme based on the brightness of the current wallpaper.
     * If the current wallpaper is bright, LightLauncherTheme is used.
     * Otherwise, DarkLauncherTheme is used.
     */
    private fun updateLauncherTheme() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            val wallpaperBitmap = wallpaperManager.fastDrawable.toBitmap()
            val wallpaperBrightness = calculateBitmapBrightness(wallpaperBitmap)

            appState.theme =
                if (wallpaperBrightness >= 128) AppState.Theme.LIGHT
                else AppState.Theme.DARK

            if (appState.isInitialStart) {
                appState.isInitialStart = false
            } else {
                // restart activity after changing theme
                restartActivity()
            }
        }
    }

    /**
     * Restarts the current activity.
     * Credit to [this gist](https://gist.github.com/alphamu/f2469c28e17b24114fe5)
     */
    private fun restartActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun handlePermissionResult(isGranted: Boolean) {
        if (!isGranted) return

        val query = findViewById<EditText>(R.id.search_box).text.toString()

        when (requestedPermission) {
            Manifest.permission.READ_EXTERNAL_STORAGE -> searcher.requestSpecificSearch(
                SearchType.FILES,
                query
            )
        }
    }

    private fun onSearchBoxFocusChanged(hasFocus: Boolean) {
        if (searchBox.text.isBlank()) {
            toggleSearchBoxAnimation(isActive = hasFocus)
        }
    }

    private fun toggleSearchBoxAnimation(isActive: Boolean) {
        if (!::searchBoxAnimationInterpolator.isInitialized) {
            searchBoxAnimationInterpolator = PathInterpolator(0.16f, 1f, 0.3f, 1f)
        }

        val searchBoxAnimation = ObjectAnimator.ofFloat(
            dateTimeView,
            "layoutWeight",
            if (isActive) 0f else 1f,
        ).apply {
            interpolator = searchBoxAnimationInterpolator
            duration = 500
        }

        val searchBoxPaddingAnimation = ValueAnimator.ofInt(
            if (isActive) 0 else statusBarHeight,
            if (isActive) statusBarHeight else 0,
        ).apply {
            interpolator = searchBoxAnimationInterpolator
            duration = 500

            addUpdateListener { updatedAnimation ->
                searchBoxContainer.updatePadding(
                    top = updatedAnimation.animatedValue as Int
                )
            }
        }

        AnimatorSet().apply {
            play(searchBoxAnimation)
                .with(searchBoxPaddingAnimation)

            start()
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
            rootView.systemUiVisibility =
                rootView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
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
        } else if (Build.VERSION.SDK_INT in (Build.VERSION_CODES.M..Build.VERSION_CODES.Q) && !isDarkModeActive) {
            rootView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }

    private fun handleSearchQuery(query: Editable?) {
        if (query == null || query.isBlank()) {
            searcher.cancelPendingSearch()
            resultAdapter.hideResult()
        } else {
            searcher.requestSearch(query.toString())
        }
    }

    /**
     * Gets the current wallpaper and renders it to wallpaperImage
     */
    private fun updateWallpaper() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            val wallpaper = wallpaperManager.fastDrawable
            wallpaperImage.setImageDrawable(wallpaper)
            blurHandler.changeWallpaper(viewToBitmap(wallpaperImage))
        }
    }

    private fun startBlurs() {
        searchBoxBlurBackground.startBlur()
    }
}
