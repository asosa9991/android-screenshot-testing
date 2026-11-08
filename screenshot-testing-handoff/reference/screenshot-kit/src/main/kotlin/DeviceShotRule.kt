package com.example.testing

import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.dropbox.dropshots.Dropshots
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File

/**
 * Everything a Lane B screenshot needs, in one rule:
 * frozen system UI, night mode applied before launch, an environment check,
 * a stable full-screen capture and a Dropshots comparison.
 *
 * ```
 * @get:Rule val shots = DeviceShotRule()
 * @Test fun loaded_dark() = shots.capture("loaded_dark", group = "checkout", night = true) { ... }
 * ```
 *
 * Pass `outer = HiltAndroidRule(this)` when the module uses Hilt.
 */
class DeviceShotRule(outer: TestRule? = null) : TestRule {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()

    // Dropshots 0.6.0: the Gradle plugin uses the *test* APK id for its device folder while the
    // runtime defaults to the *app* id, so record/pull miss in app modules (dropbox/dropshots#115).
    // Pin both to the test APK's folder.
    private val shotDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "screenshots/${instrumentation.context.packageName}",
    )
    private val dropshots = Dropshots(
        recordScreenshots = File(shotDir, ".isRecordingScreenshots").exists(),
        rootScreenshotDirectory = shotDir,
    )

    val compose = createEmptyComposeRule()

    private val chain: RuleChain = run {
        val inner = RuleChain.outerRule(SystemUiFreeze()).around(compose).around(dropshots)
        if (outer != null) RuleChain.outerRule(outer).around(inner) else inner
    }

    override fun apply(base: Statement, description: Description): Statement =
        chain.apply(base, description)

    /**
     * Launches [content] edge-to-edge in a fresh activity and compares a full-screen
     * capture (system bars included) with `src/androidTest/screenshots/<group>/<name>.png`.
     */
    fun capture(
        name: String,
        group: String,
        night: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        // Must happen before launch so enableEdgeToEdge() picks matching system-bar icons.
        setNightMode(night)

        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(ShotHostActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { content() } }
            awaitSystemBars(scenario)
            val screenshot = stableScreenshot()
            saveActual(screenshot, name, group)
            dropshots.assertSnapshot(screenshot, name, group)
        }
    }

    /**
     * Waits until the window reports visible system bars with a real height. Insets arrive
     * after the first frame; capturing before that draws content under the status bar.
     */
    private fun awaitSystemBars(scenario: ActivityScenario<ShotHostActivity>) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        var seen = "no insets"
        while (true) {
            var ready = false
            scenario.onActivity { activity ->
                val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
                // statusBars + navigationBars, not systemBars(): that also includes the caption bar,
                // which a phone never shows, so isVisible(systemBars()) is always false.
                val bars = WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
                // Fully shown: what the bars occupy now equals what they occupy when visible.
                // A half-finished show animation (seen on API 36) fails this check.
                seen = insets?.let {
                    "visible=${it.isVisible(bars)} now=${it.getInsets(bars)} full=${it.getInsetsIgnoringVisibility(bars)}"
                } ?: "no insets"
                ready = insets != null &&
                    insets.isVisible(bars) &&
                    insets.getInsets(bars).top > 0 &&
                    insets.getInsets(bars) == insets.getInsetsIgnoringVisibility(bars)
            }
            if (ready) return
            check(SystemClock.uptimeMillis() < deadline) { "INFRA: system bars never fully appeared ($seen)" }
            SystemClock.sleep(50)
        }
    }

    /**
     * Full-screen capture, repeated until two consecutive frames are identical. The paused
     * Compose clock is advanced before every capture: otherwise state that arrives late
     * (window insets, layout) is never recomposed and a stale frame looks "stable".
     */
    private fun stableScreenshot(): Bitmap {
        var previous = settleAndCapture()
        repeat(MAX_SETTLE_ATTEMPTS) {
            val next = settleAndCapture()
            if (next.sameAs(previous)) return next
            previous = next
        }
        throw IllegalStateException("INFRA: screen never settled; this is not a pixel failure")
    }

    private fun settleAndCapture(): Bitmap {
        compose.mainClock.advanceTimeBy(SETTLE_STEP_MS)
        compose.waitForIdle()
        UiDevice.getInstance(instrumentation).waitForIdle()
        SystemClock.sleep(100)
        return takeScreenshot()
    }

    /**
     * Dropshots only writes an image when a comparison fails. Saving every capture lets the
     * HTML report show what each passing test saw too. Pulled with Dropshots' own files.
     */
    private fun saveActual(bitmap: Bitmap, name: String, group: String) {
        val file = File(shotDir, "actual/$group/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun takeScreenshot(): Bitmap =
        instrumentation.uiAutomation.takeScreenshot()
            ?: throw IllegalStateException("INFRA: UiAutomation returned no screenshot")

    private inner class SystemUiFreeze : ExternalResource() {
        override fun before() {
            checkEnvironment()
            shell("settings put global sysui_demo_allowed 1")
            demo("enter")
            demo("clock -e hhmm 1200")
            demo("battery -e level 100 -e plugged false")
            // The mobile signal icon kept changing under demo mode on API 36 (found in the POC's
            // first verify run), so hide everything cellular and pin Wi-Fi.
            demo("network -e mobile hide")
            demo("network -e nosim hide")
            demo("network -e airplane hide")
            demo("network -e wifi show -e level 4 -e fully true")
            demo("notifications -e visible false")
            SystemClock.sleep(300)
        }

        override fun after() {
            demo("exit")
            setNightMode(false)
        }
    }

    /**
     * Changes night mode only when it differs, then waits for System UI to re-lay out the bars.
     * Found in the POC: launching right after a night-mode change caught the status bar mid
     * re-layout on some boots, and every light screenshot shifted up by half its height.
     */
    private fun setNightMode(night: Boolean) {
        val wanted = if (night) "yes" else "no"
        if (shellOutput("cmd uimode night").contains("Night mode: $wanted")) return
        shell("cmd uimode night $wanted")
        SystemClock.sleep(NIGHT_MODE_SETTLE_MS)
    }

    /** Strict on CI (`deviceShots.ci=true`); a warning locally so laptops can still look. */
    private fun checkEnvironment() {
        val expected = args.getString("deviceShots.fingerprint") ?: return
        if (Build.FINGERPRINT == expected) return
        val message = "Environment drifted: ${Build.FINGERPRINT} != $expected"
        check(args.getString("deviceShots.ci") != "true") { message }
        Log.w(TAG, "$message. Local results are advisory.")
    }

    private fun demo(command: String) =
        shell("am broadcast -a com.android.systemui.demo -e command $command")

    private fun shell(cmd: String) {
        shellOutput(cmd)
    }

    /** Runs [cmd] and reads to EOF, so the command has finished before the test continues. */
    private fun shellOutput(cmd: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(cmd))
            .use { it.readBytes().decodeToString() }

    private companion object {
        const val TAG = "DeviceShots"
        const val MAX_SETTLE_ATTEMPTS = 6
        const val SETTLE_STEP_MS = 500L
        const val NIGHT_MODE_SETTLE_MS = 2_000L
    }
}
