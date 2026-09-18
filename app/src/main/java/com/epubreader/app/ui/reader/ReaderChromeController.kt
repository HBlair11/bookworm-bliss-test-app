package com.epubreader.app.ui.reader

import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.SeekBar
import kotlin.math.roundToInt

/**
 * ReaderChromeController — Extracted from ReaderActivity (Phase 6).
 *
 * Provides chrome bar visibility toggle and seek-bar interaction logic.
 * State (chromeVisible, userSeeking, etc.) remains in the Activity to avoid
 * split-brain issues with other controllers that read it. The controller
 * reads/writes state through the [State] interface and fires navigation/
 * progress actions through [Callbacks].
 *
 * @param config     View references and window.
 * @param state      Read/write access to chrome and seek state.
 * @param callbacks  Navigation, progress, and history callbacks.
 */
class ReaderChromeController(
    val config: Config,
    val state: State,
    val callbacks: Callbacks,
) {

    data class Config(
        val topBar: View,
        val bottomBar: View,
        val tvPageIndicator: View,
        val seekChapter: SeekBar,
        val window: Window,
        val keepScreenOn: Boolean,
    )

    interface State {
        var chromeVisible: Boolean
        var userSeeking: Boolean
        var pendingSeekProgress: Int?
        var manualSeekTouch: Boolean
        var manualSeekFinished: Boolean
        val overlayVisible: Boolean
        val perPageSeekerActive: Boolean
        val spineIndex: Int
        val restoringHistoryLocation: Boolean
    }

    interface Callbacks {
        fun onPollProgress()
        fun onInvalidatePendingPolls()
        fun onUpdatePageIndicator()
        fun onUpdateHistoryUi()
        fun onCaptureReaderLocation(): ReaderLocation?
        fun onLocationForAbsolutePage(absolute: Int): ReaderLocation?
        fun onSameLocation(a: ReaderLocation, b: ReaderLocation): Boolean
        fun onPushHistory(location: ReaderLocation)
        fun onSeekToAbsolutePage(absolute: Int)
        fun onGoToSpine(spine: Int)
    }

    // ---- Chrome visibility ----

    fun toggleChrome() {
        state.chromeVisible = !state.chromeVisible
        config.topBar.visibility = if (state.chromeVisible) View.VISIBLE else View.GONE
        config.bottomBar.visibility = if (state.chromeVisible) View.VISIBLE else View.GONE
        callbacks.onUpdateHistoryUi()
        config.tvPageIndicator.visibility =
            if (!state.chromeVisible && !state.overlayVisible) View.VISIBLE else View.GONE
        if (state.chromeVisible) config.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else if (!config.keepScreenOn) {
            config.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        callbacks.onUpdatePageIndicator()
    }

    fun setChromeVisible(visible: Boolean) {
        if (state.chromeVisible == visible) return
        toggleChrome()
    }

    // ---- Seek bar ----

    fun progressForSeekTouch(sb: SeekBar, x: Float): Int {
        val usableWidth = (sb.width - sb.paddingLeft - sb.paddingRight).coerceAtLeast(1)
        val localX = (x - sb.paddingLeft).coerceIn(0f, usableWidth.toFloat())
        val fraction = localX / usableWidth.toFloat()
        return (fraction * sb.max).roundToInt().coerceIn(0, sb.max)
    }

    fun finishSeek(target: Int?) {
        val resolved = target ?: sbProgressFallback()
        state.pendingSeekProgress = null
        state.userSeeking = false
        if (resolved == null) {
            callbacks.onPollProgress()
            return
        }

        val current = callbacks.onCaptureReaderLocation()
        val targetLocation = callbacks.onLocationForAbsolutePage(resolved)
        if (!state.restoringHistoryLocation && current != null && targetLocation != null) {
            if (!callbacks.onSameLocation(current, targetLocation)) {
                callbacks.onPushHistory(current)
            }
        }

        if (state.perPageSeekerActive) {
            callbacks.onSeekToAbsolutePage(resolved)
        } else {
            callbacks.onGoToSpine(resolved)
        }

        callbacks.onInvalidatePendingPolls()
    }

    fun sbProgressFallback(): Int? =
        config.seekChapter.progress.takeIf { config.seekChapter.max >= 0 }
}
