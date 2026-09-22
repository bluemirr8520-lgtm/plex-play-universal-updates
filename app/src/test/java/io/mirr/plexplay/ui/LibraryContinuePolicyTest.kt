package io.mirr.plexplay.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryContinuePolicyTest {
    @Test fun libraryRootShowsSectionEvenBeforeItemsExist() {
        assertTrue(showLibraryContinueSection(true, false, ""))
        assertTrue(showLibraryContinueSection(true, false, "  "))
    }
    @Test fun searchAndChildrenDoNotRepeatLibraryShelf() {
        assertFalse(showLibraryContinueSection(true, true, ""))
        assertFalse(showLibraryContinueSection(true, false, "검색"))
    }
    @Test fun missingLibraryDoesNotShowShelf() {
        assertFalse(showLibraryContinueSection(false, false, ""))
    }
}
