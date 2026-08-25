package com.iwadjp.wollight.util

object MacAddressNormalizer {

    private val HEX_ONLY = Regex("^[0-9A-F]{12}$")

    /**
     * Returns a canonical 12 hex-digit uppercase form (no separators),
     * or null if [macAddress] is blank or not a valid MAC address.
     */
    fun normalize(macAddress: String): String? {
        val stripped = macAddress.trim().replace(":", "").replace("-", "").uppercase()
        return if (HEX_ONLY.matches(stripped)) stripped else null
    }
}
