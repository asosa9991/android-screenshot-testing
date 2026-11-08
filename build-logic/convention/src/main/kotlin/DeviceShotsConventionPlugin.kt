import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * One line per module to opt in to emulator screenshots (Lane B):
 *
 *     plugins { id("shots.device") }
 *
 * Applies Dropshots with safe defaults and adds the screenshot kit to androidTest.
 */
class DeviceShotsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // Keep Dropshots' default recordOnFailure = true. In 0.6.0, false means references are
        // copied into src/ only when the build *failed*, so a clean recording saves nothing.
        // "Never record a broken run" is enforced by ./run shots-record instead, which restores
        // the previous goldens if any test fails.
        pluginManager.apply("com.dropbox.dropshots")

        pluginManager.withPlugin("com.android.application") {
            extensions.configure<ApplicationExtension> {
                testOptions.animationsDisabled = true
            }
        }
        pluginManager.withPlugin("com.android.library") {
            extensions.configure<LibraryExtension> {
                testOptions.animationsDisabled = true
            }
        }

        dependencies {
            add("androidTestImplementation", project(":screenshot-kit"))
        }
    }
}
