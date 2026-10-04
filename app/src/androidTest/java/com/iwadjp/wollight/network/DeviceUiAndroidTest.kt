package com.iwadjp.wollight.network

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iwadjp.wollight.MainActivity
import com.iwadjp.wollight.R
import com.iwadjp.wollight.data.db.AppDatabase
import com.iwadjp.wollight.data.db.DeviceEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceUiAndroidTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun registeredDeviceCanBeOpenedEditedAndKept(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "wol_light.db").build()
        val fixture = DeviceEntity(900108, "UI regression fixture", "10.0.2.2", "AA:BB:CC:DD:EE:FF")
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            db.deviceDao().insert(fixture)
            // Seed before launching: separate Room instances do not share invalidation.
            scenario = ActivityScenario.launch(MainActivity::class.java)
            ui.waitUntil(5000) { runCatching { ui.onNodeWithText(fixture.name).assertIsDisplayed(); true }.getOrDefault(false) }
            ui.onNodeWithText(fixture.name).performClick()
            ui.onNodeWithContentDescription(context.getString(R.string.detail_edit)).performClick()
            ui.onNodeWithText(context.getString(R.string.common_device_name)).performTextReplacement("UI edited fixture")
            ui.onNodeWithText(context.getString(R.string.edit_save)).performClick()
            ui.waitUntil(5000) { ui.onAllNodesWithText("UI edited fixture").fetchSemanticsNodes().isNotEmpty() }
            assertEquals("UI edited fixture", db.deviceDao().getById(fixture.id).first()?.name)
        } finally {
            scenario?.close()
            db.deviceDao().delete(fixture)
            db.close()
        }
    }
}
