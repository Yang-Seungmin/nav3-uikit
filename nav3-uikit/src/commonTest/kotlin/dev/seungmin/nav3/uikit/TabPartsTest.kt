package dev.seungmin.nav3.uikit

import kotlin.test.Test
import kotlin.test.assertEquals

class TabPartsTest {
    private val roots = listOf("home", "search", "profile")

    @Test
    fun splitsAtTabRoots() {
        val parts = tabParts(listOf("home", "a", "search", "b", "c"), roots)
        assertEquals(listOf(0..1, 2..4, null), parts.ranges)
        assertEquals(1, parts.selected)
    }

    @Test
    fun tabThatOccursTwiceShowsItsLastPart() {
        val parts = tabParts(listOf("home", "a", "search", "home", "b"), roots)
        assertEquals(listOf(3..4, 2..2, null), parts.ranges)
        assertEquals(0, parts.selected)
    }
}
