@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.ui.components.*
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34], qualifiers=RobolectricDeviceQualifiers.Pixel7)
abstract class HeaderControlsScreenshotTestBase {
    @get:Rule val compose = createComposeRule()
    protected fun shot(page:String, dark:Boolean=true, width:Int=390, scale:Float=1f, inset:Int=24) {
        V6ScreenshotTest().installUiHost()
        val root=page in listOf("Listen","Explore","Library")
        val settings=page=="Settings"
        val detail=page=="Album"
        val actions=mutableListOf<String>()
        var measured=0
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,scale)) {
            BitChordTheme(darkTheme=dark) {
                Box(Modifier.width(width.dp).height(280.dp).background(MaterialTheme.colorScheme.background).testTag("header-frame")) {
                    FrostedTopBar(title=page,rootTitle=page.takeIf {root},onRootHeaderMeasured={measured=it},
                        statusBarInsets=WindowInsets(top=inset.dp),scrolled={false},transparentBackdrop=true,
                        artworkPageChrome=true,bareDetailChrome=detail,settingsHomeChrome=settings,
                        onBack=if(root||page=="Search") null else ({actions+="back"}),
                        accessory=if(page=="Search") ({ Text("Search music",Modifier.padding(12.dp),color=MaterialTheme.colorScheme.onSurface) }) else null,
                        actions={
                            if(page=="Listen"||page=="Library") HeaderActionButton({actions+="history"}) {
                                HeaderIcon(Icons.Rounded.History,"History")
                            }
                            if(detail) {
                                HeaderActionButton({actions+="search"}) { HeaderIcon(Icons.Rounded.Search,"Search list") }
                                HeaderActionButton({actions+="sort"}) { HeaderIcon(Icons.Rounded.Sort,"Sort") }
                                HeaderActionButton({actions+="more"}) { HeaderIcon(Icons.Rounded.MoreHoriz,"More") }
                            } else if(!settings) TopBarAccountButton(null,{actions+="profile"})
                        })
                }
            }
        } }
        compose.waitForIdle()
        val targets=compose.onAllNodesWithTag("header-action-target").fetchSemanticsNodes()
        targets.forEach { node ->
            assertTrue("width48dp",node.boundsInRoot.width>=47.5f)
            assertTrue("height48dp",node.boundsInRoot.height>=47.5f)
            assertTrue("below safe inset",node.boundsInRoot.top>=inset)
            assertTrue("inside right edge",node.boundsInRoot.right<=width+0.5f)
        }
        for(a in targets.indices) for(b in a+1 until targets.size) {
            assertFalse("targets cannot overlap",targets[a].boundsInRoot.overlaps(targets[b].boundsInRoot))
        }
        if(root) {
            val title=compose.onNodeWithTag("root-header-title").fetchSemanticsNode().boundsInRoot
            targets.forEach { assertTrue("title clear of actions",title.right<=it.boundsInRoot.left) }
            assertTrue("measured safe header boundary",measured>=inset+64)
            assertEquals(1,compose.onAllNodesWithText(page).fetchSemanticsNodes().size)
        }
        if(page=="Listen"||page=="Library") compose.onNodeWithContentDescription("History").performClick()
        if(detail) {
            compose.onNodeWithContentDescription("Search list").performClick()
            compose.onNodeWithContentDescription("Sort").performClick()
            compose.onNodeWithContentDescription("More").performClick()
            assertEquals(listOf("search","sort","more"),actions)
            assertEquals(4,compose.onAllNodesWithTag("header-action-bare",useUnmergedTree=true).fetchSemanticsNodes().size)
        } else if(!settings) {
            compose.onNodeWithContentDescription("Settings",substring=true).performClick()
            assertEquals(if(page=="Listen"||page=="Library")listOf("history","profile") else listOf("profile"),actions)
        }
        if (!root && page != "Search") {
            compose.onNodeWithContentDescription("Back").performClick()
            assertEquals("back", actions.last())
        }
        compose.onNodeWithTag("header-frame").captureRoboImage("build/outputs/roborazzi/header_${page}_${width}_${scale}_${inset}_${if(dark)"dark" else "light"}.png")
    }
}

class HeaderControlsScreenshotTest_listenDark : HeaderControlsScreenshotTestBase() { @Test fun verify() = shot("Listen") }
class HeaderControlsScreenshotTest_listenLight : HeaderControlsScreenshotTestBase() { @Test fun verify() = shot("Listen",false) }
class HeaderControlsScreenshotTest_exploreDark : HeaderControlsScreenshotTestBase() { @Test fun verify() = shot("Explore") }
class HeaderControlsScreenshotTest_exploreLight : HeaderControlsScreenshotTestBase() { @Test fun verify() = shot("Explore",false) }
class HeaderControlsScreenshotTest_libraryDark : HeaderControlsScreenshotTestBase() { @Test fun verify() = shot("Library") }
class HeaderControlsScreenshotTest_libraryLight : HeaderControlsScreenshotTestBase() { @Test fun verify() = shot("Library",false) }
class HeaderControlsScreenshotTest_listenNarrowLargeCutout : HeaderControlsScreenshotTestBase() { @Test fun verify() = shot("Listen",width=320,scale=2f,inset=48) }
class HeaderControlsScreenshotTest_exploreNarrowLargeCutout : HeaderControlsScreenshotTestBase() { @Test fun verify() = shot("Explore",width=320,scale=2f,inset=48) }
class HeaderControlsScreenshotTest_libraryNarrowLargeCutout : HeaderControlsScreenshotTestBase() { @Test fun verify() = shot("Library",width=320,scale=2f,inset=48) }
class HeaderControlsScreenshotTest_searchDark : HeaderControlsScreenshotTestBase() { @Test fun verify() = shot("Search") }
class HeaderControlsScreenshotTest_searchLight : HeaderControlsScreenshotTestBase() { @Test fun verify() = shot("Search",false) }
class HeaderControlsScreenshotTest_searchNarrowLarge : HeaderControlsScreenshotTestBase() { @Test fun verify() = shot("Search",width=320,scale=2f) }
class HeaderControlsScreenshotTest_settingsDark : HeaderControlsScreenshotTestBase() { @Test fun verify() = shot("Settings") }
class HeaderControlsScreenshotTest_settingsLight : HeaderControlsScreenshotTestBase() { @Test fun verify() = shot("Settings",false) }
class HeaderControlsScreenshotTest_albumBareDark : HeaderControlsScreenshotTestBase() { @Test fun verify() = shot("Album",width=320) }
class HeaderControlsScreenshotTest_albumBareLight : HeaderControlsScreenshotTestBase() { @Test fun verify() = shot("Album",false,width=320) }
class HeaderControlsScreenshotTest_historyDark : HeaderControlsScreenshotTestBase() { @Test fun verify() = shot("History") }
class HeaderControlsScreenshotTest_historyLight : HeaderControlsScreenshotTestBase() { @Test fun verify() = shot("History",false) }
