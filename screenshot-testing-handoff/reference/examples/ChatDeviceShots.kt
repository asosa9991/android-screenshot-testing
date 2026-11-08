package com.example.chat

import com.example.testing.DeviceScreenshot
import com.example.testing.DeviceShotRule
import org.junit.Rule
import org.junit.Test

@DeviceScreenshot(
    reason = "Header is drawn behind the status bar and the composer must clear the navigation bar; " +
        "checks system-bar icon contrast in light and dark",
)
class ChatDeviceShots {
    @get:Rule val shots = DeviceShotRule()

    @Test fun loaded_light() = shots.capture("loaded_light", group = "chat") {
        AppTheme { ChatScreen(ChatFixtures.loaded) }
    }

    @Test fun loaded_dark() = shots.capture("loaded_dark", group = "chat", night = true) {
        AppTheme { ChatScreen(ChatFixtures.loaded) }
    }

    @Test fun error_light() = shots.capture("error_light", group = "chat") {
        AppTheme { ChatScreen(ChatFixtures.error) }
    }
}
