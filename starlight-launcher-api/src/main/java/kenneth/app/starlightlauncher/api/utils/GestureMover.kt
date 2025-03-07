package kenneth.app.starlightlauncher.api.utils

import android.util.Log
import android.view.MotionEvent
import android.view.View
import java.lang.IllegalArgumentException
import kotlin.math.max

/**
 * How far should a gesture travel in order to activate certain actions
 */
const val GESTURE_ACTION_THRESHOLD = 100

/**
 * [GestureMover] moves [GestureMover.targetView] vertically according to the recorded motion events.
 */
class GestureMover {
    lateinit var targetView: View

    var gestureDelta = 0f

    var isGestureActive = false
        private set

    /**
     * The minimum y position [GestureMover.targetView] should have.
     *
     */
    var minY: Float? = null

    /**
     * The initial y position of the active gesture
     */
    var initialY = 0f
        private set

    private var viewInitialY = 0f

    private var lastY = 0f

    /**
     * Records the initial motion event of the gesture
     */
    fun recordInitialEvent(event: MotionEvent) {
        initialY = event.rawY
        lastY = initialY
        isGestureActive = true
        viewInitialY = targetView.y
    }

    /**
     * Records the ongoing [MotionEvent] of the gesture
     */
    fun addMotionMoveEvent(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_MOVE)
            throw IllegalArgumentException("The given motion event must be ACTION_MOVE")

        val delta = event.rawY - lastY
        val minY = this.minY

        if (minY != null) {
            targetView.translationY = max(targetView.translationY + delta, minY)
        } else {
            targetView.translationY += delta
        }

        lastY = event.rawY
    }

    /**
     * Records the final [MotionEvent] of the gesture
     */
    fun addMotionUpEvent(event: MotionEvent) {
        gestureDelta = targetView.y - viewInitialY
        isGestureActive = false
    }

    /**
     * Resets all parameters.
     */
    fun reset() {
        isGestureActive = false
        initialY = 0f
        viewInitialY = 0f
        gestureDelta = 0f
        minY = null
    }
}