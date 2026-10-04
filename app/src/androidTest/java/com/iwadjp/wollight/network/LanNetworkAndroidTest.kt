package com.iwadjp.wollight.network

import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iwadjp.wollight.R
import java.util.Locale
import java.util.Collections
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanNetworkAndroidTest {
    private suspend fun currentLanSubnet(context: Context): IpSubnet {
        val monitor = LanNetworkMonitor(context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
        return try { monitor.discover() } finally { monitor.close() }
    }
    @Test fun cancellingRealScanClosesItsMonitor(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scanner = IpScanner(context, NetBiosResolver(), MdnsResolver(context))
        val started = CompletableDeferred<Unit>()
        val scan = launch { scanner.scan { count, _ -> if (count == 0) started.complete(Unit) } }
        started.await()
        scan.cancelAndJoin()
        assertTrue(scan.isCancelled)
        // A subsequent discovery succeeds after cancellation released the callback.
        assertTrue(scanTargets(currentLanSubnet(context)).isNotEmpty())
    }
    @Test fun scanProgressUsesActualTargetCount(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scanner = IpScanner(context, NetBiosResolver(), MdnsResolver(context))
        val expected = scanTargets(currentLanSubnet(context)).size
        val counts = Collections.synchronizedList(mutableListOf<Int>())
        scanner.scan { count, total ->
            assertEquals(expected, total)
            counts.add(count)
        }
        assertEquals((0..expected).toList(), counts.sorted())
        Log.i("LanNetworkAndroidTest", "Progress completed: $expected/$expected")
    }

    @Test fun englishJapaneseAndUnsupportedLocaleFallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun localized(language: String): Context {
            val config = Configuration(context.resources.configuration)
            config.setLocale(Locale.forLanguageTag(language))
            return context.createConfigurationContext(config)
        }
        assertEquals("LAN scan", localized("en").getString(R.string.scan_title))
        assertEquals("LANスキャン", localized("ja").getString(R.string.scan_title))
        assertEquals("LAN scan", localized("de").getString(R.string.scan_title))
        assertEquals("Scanning… (3 / 125)", localized("en").getString(R.string.scan_progress, 3, 125))
        assertEquals("スキャン中... (3 / 125)", localized("ja").getString(R.string.scan_progress, 3, 125))
        assertEquals(localized("en").getString(R.string.scan_no_lan), localized("de").getString(R.string.scan_no_lan))
    }

    @Test fun physicalLanLinkPropertiesProduceCorrectPlan(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val scanner = IpScanner(context, NetBiosResolver(), MdnsResolver(context))
        val subnet = currentLanSubnet(context)
        val matching = cm.allNetworks.filter { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@filter false
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) &&
                cm.getLinkProperties(network)?.linkAddresses?.any { it.address == subnet.address && it.prefixLength == subnet.prefixLength } == true
        }
        assertTrue("Selected subnet must belong to a physical LAN", matching.isNotEmpty())
        val targets = scanTargets(subnet)
        assertFalse(targets.contains(subnet.address.hostAddress))
        assertTrue(targets.size <= MAX_SCAN_TARGETS)
        Log.i("LanNetworkAndroidTest", "LAN ${subnet.address.hostAddress}/${subnet.prefixLength}; targets=${targets.size}; activeVPN=${cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)}")
        if (InstrumentationRegistry.getArguments().getString("expectVpn") == "true") {
            assertTrue("A real VPN must be default", cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true)
            assertFalse("Never select the probe tunnel", subnet.address.hostAddress!!.startsWith("10.8.0."))
        }
    }

    @Test fun wifiLossAbortsScan(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val scanner = IpScanner(context, NetBiosResolver(), MdnsResolver(context))
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifi = cm.allNetworks.single { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == false }
        var disabled = false
        try {
            scanner.scan { count, _ ->
                if (count == 0) {
                    disabled = true
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(
                        instrumentation.uiAutomation.executeShellCommand("svc wifi disable")
                    ).use { it.readBytes() }
                    // Wait for the actual OS loss event while the scan is pinned, before
                    // its first probe; otherwise a fast scan can legitimately finish first.
                    val deadline = System.currentTimeMillis() + 5000
                    while (cm.getNetworkCapabilities(wifi) != null && System.currentTimeMillis() < deadline) {
                        Thread.sleep(50)
                    }
                    assertNull("The OS must actually remove the Wi-Fi network", cm.getNetworkCapabilities(wifi))
                }
            }
            fail("Scan must stop after Wi-Fi loss")
        } catch (error: LanScanException) {
            assertEquals(LanScanFailure.NETWORK_CHANGED, error.reason)
        } finally {
            if (disabled) {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    instrumentation.uiAutomation.executeShellCommand("svc wifi enable")
                ).use { it.readBytes() }
                delay(3000)
            }
        }
    }

    @Test fun vpnOnlySimulationProducesNoScanTargets() {
        val vpn = LanNetworkSnapshot("tunnel", true, true,
            listOf(IpSubnet(java.net.InetAddress.getByName("10.8.0.2"), 24)))
        val error = assertThrows(LanScanException::class.java) { selectLanSubnet(listOf(vpn), "tunnel") }
        assertEquals(LanScanFailure.NO_LAN, error.reason)
    }
}
