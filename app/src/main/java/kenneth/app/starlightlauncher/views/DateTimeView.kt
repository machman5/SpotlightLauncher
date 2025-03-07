package kenneth.app.starlightlauncher.views

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint
import kenneth.app.starlightlauncher.R
import kenneth.app.starlightlauncher.api.LatLong
import kenneth.app.starlightlauncher.api.OpenWeatherApi
import kenneth.app.starlightlauncher.databinding.DateTimeViewBinding
import kenneth.app.starlightlauncher.prefs.datetime.DateTimePreferenceManager
import kenneth.app.starlightlauncher.utils.activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
internal class DateTimeView(context: Context, attrs: AttributeSet) :
    LinearLayout(context, attrs), LifecycleEventObserver {
    @Inject
    lateinit var locale: Locale

    @Inject
    lateinit var dateTimePreferenceManager: DateTimePreferenceManager

    @Inject
    lateinit var openWeatherApi: OpenWeatherApi

    @Inject
    lateinit var locationManager: LocationManager

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    private val timeTickIntentFilter = IntentFilter(Intent.ACTION_TIME_TICK)
    private val weatherApiScope = CoroutineScope(Dispatchers.IO)

    private val weatherLocationCriteria = Criteria().apply {
        accuracy = Criteria.ACCURACY_COARSE
        powerRequirement = Criteria.POWER_LOW
    }

    /**
     * A BroadcastReceiver that receives broadcast of Intent.ACTION_TIME_TICK.
     * Must register this receiver in activity, or the time will not update.
     */
    private val timeTickBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context != null && intent?.action == Intent.ACTION_TIME_TICK) {
                updateTime()
            }
        }
    }

    private val timeFormat
        get() = SimpleDateFormat(
            if (dateTimePreferenceManager.shouldUse24HrClock)
                "HH:mm"
            else "h:mm a",
            locale
        )

    private val dateFormat = SimpleDateFormat("MMM d", locale)

    private val binding: DateTimeViewBinding

    private val separator: TextView

    private var currentWeatherLocation: LatLong

    init {
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
            1f
        )
        gravity = Gravity.CENTER
        orientation = VERTICAL
        binding = DateTimeViewBinding.inflate(LayoutInflater.from(context), this, true)
        separator = binding.dateTimeWeatherSeparator
        currentWeatherLocation = dateTimePreferenceManager.weatherLocation

        updateTime()
        showWeather()
        applyTextShadow()
        activity?.lifecycle?.addObserver(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(::onSharedPreferencesChanged)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (source is AppCompatActivity) {
            when (event) {
                Lifecycle.Event.ON_RESUME -> onResume()
                Lifecycle.Event.ON_PAUSE -> onPause()
                else -> {
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        unregisterTimeTickListener()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registerTimeTickListener()
    }

    private fun applyTextShadow() {
        val textColor = binding.clock.currentTextColor
        val shadowColor = Color.argb(
            resources.getInteger(R.integer.text_shadow_opacity_date_time_view),
            255 - Color.red(textColor),
            255 - Color.green(textColor),
            255 - Color.blue(textColor),
        )

        with(binding) {
            val radius =
                resources.getInteger(R.integer.text_shadow_radius_date_time_view).toFloat()
            val dx = resources.getInteger(R.integer.text_shadow_dx_date_time_view).toFloat()
            val dy = resources.getInteger(R.integer.text_shadow_dy_date_time_view).toFloat()

            clock.setShadowLayer(radius, dx, dy, shadowColor)
            date.setShadowLayer(radius, dx, dy, shadowColor)
            dateTimeWeatherSeparator.setShadowLayer(radius, dx, dy, shadowColor)
            temp.setShadowLayer(radius, dx, dy, shadowColor)
        }
    }

    private fun onResume() {
        registerTimeTickListener()
        updateTime()
        showWeather()
    }

    private fun onPause() {
        unregisterTimeTickListener()
    }

    private fun onSharedPreferencesChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == dateTimePreferenceManager.useAutoWeatherLocationKey) {
            toggleLocationUpdate()
        }
    }

    private fun registerTimeTickListener() {
        context.registerReceiver(timeTickBroadcastReceiver, timeTickIntentFilter)
    }

    private fun unregisterTimeTickListener() {
        try {
            context.unregisterReceiver(timeTickBroadcastReceiver)
        } catch (ex: IllegalArgumentException) {
        }
    }

    private fun showWeather() {
        if (dateTimePreferenceManager.shouldShowWeather) {
            if (dateTimePreferenceManager.shouldUseAutoWeatherLocation && ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                locationManager.getBestProvider(weatherLocationCriteria, true)?.let {
                    locationManager.getLastKnownLocation(it)?.let { loc ->
                        currentWeatherLocation = LatLong(loc)
                        loadWeather()
                    }
                }
            } else {
                loadWeather()
            }
        } else {
            with(binding) {
                temp.isVisible = false
                weatherIcon.isVisible = false
                dateTimeWeatherSeparator.isVisible = false
            }
        }
    }

    private fun loadWeather() {
        weatherApiScope.launch {
            val weather = try {
                openWeatherApi.run {
                    latLong = currentWeatherLocation
                    unit = dateTimePreferenceManager.weatherUnit
                    getCurrentWeather()
                }
            } catch (ex: Exception) {
                Log.e("hub", "$ex")
                return@launch
            }

            val isWeatherAvailable = weather != null

            activity?.runOnUiThread {
                binding.temp.isVisible = isWeatherAvailable
                separator.isVisible = isWeatherAvailable
                binding.weatherIcon.isVisible = isWeatherAvailable

                if (isWeatherAvailable) {
                    binding.temp.text = context.getString(
                        R.string.date_time_temperature_format,
                        weather!!.main.temp,
                        openWeatherApi.unit.symbol
                    )

                    Glide
                        .with(context)
                        .load(weather.weather[0].iconURL)
                        .into(binding.weatherIcon)

                    binding.weatherIcon.contentDescription = weather.weather[0].description
                    binding.dateTimeWeatherSeparator.isVisible = true
                }
            }
        }
    }

    /**
     * Enables or disables auto update of weather location.
     */
    private fun toggleLocationUpdate() {
        if (dateTimePreferenceManager.shouldUseAutoWeatherLocation && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val locationCriteria = Criteria().apply {
                accuracy = Criteria.ACCURACY_COARSE
                powerRequirement = Criteria.POWER_LOW
            }

            locationManager.getBestProvider(locationCriteria, true)
                ?.let {
                    locationManager.getLastKnownLocation(it)?.let { loc ->
                        currentWeatherLocation = LatLong(loc)
                    }
                    loadWeather()
                    locationManager.requestLocationUpdates(
                        it,
                        dateTimePreferenceManager.autoWeatherLocationCheckFrequency,
                        1000F,
                        ::onLocationUpdated
                    )
                }
        } else {
            currentWeatherLocation = dateTimePreferenceManager.weatherLocation
            locationManager.removeUpdates(::onLocationUpdated)
        }
    }

    private fun onLocationUpdated(location: Location) {
        currentWeatherLocation = LatLong(location)
        loadWeather()
    }

    private fun updateTime() {
        val currentTime = Calendar.getInstance().time

        binding.clock.text = timeFormat.format(currentTime)
        binding.date.text = dateFormat.format(currentTime)
    }
}
