package kenneth.app.starlightlauncher.filesearchmodule.activity

import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import kenneth.app.starlightlauncher.api.preference.SettingsActivity
import kenneth.app.starlightlauncher.filesearchmodule.R
import kenneth.app.starlightlauncher.filesearchmodule.fragment.SearchModuleSettingsFragment

class SearchModuleSettingsActivity : SettingsActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    override fun createPreferenceFragment() = SearchModuleSettingsFragment()
}