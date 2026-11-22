package com.music.bitchord

import com.music.bitchord.ui.screens.folderDisplayName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsFolderNameTest {
    private fun tree(id: String) =
        "content://com.android.externalstorage.documents/tree/$id/document/$id"

    @Test fun typicalTreeUriShowsTheFolderName() {
        assertEquals("Music (1)", folderDisplayName(tree("primary%3AMusic%20%281%29")))
    }
    @Test fun nestedPathKeepsItsSlashes() {
        assertEquals("Music/spool", folderDisplayName(tree("primary%3AMusic%2Fspool")))
    }
    @Test fun encodedPlusStaysAPlus() {
        assertEquals("C++", folderDisplayName(tree("primary%3AC%2B%2B")))
    }
    @Test fun literalPlusIsNotDecodedToASpace() {
        assertEquals("A+B", folderDisplayName(tree("primary%3AA+B")))
    }
    @Test fun colonInsideTheFolderNameIsKept() {
        assertEquals("Music/A:B", folderDisplayName(tree("primary%3AMusic%2FA%3AB")))
    }
    @Test fun invalidEscapeStaysLiteral() {
        assertEquals("100%zz", folderDisplayName(tree("primary%3A100%zz")))
    }
    @Test fun utf8FolderNamesDecode() {
        assertEquals("സ്നേഹം", folderDisplayName(tree("primary%3A%E0%B4%B8%E0%B5%8D%E0%B4%A8%E0%B5%87%E0%B4%B9%E0%B4%82")))
    }
    @Test fun sdCardVolumeShowsOnlyThePath() {
        assertEquals("Music", folderDisplayName(tree("12AB-34CD%3AMusic")))
    }
    @Test fun blankInputHasNoName() {
        assertNull(folderDisplayName(""))
    }
    @Test fun volumeWithoutAPathHasNoName() {
        assertNull(folderDisplayName(tree("primary%3A")))
    }
    @Test fun nonSafUriFallsBackToItsLastSegment() {
        assertEquals("1234", folderDisplayName("content://media/external/file/1234"))
    }
}
