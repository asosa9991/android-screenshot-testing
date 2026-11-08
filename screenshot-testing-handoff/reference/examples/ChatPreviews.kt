package com.example.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.previews.PHONE
import com.example.previews.ThemeVariants

// Every @Preview here is also a JVM screenshot test: `./screenshots previews <module>` compares them with
// the goldens in src/test/screenshots/. No emulator needed.

@ThemeVariants
@Composable
private fun ChatLoaded() = AppTheme { ChatScreen(ChatFixtures.loaded) }

@Preview(device = PHONE, showBackground = true)
@Composable
private fun ChatEmpty() = AppTheme { ChatScreen(ChatFixtures.empty) }

@Preview(device = PHONE, showBackground = true)
@Composable
private fun ChatSteps() = AppTheme { ChatScreen(ChatFixtures.steps) }

@Preview(device = PHONE, showBackground = true)
@Composable
private fun ChatWaiting() = AppTheme { ChatScreen(ChatFixtures.waiting) }

@Preview(device = PHONE, showBackground = true)
@Composable
private fun ChatError() = AppTheme { ChatScreen(ChatFixtures.error) }

@Preview(device = PHONE, showBackground = true)
@Composable
private fun ChatUnsupportedBlock() = AppTheme { ChatScreen(ChatFixtures.unsupported) }
