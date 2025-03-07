package kenneth.app.starlightlauncher.views

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import kenneth.app.starlightlauncher.api.utils.swap

/**
 * A [LinearLayout] that provides methods for inserting children in a given order.
 */
internal abstract class OrderedInsertionLinearLayout(context: Context, attrs: AttributeSet?) :
    LinearLayout(context, attrs) {
    /**
     * A list of all containers that may or may not be shown in [OrderedInsertionLinearLayout].
     */
    protected abstract val allContainers: MutableList<Container?>

    /**
     * Contains [Container]s that are currently in layout.
     */
    private val containersInLayout = mutableMapOf<Int, Container>()

    /**
     * Get the [Container] at the given position, or null if it has not been created yet.
     */
    protected fun containerAt(position: Int) = allContainers[position]

    /**
     * Create a new [Container] at the given position.
     */
    protected fun createContainerAt(position: Int) =
        Container(context)
            .apply {
                id = generateViewId()
                this.position = position
            }
            .also {
                allContainers[position] = it
                insertContainer(it)
            }

    /**
     * Swaps two [Container]s.
     */
    protected fun swapContainers(fromPosition: Int, toPosition: Int) {
        val fromContainer = allContainers[fromPosition]
        val toContainer = allContainers[toPosition]
        when {
            fromContainer != null && toContainer != null -> {
                fromContainer.position = toPosition
                toContainer.position = fromPosition

                allContainers.swap(fromPosition, toPosition)
                containersInLayout.swap(fromContainer.id, toContainer.id)
                removeView(fromContainer)
                removeView(toContainer)
                insertContainer(fromContainer)
                insertContainer(toContainer)
            }
            fromContainer != null && toContainer == null -> {
                fromContainer.position = toPosition
                allContainers[toPosition] = fromContainer
                allContainers[fromPosition] = null

                removeView(fromContainer)
                insertContainer(fromContainer)
            }
            fromContainer == null && toContainer != null -> {
                toContainer.position = fromPosition
                allContainers[fromPosition] = toContainer
                allContainers[toPosition] = null

                removeView(toContainer)
                insertContainer(toContainer)
            }
        }
    }

    /**
     * Inserts the given [Container] at a suitable position.
     */
    private fun insertContainer(container: Container) {
        if (!containersInLayout.contains(container.id)) {
            containersInLayout[container.id] = container
            if (childCount == 1) {
                val other = containersInLayout[getChildAt(0).id]!!
                when {
                    other.position > container.position ->
                        addView(container, 0)
                    other.position < container.position ->
                        addView(container)
                }
            } else {
                for (i in 0 until childCount - 1) {
                    val cur = containersInLayout[getChildAt(i).id]!!
                    val next = containersInLayout[getChildAt(i + 1).id]!!
                    when {
                        container.position < cur.position && container.position < next.position -> {
                            addView(container, i)
                            return
                        }
                        container.position > cur.position && container.position < next.position -> {
                            addView(container, i + 1)
                            return
                        }
                    }
                }
                addView(container)
            }
        }
    }

    /**
     * A [LinearLayout] that contains all children of [OrderedInsertionLinearLayout].
     * This [Container] contains the position of each child.
     */
    class Container(context: Context) : LinearLayout(context) {
        /**
         * The position at which this container should appear in [OrderedInsertionLinearLayout].
         */
        var position = 0
    }
}