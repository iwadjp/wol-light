package com.iwadjp.wollight.network

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iwadjp.wollight.data.db.AppDatabase
import com.iwadjp.wollight.data.db.DeviceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DevicePersistenceAndroidTest {
    @Test fun registeredDeviceEditAndPersistence(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "wol_light.db").build()
        val dao = db.deviceDao()
        val fixture = DeviceEntity(900106, "RC upgrade fixture", "10.0.2.2", "AA:BB:CC:DD:EE:FF")
        try {
            when (InstrumentationRegistry.getArguments().getString("upgradePhase")) {
                "seed" -> { dao.insert(fixture); assertEquals(fixture, dao.getById(fixture.id).first()) }
                "verify" -> assertEquals("Upgrade must preserve every device field", fixture, dao.getById(fixture.id).first())
                else -> {
                    // This fixture ID belongs only to this test's dedicated emulator.
                    val temporary = fixture.copy(id = 900107)
                    dao.insert(temporary)
                    val edited = temporary.copy(name = "Edited fixture", port = 7)
                    dao.update(edited)
                    assertEquals(edited, dao.getById(edited.id).first())
                    dao.delete(edited)
                    assertNull(dao.getById(edited.id).first())
                }
            }
        } finally { db.close() }
    }
}
