package com.github.nrfr.manager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun detectsNewerVersion() {
        assertTrue(AppUpdateManager.isNewerVersion("1.5.7", "1.5.6"))
        assertFalse(AppUpdateManager.isNewerVersion("1.5.6", "1.5.7"))
        assertFalse(AppUpdateManager.isNewerVersion("1.5.7", "1.5.7"))
    }
}
