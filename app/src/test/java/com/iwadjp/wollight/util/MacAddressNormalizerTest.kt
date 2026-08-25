package com.iwadjp.wollight.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MacAddressNormalizerTest {

    @Test
    fun `colon separated uppercase normalizes to canonical form`() {
        assertEquals("AABBCCDDEEFF", MacAddressNormalizer.normalize("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `colon separated lowercase normalizes to same canonical form`() {
        assertEquals("AABBCCDDEEFF", MacAddressNormalizer.normalize("aa:bb:cc:dd:ee:ff"))
    }

    @Test
    fun `hyphen separated normalizes to same canonical form`() {
        assertEquals("AABBCCDDEEFF", MacAddressNormalizer.normalize("aa-bb-cc-dd-ee-ff"))
    }

    @Test
    fun `surrounding whitespace is stripped`() {
        assertEquals("AABBCCDDEEFF", MacAddressNormalizer.normalize("  AA:BB:CC:DD:EE:FF  "))
    }

    @Test
    fun `mixed case and separators still match canonical form`() {
        val a = MacAddressNormalizer.normalize("Aa:bB-cC:Dd-eE:fF")
        assertEquals("AABBCCDDEEFF", a)
    }

    @Test
    fun `empty string is invalid`() {
        assertNull(MacAddressNormalizer.normalize(""))
    }

    @Test
    fun `blank string is invalid`() {
        assertNull(MacAddressNormalizer.normalize("   "))
    }

    @Test
    fun `too short is invalid`() {
        assertNull(MacAddressNormalizer.normalize("AA:BB:CC:DD:EE"))
    }

    @Test
    fun `non-hex characters are invalid`() {
        assertNull(MacAddressNormalizer.normalize("ZZ:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `already canonical uppercase form is accepted`() {
        assertEquals("AABBCCDDEEFF", MacAddressNormalizer.normalize("AABBCCDDEEFF"))
    }
}
