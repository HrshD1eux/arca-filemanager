package com.Hrshd1eux.filemanager

import com.Hrshd1eux.filemanager.core.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ArcaFoundationTest {

    @Test
    fun verifyNavigationRoutes() {
        assertNotNull(Screen.Home)
        assertNotNull(Screen.Settings)
        assertNotNull(Screen.About)
    }

    @Test
    fun verifyAppIdentity() {
        val expectedNamespace = "com.Hrshd1eux.filemanager"
        assertEquals("com.Hrshd1eux.filemanager", expectedNamespace)
    }
}
