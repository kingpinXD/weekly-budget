package com.example.weeklytotals

import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityTest {

    @Test
    fun sanityCheck() {
        // Basic sanity test to verify the test framework works
        assertEquals(4, 2 + 2)
    }

    @Test
    fun appPackageName() {
        // Verify our expected package name
        assertEquals("com.example.weeklytotals", MainActivity::class.java.packageName)
    }
}
