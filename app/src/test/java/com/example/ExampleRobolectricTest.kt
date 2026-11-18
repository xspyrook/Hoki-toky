package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.i18n.ArabicStrings
import com.example.ui.i18n.EnglishStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Global Walkie Talkie", appName)
  }

  @Test
  fun `bilingual strings are non empty`() {
    assertTrue(EnglishStrings.holdToTalk.isNotBlank())
    assertTrue(ArabicStrings.holdToTalk.isNotBlank())
  }
}
