package com.example.testing

/**
 * Marks a Lane B (emulator) screenshot test. CI runs only tests carrying this annotation.
 *
 * [reason] is required: name the system-UI dependency that makes a device necessary
 * (edge-to-edge insets, system-bar contrast, notifications, PiP, WebView, SurfaceView).
 * Everything else belongs in Lane A as a `@PreviewTest`.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class DeviceScreenshot(val reason: String)
