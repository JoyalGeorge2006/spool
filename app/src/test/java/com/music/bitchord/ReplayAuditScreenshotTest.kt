package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.data.stats.*
import com.music.bitchord.ui.replay.*
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Synthetic listening summary. Never opens a real account or sends a share. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ReplayAuditScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val songs = List(6) { i -> RankedSong(Song("fixture:replay-$i", "Fixture track ${i+1}", "Fixture artist", null), (60-i)*60000L, 10-i) }
    private val entries = List(5) { i -> RankedEntry("Fixture artist ${i+1}", "Fixture album", null, null, (60-i)*60000L, 10-i) }
    internal val summary = ReplaySummary(ReplayPeriod.THIS_YEAR, "2026", 48000000L, 120,
        songs, entries, entries.mapIndexed { index, entry -> entry.copy(title = "Fixture album", browseId = "fixture:album-$index") },
        entries.mapIndexed { index, entry -> entry.copy(title = "Fixture genre ${index+1}") },
        List(24) { if(it==17) 3600000L else 0L }, "2026-11-01", 3600000L, 6, 5, 5, "2026-01")
    private fun shot(dark: Boolean, page: ReplayStoryPage?, charts: Boolean = false) {
        V6ScreenshotTest().installUiHost()
        val old = AppSettings.themeMode.value
        AppSettings.setThemeMode(if(dark) ThemeMode.DARK else ThemeMode.LIGHT)
        try {
            compose.setContent { BitChordTheme(darkTheme=dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if(page != null) ReplayStories(summary, page, {}, {}, paused=true)
                    else ReplayScreen(ReplayState(ReplayPeriod.THIS_YEAR,summary,false,"01/26"),"Fixture listener",{}, {}, {}, {_,_->}, {_,_,_,_->}, {}, PaddingValues(), landingPage=if(charts) ReplayStoryPage.SONGS else ReplayStoryPage.INTRO)
                }
            } }
            compose.waitForIdle()
            compose.onAllNodes(isRoot()).onLast().captureRoboImage("build/outputs/roborazzi/replay_${page?.name?.lowercase() ?: if(charts) "charts" else "data"}_${if(dark) "dark" else "light"}.png")
        } finally { AppSettings.setThemeMode(old) }
    }
    @Test fun story_intro_light() = shot(false, ReplayStoryPage.INTRO)
    @Test fun story_intro_dark() = shot(true, ReplayStoryPage.INTRO)
    @Test fun story_minutes_light() = shot(false, ReplayStoryPage.MINUTES)
    @Test fun story_minutes_dark() = shot(true, ReplayStoryPage.MINUTES)
    @Test fun story_artists_light() = shot(false, ReplayStoryPage.ARTISTS)
    @Test fun story_artists_dark() = shot(true, ReplayStoryPage.ARTISTS)
    @Test fun story_songs_light() = shot(false, ReplayStoryPage.SONGS)
    @Test fun story_songs_dark() = shot(true, ReplayStoryPage.SONGS)
    @Test fun story_albums_light() = shot(false, ReplayStoryPage.ALBUMS)
    @Test fun story_albums_dark() = shot(true, ReplayStoryPage.ALBUMS)
    @Test fun story_genres_light() = shot(false, ReplayStoryPage.GENRES)
    @Test fun story_genres_dark() = shot(true, ReplayStoryPage.GENRES)
    @Test fun story_habits_light() = shot(false, ReplayStoryPage.HABITS)
    @Test fun story_habits_dark() = shot(true, ReplayStoryPage.HABITS)
    @Test fun story_summary_light() = shot(false, ReplayStoryPage.SUMMARY)
    @Test fun story_summary_dark() = shot(true, ReplayStoryPage.SUMMARY)
    @Test fun data_light() = shot(false, null, false)
    @Test fun data_dark() = shot(true, null, false)
    @Test fun charts_light() = shot(false, null, true)
    @Test fun charts_dark() = shot(true, null, true)
}
