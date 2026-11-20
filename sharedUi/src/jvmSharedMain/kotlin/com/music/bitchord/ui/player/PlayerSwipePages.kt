package com.music.bitchord.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** Only the portrait section pages own horizontal row swipes. */
internal val LocalPlayerPageGestures = compositionLocalOf { false }

/** Stable page identities keep queue and lyric scroll positions independent. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PlayerSwipePages(
    selected: PlayerPaneTab,
    onSelected: (PlayerPaneTab) -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    content: @Composable (PlayerPaneTab) -> Unit,
) {
    val tabs = PlayerPaneTab.entries
    val pager = rememberPagerState(initialPage = selected.ordinal, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val latestSelected by rememberUpdatedState(onSelected)
    LaunchedEffect(selected) {
        if (pager.currentPage != selected.ordinal && !pager.isScrollInProgress) pager.animateScrollToPage(selected.ordinal, animationSpec = tween(if (reduceMotion) 0 else 260))
    }
    LaunchedEffect(pager) {
        snapshotFlow { if (!pager.isScrollInProgress) pager.settledPage else null }
            .filterNotNull().distinctUntilChanged().collect { latestSelected(tabs[it]) }
    }
    Column(modifier) {
        PlayerPaneTabRow(selected = tabs[pager.currentPage], onSelect = { tab ->
            scope.launch { pager.animateScrollToPage(tab.ordinal, animationSpec = tween(if (reduceMotion) 0 else 260)) }
        })
        CompositionLocalProvider(LocalPlayerPageGestures provides true) {
            HorizontalPager(state = pager, beyondViewportPageCount = 2,
                flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(state = pager, snapAnimationSpec = tween(if (reduceMotion) 0 else 260)),
                key = { tabs[it].name }, modifier = Modifier.weight(1f).bleedHorizontally(PLAYER_GUTTER).fillMaxWidth().testTag("player-section-pager")) { index ->
                Box(Modifier.fillMaxSize().clipToBounds().padding(horizontal = if (tabs[index] == PlayerPaneTab.RELATED) 0.dp else PLAYER_GUTTER).testTag("player-page-${tabs[index].name}")) { content(tabs[index]) }
            }
        }
    }
}
