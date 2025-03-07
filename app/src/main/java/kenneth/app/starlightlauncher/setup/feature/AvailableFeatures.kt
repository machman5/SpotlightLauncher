package kenneth.app.starlightlauncher.setup.feature

import androidx.annotation.StringRes
import kenneth.app.starlightlauncher.R

/**
 * Describes an available feature in the feature setup step.
 */
internal data class Feature(
    @StringRes
    val name: Int,

    @StringRes
    val description: Int,

    /**
     * The key of this feature in shared preferences.
     */
    @StringRes
    val key: Int,
)

/**
 * Defines available features that users can enable in the feature setup step.
 */
internal val AVAILABLE_FEATURES = listOf(
    Feature(
        name = R.string.feature_name_media_control,
        description = R.string.feature_description_media_control,
        key = R.string.pref_key_media_control_enabled,
    ),
    Feature(
        name = R.string.feature_name_note_widget,
        description = R.string.feature_description_note_widget,
        key = R.string.pref_key_note_widget_enabled,
    ),
    Feature(
        name = R.string.feature_name_unit_converter,
        description = R.string.feature_description_unit_converter,
        key = R.string.pref_key_unit_converter_enabled,
    ),
)
