package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        HomeScreen(
          scannedPdfs = listOf(
            PdfFile(1L, "مثال_كتاب.pdf", "/storage/emulated/0/Download/example.pdf", 1024 * 512, System.currentTimeMillis()),
            PdfFile(2L, "تقرير_مالي_2026.pdf", "/storage/emulated/0/Documents/report.pdf", 1024 * 1024 * 3, System.currentTimeMillis() - 86400000)
          ),
          recentPdfs = listOf(
            PdfFile(1L, "مثال_كتاب.pdf", "/storage/emulated/0/Download/example.pdf", 1024 * 512, System.currentTimeMillis())
          ),
          searchQuery = "",
          isScanning = false,
          onSearchChange = {},
          onOpenFilePicker = {},
          onOpenSamplePdf = {},
          onOpenFile = {},
          onTriggerScan = {},
          onClearRecents = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
