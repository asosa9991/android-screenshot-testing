package com.example.previews

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * The phone every preview renders on: a Pixel 6 (411 × 914 dp, 420 dpi), the same profile as
 * the emulator screenshots, so both kinds of golden show the same screen.
 */
const val PHONE = "id:pixel_6"

/**
 * A screen in light mode, dark mode, and with large text (font scale 1.5), on [PHONE].
 * Put it on a preview and Roborazzi renders all three as JVM screenshot tests (`./screenshots previews <module>`).
 */
@Preview(name = "light", device = PHONE, showBackground = true)
@Preview(name = "dark", device = PHONE, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "font-1.5", device = PHONE, showBackground = true, fontScale = 1.5f)
annotation class ThemeVariants
