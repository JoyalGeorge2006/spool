package com.music.bitchord

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source-bound regression contract. Does not claim a live Discord session. */
class DiscordIdentityRemovalTest {
    private fun source(path: String): String = File("src/main/java/com/music/bitchord/$path").readText()
    @Test fun mobilePayloadHasNoUpstreamIdentityOrLinkButtons() {
        val rpc = source("data/discord/DiscordRPC.kt")
        assertFalse(rpc.contains("1543679166967193620"))
        assertFalse(rpc.contains("github.com/kushagrasinghx"))
        assertFalse(rpc.contains("Visit BitChord"))
        assertTrue(rpc.contains("applicationId = null"))
        assertTrue(rpc.contains("buttons = null"))
        assertTrue(rpc.contains("?.let(RpcImage::DiscordImage)"))
        assertFalse(rpc.contains("RpcImage::ExternalImage"))
        assertTrue(rpc.contains("smallImage = null"))
        assertTrue(rpc.contains("detailsUrl = watchUrl(song)"))
        assertTrue(rpc.contains("details = songTitleWithRate"))
        assertTrue(rpc.contains("state = song.artist"))
    }
    @Test fun mobilePreviewDoesNotOfferRemovedProjectButton() {
        val screen = source("ui/screens/DiscordScreen.kt")
        assertFalse(screen.contains("PresenceButton("))
        assertFalse(screen.contains("DiscordDialog.BUTTON_"))
        assertTrue(screen.contains("discord_basic_presence_notice"))
    }
}
