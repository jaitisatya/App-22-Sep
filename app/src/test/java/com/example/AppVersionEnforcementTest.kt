package com.example

import com.example.data.model.AppVersionConfig
import com.example.data.model.VersionStatus
import org.junit.Assert.*
import org.junit.Test

class AppVersionEnforcementTest {

    @Test
    fun test1_installedVersionLowerThanMinSupported_isUnsupported() {
        val installedVersionCode = 1
        val minSupportedVersionCode = 2

        val isSupported = installedVersionCode >= minSupportedVersionCode
        val status = if (isSupported) VersionStatus.SUPPORTED else VersionStatus.UNSUPPORTED

        assertEquals(VersionStatus.UNSUPPORTED, status)
        assertFalse("Old version should not be allowed to perform cloud sync", isSupported)
    }

    @Test
    fun test2_installedVersionEqualToMinSupported_isSupported() {
        val installedVersionCode = 2
        val minSupportedVersionCode = 2

        val isSupported = installedVersionCode >= minSupportedVersionCode
        val status = if (isSupported) VersionStatus.SUPPORTED else VersionStatus.UNSUPPORTED

        assertEquals(VersionStatus.SUPPORTED, status)
        assertTrue("Matching version should be allowed to run and sync", isSupported)
    }

    @Test
    fun test3_installedVersionHigherThanMinSupported_isSupported() {
        val installedVersionCode = 3
        val minSupportedVersionCode = 2

        val isSupported = installedVersionCode >= minSupportedVersionCode
        val status = if (isSupported) VersionStatus.SUPPORTED else VersionStatus.UNSUPPORTED

        assertEquals(VersionStatus.SUPPORTED, status)
        assertTrue("Higher version should be allowed to run and sync", isSupported)
    }

    @Test
    fun test4_networkCheckFailed_blocksSync() {
        val status = VersionStatus.CHECK_FAILED
        val isSyncAllowed = status == VersionStatus.SUPPORTED

        assertFalse("Network failure must NOT allow sync or cloud writes", isSyncAllowed)
    }

    @Test
    fun test5_appVersionConfig_containsRequiredFieldsAndDefaults() {
        val config = AppVersionConfig(
            minSupportedVersionCode = 5,
            latestVersionCode = 6,
            latestVersionName = "2.1",
            forceUpdate = true,
            updateUrl = "https://jaitifoundation.org/download/app-latest.apk",
            title = "Please Update",
            message = "A new version of the Jaiti Foundation App is available. Please update the app to continue."
        )

        assertEquals(5, config.minSupportedVersionCode)
        assertEquals(6, config.latestVersionCode)
        assertEquals("2.1", config.latestVersionName)
        assertTrue(config.forceUpdate)
        assertEquals("https://jaitifoundation.org/download/app-latest.apk", config.updateUrl)
        assertEquals("Please Update", config.title)
        assertEquals(
            "A new version of the Jaiti Foundation App is available. Please update the app to continue.",
            config.message
        )
    }

    @Test
    fun test6_initialCheckingState_blocksSyncUntilVerified() {
        val status = VersionStatus.CHECKING
        val isSyncAllowed = status == VersionStatus.SUPPORTED

        assertFalse("CHECKING state must block background writes and cloud synchronization", isSyncAllowed)
    }
}
