package kenneth.app.starlightlauncher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kenneth.app.starlightlauncher.R
import kenneth.app.starlightlauncher.api.preference.ObservablePreferences
import kenneth.app.starlightlauncher.api.utils.swap
import kenneth.app.starlightlauncher.extension.ExtensionManager
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

internal sealed class WidgetPreferenceChanged {
    data class WidgetOrderChanged(
        val fromPosition: Int,
        val toPosition: Int,
    ) : WidgetPreferenceChanged()

    data class NewAndroidWidgetAdded(
        val addedWidget: AddedWidget.AndroidWidget,

        val appWidgetProviderInfo: AppWidgetProviderInfo,
    ) : WidgetPreferenceChanged()
}

internal typealias WidgetPreferenceListener = (event: WidgetPreferenceChanged) -> Unit

@Singleton
internal class WidgetPreferenceManager @Inject constructor(
    @ApplicationContext context: Context,
    private val extensionManager: ExtensionManager,
    private val random: Random,
) : ObservablePreferences<WidgetPreferenceManager>(context) {
    private val appWidgetManager = AppWidgetManager.getInstance(context.applicationContext)

    val keys = WidgetPrefKeys(context)

    private var _addedWidgets =
        sharedPreferences.getString(keys.addedWidgets, null)
            ?.let {
                Json.decodeFromString<List<AddedWidget>>(it)
            }
            ?.toMutableList()
            ?: mutableListOf<AddedWidget>().apply {
                extensionManager.installedExtensions.forEach { ext ->
                    if (ext.widget != null) add(
                        AddedWidget.StarlightWidget(
                            random.nextInt(),
                            ext.name,
                        )
                    )
                }
            }

    /**
     * Maps internal IDs of added widgets to their corresponding position in the widget list.
     */
    private var addedWidgetPositions =
        _addedWidgets.foldIndexed(mutableMapOf<Int, Int>()) { i, m, widget ->
            m.apply { put(widget.id, i) }
        }

    private var addedWidgetsMap =
        _addedWidgets.fold(mutableMapOf<Int, AddedWidget>()) { m, widget ->
            m.apply { put(widget.id, widget) }
        }

    val addedWidgets
        get() = _addedWidgets.toList()

    override fun updateValue(sharedPreferences: SharedPreferences, key: String) {}

    fun changeWidgetOrder(fromPosition: Int, toPosition: Int) {
        val fromWidget = _addedWidgets[fromPosition]
        val toWidget = _addedWidgets[toPosition]
        _addedWidgets.swap(fromPosition, toPosition)
        addedWidgetPositions.swap(fromWidget.id, toWidget.id)
        saveAddedWidgets()
        setChanged()
        notifyObservers(WidgetPreferenceChanged.WidgetOrderChanged(fromPosition, toPosition))
    }

    fun addAndroidWidget(appWidgetId: Int, appWidgetProviderInfo: AppWidgetProviderInfo) {
        val newWidget = AddedWidget.AndroidWidget(
            appWidgetProviderInfo.provider,
            appWidgetId,
            appWidgetProviderInfo.minHeight,
        )
        _addedWidgets += newWidget
        addedWidgetsMap[newWidget.id] = newWidget
        addedWidgetPositions[newWidget.id] = _addedWidgets.lastIndex
        saveAddedWidgets()
        setChanged()
        notifyObservers(
            WidgetPreferenceChanged.NewAndroidWidgetAdded(
                newWidget,
                appWidgetProviderInfo
            )
        )
    }

    /**
     * Change the height of [addedWidget]. [newHeight] must be specified in dp.
     */
    fun changeWidgetHeight(addedWidget: AddedWidget, newHeight: Int) {
        if (addedWidget is AddedWidget.AndroidWidget) {
            addedWidgetPositions[addedWidget.id]?.let {
                val updated = addedWidget.copy(height = newHeight)
                _addedWidgets[it] = updated
                saveAddedWidgets()
            }
        }
    }

    fun removeAndroidWidget(appWidgetId: Int): Int? {
        val appWidgetProviderInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
        _addedWidgets.removeIf { it is AddedWidget.AndroidWidget && it.provider == appWidgetProviderInfo.provider }
        saveAddedWidgets()
        return addedWidgetPositions.remove(appWidgetId)
    }

    fun removeStarlightWidget(extensionName: String) {
        _addedWidgets.removeIf { it is AddedWidget.StarlightWidget && it.extensionName == extensionName }
        saveAddedWidgets()
    }

    fun addOnWidgetPreferenceChangedListener(listener: WidgetPreferenceListener) {
        addObserver { o, arg ->
            if (arg is WidgetPreferenceChanged) {
                listener(arg)
            }
        }
    }

    private fun saveAddedWidgets() {
        sharedPreferences.edit(commit = true) {
            putString(
                keys.addedWidgets,
                Json.encodeToString(_addedWidgets)
            )
        }
    }
}

class WidgetPrefKeys(context: Context) {
    val widgetOrder by lazy { context.getString(R.string.pref_key_widget_order) }

    val addedWidgets by lazy { context.getString(R.string.pref_key_added_widgets) }
}
