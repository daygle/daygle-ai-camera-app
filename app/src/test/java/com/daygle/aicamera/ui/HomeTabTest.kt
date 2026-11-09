package com.daygle.aicamera.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTabTest {

    @Test
    fun keepsSavedOrder() {
        assertEquals(
            listOf(HomeTab.Events, HomeTab.Cameras, HomeTab.Settings),
            HomeTab.parse("Events,Cameras,Settings"),
        )
    }

    @Test
    fun showsEveryTabWhenNothingValidIsSaved() {
        assertEquals(HomeTab.entries, HomeTab.parse(null))
        assertEquals(HomeTab.entries, HomeTab.parse(""))
        assertEquals(HomeTab.entries, HomeTab.parse("Gone, Missing"))
    }

    @Test
    fun dropsUnknownNamesAndDuplicates() {
        assertEquals(
            listOf(HomeTab.Clips, HomeTab.Settings),
            HomeTab.parse(" Clips ,Removed,Clips,Settings"),
        )
    }

    @Test
    fun alwaysKeepsSettingsReachable() {
        // A saved order without Settings (from before it was locked on)
        // would otherwise leave no way back to the settings screens.
        assertEquals(listOf(HomeTab.Cameras, HomeTab.Settings), HomeTab.parse("Cameras"))
    }
}
