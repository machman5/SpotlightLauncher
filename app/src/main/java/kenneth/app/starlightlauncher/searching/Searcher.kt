package kenneth.app.starlightlauncher.searching

import kenneth.app.starlightlauncher.api.SearchResult
import kenneth.app.starlightlauncher.extension.ExtensionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.schedule

private const val SEARCH_DELAY: Long = 500

typealias ResultCallback = (SearchResult) -> Unit

@Singleton
internal class Searcher @Inject constructor(
    private val extensionManager: ExtensionManager,
) {
    private val resultCallbacks = mutableListOf<ResultCallback>()

    private val searchCoroutineScopes = mutableListOf<CoroutineScope>()

    /**
     * Counts the number of search categories that have finished loading.
     * For example, if [Searcher] has finished searching through apps,
     * this counter is incremented by 1.
     */
    private var numberOfLoadedModules = 0

    private var searchTimer: TimerTask? = null

    /**
     * Adds a listener that is called when search result is available.
     */
    fun addSearchResultListener(callback: ResultCallback) {
        resultCallbacks.add(callback)
    }

    /**
     * Requests to search for all types after a set delay (currently set to 1 second)
     */
    fun requestSearch(keyword: String) {
        searchTimer?.cancel()
        searchTimer = Timer().schedule(SEARCH_DELAY) {
            performSearch(keyword)
        }
        numberOfLoadedModules = 0
    }

    /**
     * Cancels any pending search requests
     */
    fun cancelPendingSearch() {
        searchCoroutineScopes.forEach { it.cancel() }
        searchTimer?.cancel()
        numberOfLoadedModules = 0
    }

    private fun performSearch(keyword: String) {
        val searchRegex = Regex("[$keyword]", RegexOption.IGNORE_CASE)

        extensionManager.installedSearchModules.forEach { module ->
            CoroutineScope(Dispatchers.IO)
                .also { searchCoroutineScopes.add(it) }
                .launch {
                    val result = module.search(keyword, searchRegex)
                    numberOfLoadedModules++
                    resultCallbacks.forEach { cb -> cb(result) }
                }
        }
    }
}
