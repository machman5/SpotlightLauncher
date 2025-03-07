package kenneth.app.starlightlauncher.appsearchmodule

import android.content.Context
import android.content.pm.LauncherApps
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import kenneth.app.starlightlauncher.api.SearchResult
import kenneth.app.starlightlauncher.api.StarlightLauncherApi
import kenneth.app.starlightlauncher.api.preference.PreferencesChanged
import kenneth.app.starlightlauncher.api.view.SearchResultAdapter
import kenneth.app.starlightlauncher.appsearchmodule.databinding.AppSearchResultCardBinding

/**
 * Defines how many apps are shown when the app grid is displayed initially.
 */
private const val INITIAL_ITEM_COUNT = 10

class AppSearchResultAdapter(
    private val context: Context,
    private val launcher: StarlightLauncherApi
) :
    SearchResultAdapter {
    private lateinit var appList: AppList
    private lateinit var currentViewHolder: AppSearchResultViewHolder

    private var appGridAdapter: AppGridAdapter? = null
    private val prefs = AppSearchModulePreferences.getInstance(context)

    init {
        prefs.addOnPreferenceChangedListener(::onPreferencesChanged)
    }

    override fun onCreateViewHolder(parent: ViewGroup): AppSearchResultViewHolder {
        val binding =
            AppSearchResultCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                .apply {
                    appSearchResultCard.blurWith(launcher.blurHandler)
                }
        return AppSearchResultViewHolder(binding)
    }

    override fun onBindSearchResult(
        holder: SearchResultAdapter.ViewHolder,
        searchResult: SearchResult
    ) {
        if (holder is AppSearchResultViewHolder && searchResult is AppSearchModule.Result) {
            onBindSearchResult(holder, searchResult)
        }
    }

    private fun onPreferencesChanged(event: AppSearchModulePreferenceChanged) {
        when (event) {
            is AppSearchModulePreferenceChanged.AppLabelVisibilityChanged -> {
                if (event.isVisible) {
                    appGridAdapter?.showAppLabels()
                } else {
                    appGridAdapter?.hideAppLabels()
                }
            }
            else -> {}
        }
    }

    private fun onBindSearchResult(
        holder: AppSearchResultViewHolder,
        searchResult: AppSearchModule.Result,
    ) {
        currentViewHolder = holder

        with(holder.binding) {
            if (searchResult.apps.isEmpty()) {
                appGrid.isVisible = false
                showMoreButton.isVisible = false
                noResultLabel.isVisible = true
            } else {
                appList = searchResult.apps

                val initialAppGridItems =
                    if (appList.size > INITIAL_ITEM_COUNT)
                        appList
                            .subList(0, INITIAL_ITEM_COUNT)
                            .toMutableList()
                    else
                        appList.toMutableList()

                val appGridAdapter =
                    AppGridAdapter(
                        context,
                        initialAppGridItems,
                        launcher,
                        prefs.shouldShowAppNames
                    )
                        .also { appGridAdapter = it }

                appGrid.apply {
                    layoutManager = GridLayoutManager(context, 5)
                    adapter = appGridAdapter
                }

                showMoreButton.apply {
                    isVisible = appList.size > INITIAL_ITEM_COUNT
                    setOnClickListener { showMoreApps() }
                }

                appGrid.isVisible = true
                noResultLabel.isVisible = false
            }
        }
    }

    private fun showMoreApps() {
        appGridAdapter?.let {
            it.showMore()
            currentViewHolder.binding
                .showMoreButton.isVisible = it.hasMore()
        }
    }
}

class AppSearchResultViewHolder(val binding: AppSearchResultCardBinding) :
    SearchResultAdapter.ViewHolder {
    override val rootView = binding.root
}