package io.mirr.plexplay.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.mirr.plexplay.data.PlexItem
import io.mirr.plexplay.data.PlexSection
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@Composable
fun PlexPlayApp(viewModel: PlexViewModel) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val hasInternalBackTarget =
        state.selectedItem != null ||
            state.canNavigateBack ||
            !state.isHome ||
            state.isSettingsVisible
            || state.isLibraryOrderVisible
            || state.isAccountVisible
    BackHandler(
        enabled = state.connection.isConfigured &&
            state.playback == null &&
            hasInternalBackTarget,
    ) {
        if (!viewModel.navigateBack()) viewModel.showSettings(false)
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearNotice()
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (state.playback == null) {
            AnimatedContent(
                targetState = when {
                    !state.connection.isConfigured || state.isSettingsVisible -> "connection"
                    else -> "library"
                },
                label = "screen",
            ) { screen ->
                when (screen) {
                    "connection" -> ConnectionScreen(
                        isLoading = state.isLoading,
                        canCancel = state.connection.isConfigured,
                        onConnect = viewModel::connect,
                        onCancel = { viewModel.showSettings(false) },
                    )
                    else -> LibraryScreen(
                        state = state,
                        imageUrl = viewModel::imageUrl,
                        token = viewModel.token(),
                        onSelectSection = viewModel::selectSection,
                        onSelectItem = viewModel::selectItem,
                        onPlay = viewModel::play,
                        onQueryChange = viewModel::setQuery,
                        onShowLibraryOrder = { viewModel.showLibraryOrder(true) },
                        onBack = viewModel::navigateBack,
                        onHome = viewModel::selectHome,
                        onRefresh = viewModel::refresh,
                        onAccount = { viewModel.showAccount(true) },
                    )
                }
            }
        }

        state.playback?.let { source ->
            UniversalPlayerHost(
                source = source,
                playbackQuality = state.playbackQuality,
                hasPreviousPlayback = state.hasPreviousPlayback,
                previousPlaybackTitle = state.previousPlaybackTitle,
                hasNextPlayback = state.hasNextPlayback,
                nextPlaybackTitle = state.nextPlaybackTitle,
                autoPlayNext = state.autoPlayNext,
                onPlaybackQualityChanged = viewModel::setPlaybackQuality,
                onAutoPlayNextChanged = viewModel::setAutoPlayNext,
                onPlayPrevious = viewModel::playPrevious,
                onPlayNext = viewModel::playNext,
                onClose = viewModel::closePlayer,
                onPlaybackCompleted = viewModel::completePlayback,
                onProgress = viewModel::reportProgress,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        )

        AnimatedVisibility(
            visible = state.isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
                shadowElevation = 12.dp,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(18.dp).size(32.dp),
                    strokeWidth = 3.dp,
                )
            }
        }
    }

    if (state.playback == null) {
        state.selectedItem?.let { item ->
            ItemSheet(
                item = item,
                canBrowse = state.selectedItemCanBrowse,
                posterUrl = viewModel.imageUrl(item.thumb),
                backdropUrl = viewModel.imageUrl(item.art ?: item.thumb),
                token = viewModel.token(),
                canRemoveFromContinue = state.continueWatching.any {
                    it.ratingKey == item.ratingKey
                } || state.libraryContinueRows.any { row ->
                    row.items.any { it.ratingKey == item.ratingKey }
                },
                actorWorks = state.relatedActorWorks,
                similarGenreWorks = state.relatedGenreWorks,
                isRelatedLoading = state.isRelatedLoading,
                relatedImageUrl = viewModel::imageUrl,
                onDismiss = { viewModel.selectItem(null) },
                onPlay = { viewModel.play(item) },
                onBrowse = { viewModel.browse(item) },
                onMarkWatched = { viewModel.markWatched(item) },
                onMarkUnwatched = { viewModel.markUnwatched(item) },
                onRemoveFromContinue = { viewModel.removeFromContinueWatching(item) },
                onSelectRelated = viewModel::selectItem,
            )
        }
        if (state.isLibraryOrderVisible) {
            LibraryOrderDialog(
                sections = state.sections,
                onMove = viewModel::moveLibrary,
                onDismiss = { viewModel.showLibraryOrder(false) },
            )
        }
        if (state.isAccountVisible) {
            AccountDialog(
                serverName = state.serverName,
                serverVersion = state.serverVersion,
                serverUrl = state.connection.baseUrl,
                onLogout = viewModel::logout,
                onDismiss = { viewModel.showAccount(false) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen(
    state: PlexUiState,
    imageUrl: (String?) -> String?,
    token: String,
    onSelectSection: (PlexSection) -> Unit,
    onSelectItem: (PlexItem) -> Unit,
    onPlay: (PlexItem) -> Unit,
    onQueryChange: (String) -> Unit,
    onShowLibraryOrder: () -> Unit,
    onBack: () -> Boolean,
    onHome: () -> Unit,
    onRefresh: () -> Unit,
    onAccount: () -> Unit,
) {
    val libraryGridState = rememberLazyGridState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTelevision = remember(context, configuration) {
        context.isTelevisionDevice()
    }
    val useSideNavigation = isTelevision || configuration.screenWidthDp >= 840
    val homeActionFocusRequester = remember { FocusRequester() }
    var homeSearchExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(useSideNavigation) {
        if (useSideNavigation) {
            delay(180)
            runCatching { homeActionFocusRequester.requestFocus() }
        }
    }
    LaunchedEffect(state.selectedSection?.key) {
        if (!state.isHome) {
            libraryGridState.scrollToItem(0)
        }
    }
    LaunchedEffect(state.isHome) {
        if (!state.isHome) homeSearchExpanded = false
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (useSideNavigation) {
            PlexNavigationSidebar(
                state = state,
                homeFocusRequester = homeActionFocusRequester,
                onHome = onHome,
                onSearch = {
                    homeSearchExpanded = true
                    if (!state.isHome) onHome()
                },
                onSelectSection = onSelectSection,
                onRefresh = onRefresh,
                onShowLibraryOrder = onShowLibraryOrder,
                onAccount = onAccount,
            )
        }
        Scaffold(
        modifier = Modifier.weight(1f),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = .96f),
                ),
                navigationIcon = {
                    if (state.canNavigateBack) {
                        IconButton(onClick = { onBack() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로")
                        }
                    } else {
                        Box(
                            Modifier
                                .padding(start = 16.dp)
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(PlexGold),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                null,
                                tint = Color(0xFF1B1200),
                                modifier = Modifier.size(25.dp),
                            )
                        }
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.isHome) "PLEX" else state.title,
                                color = if (state.isHome) PlexGold
                                    else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (state.isHome) FontWeight.Black
                                    else FontWeight.Bold,
                                letterSpacing = if (state.isHome) 2.sp else 0.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (state.serverName.isNotBlank()) {
                                Text(
                                    text = state.serverName,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                        if (!state.isHome) {
                            Spacer(Modifier.width(12.dp))
                            LibrarySearchField(
                                query = state.query,
                                onQueryChange = onQueryChange,
                                modifier = Modifier
                                    .widthIn(min = 180.dp, max = 360.dp)
                                    .height(52.dp),
                            )
                        }
                    }
                },
                actions = {
                    if (state.isHome) {
                        IconButton(
                            onClick = {
                                if (homeSearchExpanded) onQueryChange("")
                                homeSearchExpanded = !homeSearchExpanded
                            },
                        ) {
                            Icon(
                                if (homeSearchExpanded) {
                                    Icons.Rounded.Close
                                } else {
                                    Icons.Rounded.Search
                                },
                                if (homeSearchExpanded) "검색 닫기" else "검색 열기",
                            )
                        }
                    }
                    if (!useSideNavigation) {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Rounded.Refresh, "새로고침")
                        }
                        if (state.isHome) {
                            TextButton(onClick = onAccount) {
                                Icon(
                                    Icons.Rounded.Person,
                                    null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("계정")
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!useSideNavigation && !state.canNavigateBack && !state.isHome) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.sections.forEach { section ->
                        item(key = section.key) {
                            FilterChip(
                                selected = section.key == state.selectedSection?.key,
                                onClick = { onSelectSection(section) },
                                label = { Text(section.title) },
                                leadingIcon = {
                                    Icon(
                                        when (section.type) {
                                            "movie" -> Icons.Rounded.Movie
                                            "show" -> Icons.Rounded.Tv
                                            else -> Icons.Rounded.GridView
                                        },
                                        null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                        }
                    }
                    item(key = "library_order") {
                        FilterChip(
                            selected = false,
                            onClick = onShowLibraryOrder,
                            label = { Text("순서") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Reorder,
                                    null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (state.isHome) {
                if (homeSearchExpanded || state.query.isNotBlank()) {
                    LibrarySearchField(
                        query = state.query,
                        onQueryChange = onQueryChange,
                        placeholder = "Plex 전체 검색",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(52.dp),
                    )
                }
                if (state.query.isBlank()) {
                    HomeContent(
                        state = state,
                        imageUrl = imageUrl,
                        token = token,
                        onSelectItem = onSelectItem,
                        onPlay = onPlay,
                        onSelectSection = onSelectSection,
                        onShowLibraryOrder = onShowLibraryOrder,
                    )
                } else {
                    HomeSearchResults(
                        query = state.query,
                        items = state.homeSearchResults,
                        isLoading = state.isHomeSearchLoading,
                        imageUrl = imageUrl,
                        token = token,
                        onSelectItem = onSelectItem,
                    )
                }
            } else {
                val selectedSection = state.selectedSection
                val libraryContinueItems = if (
                    !state.canNavigateBack &&
                    state.query.isBlank() &&
                    selectedSection != null
                ) {
                    state.libraryContinueRows
                        .firstOrNull { it.section.key == selectedSection.key }
                        ?.items
                        .orEmpty()
                } else {
                    emptyList()
                }
                val libraryItems = state.filteredItems

                if (
                    libraryItems.isEmpty() &&
                    libraryContinueItems.isEmpty() &&
                    !state.isLoading
                ) {
                    EmptyLibrary(Modifier.fillMaxSize())
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(142.dp),
                        state = libraryGridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 32.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        if (libraryContinueItems.isNotEmpty()) {
                            item(
                                key = "library_continue",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                ContinueWatchingRow(
                                    title = "이어보기",
                                    items = libraryContinueItems,
                                    imageUrl = imageUrl,
                                    token = token,
                                    onSelectItem = onSelectItem,
                                    onPlay = onPlay,
                                )
                            }
                        }
                        if (libraryItems.isNotEmpty()) {
                            item(
                                key = "library_all_header",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                LibraryGridSectionHeader(
                                    title = if (state.query.isBlank()) {
                                        "전체 목록"
                                    } else {
                                        "검색 결과"
                                    },
                                    count = libraryItems.size,
                                    sortedLabel = "가나다순",
                                )
                            }
                        }
                        items(
                            items = libraryItems,
                            key = { "${it.type}-${it.ratingKey}-${it.key}" },
                        ) { item ->
                            MediaCard(
                                item = item,
                                imageUrl = imageUrl(item.thumb),
                                token = token,
                                onClick = { onSelectItem(item) },
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun PlexNavigationSidebar(
    state: PlexUiState,
    homeFocusRequester: FocusRequester,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onSelectSection: (PlexSection) -> Unit,
    onRefresh: () -> Unit,
    onShowLibraryOrder: () -> Unit,
    onAccount: () -> Unit,
) {
    var containsFocus by remember { mutableStateOf(false) }
    val sidebarWidth by animateDpAsState(
        targetValue = if (containsFocus) 232.dp else 80.dp,
        animationSpec = tween(durationMillis = 180),
        label = "plex_sidebar_width",
    )
    Surface(
        modifier = Modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .animateContentSize()
            .onFocusChanged { containsFocus = it.hasFocus || it.isFocused },
        color = Color(0xFF111214),
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PlexGold),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF171000),
                        modifier = Modifier.size(29.dp),
                    )
                }
                AnimatedVisibility(visible = containsFocus) {
                    Text(
                        text = "PLEX",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "nav_home") {
                    PlexSidebarItem(
                        label = "홈",
                        icon = Icons.Rounded.Home,
                        selected = state.isHome,
                        expanded = containsFocus,
                        onClick = onHome,
                        modifier = Modifier.focusRequester(homeFocusRequester),
                    )
                }
                item(key = "nav_search") {
                    PlexSidebarItem(
                        label = "검색",
                        icon = Icons.Rounded.Search,
                        selected = state.isHome && state.query.isNotBlank(),
                        expanded = containsFocus,
                        onClick = onSearch,
                    )
                }
                item(key = "nav_library_heading") {
                    AnimatedVisibility(visible = containsFocus) {
                        Text(
                            text = "내 미디어",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 14.dp, top = 13.dp, bottom = 5.dp),
                        )
                    }
                }
                state.sections.forEach { section ->
                    item(key = "nav_section_${section.key}") {
                        PlexSidebarItem(
                            label = section.title,
                            icon = when (section.type) {
                                "movie" -> Icons.Rounded.Movie
                                "show" -> Icons.Rounded.Tv
                                "artist" -> Icons.Rounded.MusicNote
                                "photo" -> Icons.Rounded.PhotoLibrary
                                else -> Icons.Rounded.GridView
                            },
                            selected = !state.isHome &&
                                section.key == state.selectedSection?.key,
                            expanded = containsFocus,
                            onClick = { onSelectSection(section) },
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = .09f)),
            )
            Spacer(Modifier.height(8.dp))
            PlexSidebarItem(
                label = "새로고침",
                icon = Icons.Rounded.Refresh,
                expanded = containsFocus,
                onClick = onRefresh,
            )
            PlexSidebarItem(
                label = "라이브러리 순서",
                icon = Icons.Rounded.Reorder,
                expanded = containsFocus,
                onClick = onShowLibraryOrder,
            )
            PlexSidebarItem(
                label = "계정",
                icon = Icons.Rounded.Person,
                expanded = containsFocus,
                onClick = onAccount,
            )
        }
    }
}

@Composable
private fun PlexSidebarItem(
    label: String,
    icon: ImageVector,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = when {
            focused -> Color.White.copy(alpha = .17f)
            selected -> PlexGold.copy(alpha = .17f)
            else -> Color.Transparent
        },
        border = if (focused) {
            androidx.compose.foundation.BorderStroke(2.dp, Color.White)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) PlexGold else Color.White.copy(alpha = .9f),
                modifier = Modifier.size(24.dp),
            )
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun OttTopActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        modifier = modifier
            .height(42.dp)
            .scale(if (focused) 1.08f else 1f)
            .onFocusChanged { focused = it.isFocused || it.hasFocus },
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) PlexGold else Color(0xFF242424),
            contentColor = if (focused) Color(0xFF171000) else Color.White,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (focused) 3.dp else 1.dp,
            color = when {
                focused -> Color.White
                selected -> PlexGold
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f)
            },
        ),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontWeight = if (focused || selected) FontWeight.Black else FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "검색",
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(999.dp),
        textStyle = MaterialTheme.typography.bodySmall,
        placeholder = {
            Text(
                text = placeholder,
                fontSize = 12.sp,
            )
        },
        leadingIcon = {
            Icon(
                Icons.Rounded.Search,
                null,
                modifier = Modifier.size(18.dp),
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Rounded.Close,
                        "검색어 지우기",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun HomeSearchResults(
    query: String,
    items: List<PlexItem>,
    isLoading: Boolean,
    imageUrl: (String?) -> String?,
    token: String,
    onSelectItem: (PlexItem) -> Unit,
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }
    if (items.isEmpty()) {
        EmptyLibrary(Modifier.fillMaxSize())
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(142.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 32.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(
            key = "home_search_header",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            LibraryGridSectionHeader(
                title = "‘${query.trim()}’ 검색 결과",
                count = items.size,
                sortedLabel = "Plex 전체",
            )
        }
        items(
            items = items,
            key = { "search-${it.type}-${it.ratingKey}-${it.key}" },
        ) { item ->
            MediaCard(
                item = item,
                imageUrl = imageUrl(item.thumb),
                token = token,
                onClick = { onSelectItem(item) },
            )
        }
    }
}

@Composable
private fun LibraryGridSectionHeader(
    title: String,
    count: Int,
    sortedLabel: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(PlexGold.copy(alpha = .42f)),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = PlexGold.copy(alpha = .18f),
            ) {
                Text(
                    text = sortedLabel,
                    color = PlexGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "${count}개",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AccountDialog(
    serverName: String,
    serverVersion: String,
    serverUrl: String,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plex 계정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "로그인됨",
                    color = PlexGold,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = serverName.ifBlank { "Plex Media Server" },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (serverVersion.isNotBlank()) {
                    Text(
                        text = "서버 버전 $serverVersion",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    text = serverUrl,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("로그아웃")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        },
    )
}

@Composable
private fun LibraryOrderDialog(
    sections: List<PlexSection>,
    onMove: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Black,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("라이브러리 순서") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                sections.forEachIndexed { index, section ->
                    item(key = section.key) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF111111),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = section.title,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                TextButton(
                                    onClick = { onMove(index, 0) },
                                    enabled = index > 0,
                                ) {
                                    Text("맨 앞")
                                }
                                IconButton(
                                    onClick = { onMove(index, index - 1) },
                                    enabled = index > 0,
                                ) {
                                    Icon(Icons.Rounded.ArrowUpward, "위로")
                                }
                                IconButton(
                                    onClick = { onMove(index, index + 1) },
                                    enabled = index < sections.lastIndex,
                                ) {
                                    Icon(Icons.Rounded.ArrowDownward, "아래로")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("완료")
            }
        },
    )
}

@Composable
private fun HomeContent(
    state: PlexUiState,
    imageUrl: (String?) -> String?,
    token: String,
    onSelectItem: (PlexItem) -> Unit,
    onPlay: (PlexItem) -> Unit,
    onSelectSection: (PlexSection) -> Unit,
    onShowLibraryOrder: () -> Unit,
) {
    if (
        state.continueWatching.isEmpty() &&
        state.libraryWatchedRows.isEmpty() &&
        state.libraryContinueRows.isEmpty() &&
        state.homeRows.isEmpty() &&
        state.sections.isEmpty() &&
        !state.isLoading
    ) {
        EmptyLibrary(Modifier.fillMaxSize())
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (state.continueWatching.isNotEmpty()) {
            item(key = "continue_watching") {
                ContinueWatchingRow(
                    items = state.continueWatching,
                    imageUrl = imageUrl,
                    token = token,
                    onSelectItem = onSelectItem,
                    onPlay = onPlay,
                )
            }
        }
        if (state.sections.isNotEmpty()) {
            item(key = "browse_libraries") {
                BrowseLibrariesRow(
                    sections = state.sections,
                    serverName = state.serverName,
                    onSelectSection = onSelectSection,
                    onShowLibraryOrder = onShowLibraryOrder,
                )
            }
        }
        state.sections.forEach { section ->
            state.homeRows
                .firstOrNull { it.section.key == section.key }
                ?.let { row ->
                    item(key = "recent_${section.key}") {
                        HomeMediaRow(
                            title = "${section.title}의 최근 추가",
                            items = row.items,
                            imageUrl = imageUrl,
                            token = token,
                            onSelectItem = onSelectItem,
                            onTitleClick = { onSelectSection(section) },
                        )
                    }
                }
            }
    }
}

@Composable
private fun ContinueWatchingRow(
    title: String = "이어보기",
    items: List<PlexItem>,
    imageUrl: (String?) -> String?,
    token: String,
    onSelectItem: (PlexItem) -> Unit,
    onPlay: (PlexItem) -> Unit,
    onTitleClick: (() -> Unit)? = null,
) {
    val cardWidth = if (LocalConfiguration.current.screenWidthDp >= 840) 282.dp else 238.dp
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeRowTitle(
            title = title,
            actionLabel = if (onTitleClick != null) "전체 보기" else null,
            onAction = onTitleClick,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.forEach { item ->
                item(key = "continue-${item.ratingKey}-${item.key}") {
                    var focused by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .width(cardWidth)
                            .scale(if (focused) 1.035f else 1f)
                            .onFocusChanged { focused = it.isFocused || it.hasFocus }
                            .clickable { onSelectItem(item) },
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .then(
                                    homeCardBorder(
                                        item = item,
                                        focused = focused,
                                        shape = RoundedCornerShape(8.dp),
                                    ),
                                ),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = if (focused) 14.dp else 3.dp,
                            ),
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                PlexImage(
                                    url = imageUrl(item.art ?: item.thumb),
                                    token = token,
                                    contentDescription = item.title,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = .86f),
                                                ),
                                            ),
                                        ),
                                )
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .clickable { onPlay(item) },
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = .62f),
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        null,
                                        tint = Color.White,
                                        modifier = Modifier.padding(8.dp).size(28.dp),
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { item.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .align(Alignment.BottomCenter),
                                    color = PlexGold,
                                    trackColor = Color.White.copy(alpha = .22f),
                                )
                                WatchedBadge(
                                    watched = item.isPlayable && item.isWatched,
                                    modifier = Modifier.align(Alignment.TopStart),
                                )
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = item.title,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.subtitle ?: formatDuration(item.viewOffsetMs),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseLibrariesRow(
    sections: List<PlexSection>,
    serverName: String,
    onSelectSection: (PlexSection) -> Unit,
    onShowLibraryOrder: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeRowTitle(
            title = "라이브러리 둘러보기",
            actionLabel = "순서 편집",
            onAction = onShowLibraryOrder,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            sections.forEach { section ->
                item(key = "library-${section.key}") {
                    var focused by remember { mutableStateOf(false) }
                    Surface(
                        modifier = Modifier
                            .width(190.dp)
                            .height(76.dp)
                            .scale(if (focused) 1.04f else 1f)
                            .onFocusChanged { focused = it.isFocused || it.hasFocus }
                            .clickable { onSelectSection(section) },
                        shape = RoundedCornerShape(9.dp),
                        color = if (focused) {
                            Color(0xFF34363C)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = if (focused) {
                            androidx.compose.foundation.BorderStroke(3.dp, Color.White)
                        } else {
                            null
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(PlexGold.copy(alpha = .16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = when (section.type) {
                                        "movie" -> Icons.Rounded.Movie
                                        "show" -> Icons.Rounded.Tv
                                        "artist" -> Icons.Rounded.MusicNote
                                        "photo" -> Icons.Rounded.PhotoLibrary
                                        else -> Icons.Rounded.GridView
                                    },
                                    contentDescription = null,
                                    tint = PlexGold,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = section.title,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = serverName,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeRowTitle(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier)
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                color = PlexGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                tint = PlexGold,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun HomeMediaRow(
    title: String,
    items: List<PlexItem>,
    imageUrl: (String?) -> String?,
    token: String,
    onSelectItem: (PlexItem) -> Unit,
    onTitleClick: (() -> Unit)? = null,
) {
    val cardWidth = if (LocalConfiguration.current.screenWidthDp >= 840) 166.dp else 142.dp
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeRowTitle(
            title = title,
            actionLabel = if (onTitleClick != null) "전체 보기" else null,
            onAction = onTitleClick,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.forEach { item ->
                item(key = "${item.type}-${item.ratingKey}-${item.key}") {
                    MediaCard(
                        item = item,
                        imageUrl = imageUrl(item.thumb),
                        token = token,
                        modifier = Modifier.width(cardWidth),
                        onClick = { onSelectItem(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaCard(
    item: PlexItem,
    imageUrl: String?,
    token: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(if (focused) 1.045f else 1f)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable(onClick = onClick),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(.68f)
                .then(
                    homeCardBorder(
                        item = item,
                        focused = focused,
                        shape = RoundedCornerShape(8.dp),
                    ),
                ),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (focused) 14.dp else 3.dp,
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
                PlexImage(
                    url = imageUrl,
                    token = token,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                )
                if (imageUrl == null) {
                    Icon(
                        if (item.isPlayable) Icons.Rounded.Movie else Icons.Rounded.Folder,
                        null,
                        modifier = Modifier.align(Alignment.Center).size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f),
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = .8f)),
                            ),
                        ),
                )
                if (item.unwatchedEpisodeCount > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(7.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = PlexGold,
                        shadowElevation = 5.dp,
                    ) {
                        Text(
                            text = if (item.unwatchedEpisodeCount > 999) {
                                "999+"
                            } else {
                                item.unwatchedEpisodeCount.toString()
                            },
                            color = Color(0xFF171000),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(
                                horizontal = 7.dp,
                                vertical = 3.dp,
                            ),
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(7.dp),
                        color = Color.Black.copy(alpha = .68f),
                    ) {
                        Text(
                            text = typeLabel(item.type),
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(
                                horizontal = 7.dp,
                                vertical = 4.dp,
                            ),
                        )
                    }
                }
                if (item.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter),
                        color = PlexGold,
                        trackColor = Color.White.copy(alpha = .22f),
                    )
                }
                WatchedBadge(
                    watched = item.isPlayable && item.isWatched,
                    modifier = Modifier.align(Alignment.TopStart),
                )
                MediaFeatureBadges(
                    item = item,
                    compact = true,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title,
            color = if (focused) Color.White else Color.Unspecified,
            fontWeight = if (focused) FontWeight.Black else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.subtitle
                ?: item.year?.toString()
                ?: childLabel(item),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val HomeFocusColor = Color.White

private fun homeCardBorder(
    item: PlexItem,
    focused: Boolean,
    shape: Shape,
): Modifier =
    if (focused) {
        Modifier.border(
            width = 3.dp,
            color = HomeFocusColor,
            shape = shape,
        )
    } else if (item.isPlayable && item.isWatched) {
        Modifier.border(
            width = 3.dp,
            color = PlexGold,
            shape = shape,
        )
    } else {
        Modifier
    }

@Composable
private fun WatchedBadge(
    watched: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!watched) return
    Surface(
        modifier = modifier.padding(8.dp),
        shape = RoundedCornerShape(999.dp),
        color = PlexGold.copy(alpha = .96f),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = "보았음",
                color = Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.GridView,
            null,
            modifier = Modifier.size(58.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f),
        )
        Spacer(Modifier.height(12.dp))
        Text("표시할 콘텐츠가 없습니다", fontWeight = FontWeight.SemiBold)
        Text(
            "검색어를 지우거나 Plex 라이브러리를 확인해 주세요.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ConnectionScreen(
    isLoading: Boolean,
    canCancel: Boolean,
    onConnect: (String, String) -> Unit,
    onCancel: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val canSubmit = username.isNotBlank() &&
        password.isNotBlank() &&
        !isLoading

    fun submitLogin() {
        if (!canSubmit) return
        focusManager.clearFocus()
        onConnect(username.trim(), password)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        PlexGold.copy(alpha = .16f),
                        MaterialTheme.colorScheme.background,
                    ),
                    radius = 900f,
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(PlexGold),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    null,
                    tint = Color(0xFF1B1200),
                    modifier = Modifier.size(54.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("Plex Play Universal", fontSize = 32.sp, fontWeight = FontWeight.Black)
            Text(
                "내 Plex 서버, 내 손안의 플레이어",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
                ),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Plex 계정 로그인", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "로그인 후 계정에 등록된 Plex 서버를 자동으로 찾아 연결합니다.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Plex 사용자명 또는 이메일") },
                        leadingIcon = { Icon(Icons.Rounded.Person, null) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Plex 비밀번호") },
                        leadingIcon = { Icon(Icons.Rounded.Key, null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Rounded.VisibilityOff
                                    else Icons.Rounded.Visibility,
                                    if (passwordVisible) "비밀번호 숨기기" else "비밀번호 보기",
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { submitLogin() },
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Lock,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "비밀번호는 저장하지 않습니다. 발급된 토큰만 암호화해 보관합니다.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    Button(
                        onClick = ::submitLogin,
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("로그인 및 서버 자동 연결", fontWeight = FontWeight.Bold)
                    }
                    if (canCancel) {
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("취소")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemSheet(
    item: PlexItem,
    canBrowse: Boolean,
    posterUrl: String?,
    backdropUrl: String?,
    token: String,
    canRemoveFromContinue: Boolean,
    actorWorks: List<PlexItem>,
    similarGenreWorks: List<PlexItem>,
    isRelatedLoading: Boolean,
    relatedImageUrl: (String?) -> String?,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onBrowse: () -> Unit,
    onMarkWatched: () -> Unit,
    onMarkUnwatched: () -> Unit,
    onRemoveFromContinue: () -> Unit,
    onSelectRelated: (PlexItem) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val wideLayout = configuration.screenWidthDp >= 720
    val detailListState = rememberLazyListState()
    val hasPrimaryAction = item.isPlayable || canBrowse
    val backFocusRequester = remember(item.ratingKey) { FocusRequester() }
    val primaryActionFocusRequester = remember(item.ratingKey) { FocusRequester() }
    var primaryActionFocused by remember(item.ratingKey) { mutableStateOf(false) }
    LaunchedEffect(item.ratingKey, hasPrimaryAction) {
        detailListState.scrollToItem(0)
        delay(120)
        runCatching {
            if (hasPrimaryAction) {
                primaryActionFocusRequester.requestFocus()
            } else {
                backFocusRequester.requestFocus()
            }
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            Box(Modifier.fillMaxSize()) {
                PlexImage(
                    url = backdropUrl,
                    token = token,
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (wideLayout) 520.dp else 340.dp)
                        .align(Alignment.TopCenter)
                        .alpha(.58f),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                .34f to Color.Black.copy(alpha = .25f),
                                .68f to Color.Black.copy(alpha = .94f),
                                1f to Color.Black,
                            ),
                        ),
                )
                if (wideLayout) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = .92f),
                                        Color.Black.copy(alpha = .28f),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .size(48.dp)
                        .zIndex(2f)
                        .focusRequester(backFocusRequester)
                        .focusProperties {
                            if (hasPrimaryAction) {
                                down = primaryActionFocusRequester
                            }
                        }
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = .7f)),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "상세 화면 닫기",
                        tint = Color.White,
                    )
                }
                LazyColumn(
                    state = detailListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(
                        start = if (wideLayout) 54.dp else 22.dp,
                        end = if (wideLayout) 54.dp else 22.dp,
                        bottom = 36.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "detail_spacer") {
                        Spacer(Modifier.height(if (wideLayout) 270.dp else 210.dp))
                    }
                    item(key = "detail_content") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(22.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            if (wideLayout && posterUrl != null) {
                                Card(
                                    modifier = Modifier
                                        .width(150.dp)
                                        .aspectRatio(.68f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF24262B),
                                    ),
                                ) {
                                    PlexImage(
                                        url = posterUrl,
                                        token = token,
                                        contentDescription = item.title,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.widthIn(max = 820.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                WatchedBadge(
                                    watched = item.isPlayable && item.isWatched,
                                    modifier = Modifier,
                                )
                                Text(
                                    text = item.title,
                                    color = Color.White,
                                    fontSize = if (wideLayout) 38.sp else 29.sp,
                                    lineHeight = if (wideLayout) 42.sp else 33.sp,
                                    fontWeight = FontWeight.Black,
                                )
                                val metadata = listOfNotNull(
                                    item.year?.toString(),
                                    formatDuration(item.durationMs)
                                        .takeIf { item.durationMs > 0 },
                                    item.subtitle,
                                    typeLabel(item.type),
                                ).joinToString("  ·  ")
                                Text(
                                    text = metadata,
                                    color = Color.White.copy(alpha = .74f),
                                    fontSize = 14.sp,
                                )
                                MediaFeatureBadges(item = item)
                                val videoDescription = formatVideoDescription(item)
                                val audioDescription = formatAudioDescription(item)
                                if (videoDescription != null || audioDescription != null) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        videoDescription?.let {
                                            MediaTypeLine(label = "비디오", value = it)
                                        }
                                        audioDescription?.let {
                                            MediaTypeLine(label = "오디오", value = it)
                                        }
                                    }
                                }
                                PlaybackCompatibilityLine(item = item)
                                if (!item.summary.isNullOrBlank()) {
                                    Text(
                                        text = item.summary,
                                        color = Color.White.copy(alpha = .82f),
                                        lineHeight = 22.sp,
                                        maxLines = if (wideLayout) 4 else 5,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (item.progress > 0f) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(Modifier.fillMaxWidth()) {
                                            Text(
                                                text = "${formatDuration(item.viewOffsetMs)}부터 이어보기",
                                                color = Color.White.copy(alpha = .8f),
                                                fontSize = 12.sp,
                                            )
                                            Spacer(Modifier.weight(1f))
                                            Text(
                                                text = "${(item.progress * 100).toInt()}%",
                                                color = Color.White.copy(alpha = .65f),
                                                fontSize = 12.sp,
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { item.progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(CircleShape),
                                            color = PlexGold,
                                            trackColor = Color.White.copy(alpha = .2f),
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    if (hasPrimaryAction) {
                                        Button(
                                            onClick = if (item.isPlayable) onPlay else onBrowse,
                                            modifier = Modifier
                                                .height(50.dp)
                                                .scale(if (primaryActionFocused) 1.06f else 1f)
                                                .focusRequester(primaryActionFocusRequester)
                                                .focusProperties {
                                                    up = backFocusRequester
                                                }
                                                .onFocusChanged {
                                                    primaryActionFocused =
                                                        it.isFocused || it.hasFocus
                                                },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (primaryActionFocused) {
                                                    PlexGold
                                                } else {
                                                    Color.White
                                                },
                                                contentColor = Color.Black,
                                            ),
                                        ) {
                                            Icon(
                                                if (item.isPlayable) Icons.Rounded.PlayArrow
                                                else Icons.Rounded.Folder,
                                                contentDescription = null,
                                            )
                                            Spacer(Modifier.width(7.dp))
                                            Text(
                                                text = when {
                                                    !item.isPlayable -> "목록 보기"
                                                    item.progress > 0 -> "이어보기"
                                                    else -> "재생"
                                                },
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = if (item.isWatched) {
                                            onMarkUnwatched
                                        } else {
                                            onMarkWatched
                                        },
                                        modifier = Modifier.height(50.dp),
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Icon(
                                            if (item.isWatched) Icons.Rounded.VisibilityOff
                                            else Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(19.dp),
                                        )
                                        Spacer(Modifier.width(7.dp))
                                        Text(
                                            if (item.isWatched) {
                                                "시청하지 않음으로 표시"
                                            } else {
                                                "시청 완료로 표시"
                                            },
                                        )
                                    }
                                }
                                if (canRemoveFromContinue) {
                                    TextButton(onClick = onRemoveFromContinue) {
                                        Text(
                                            text = "이어보기에서 제거",
                                            color = Color.White.copy(alpha = .78f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (isRelatedLoading) {
                        item(key = "related_loading") {
                            Row(
                                modifier = Modifier.padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = "관련 작품을 찾는 중…",
                                    color = Color.White.copy(alpha = .7f),
                                )
                            }
                        }
                    }
                    if (actorWorks.isNotEmpty()) {
                        item(key = "actor_works") {
                            RelatedMediaRow(
                                title = "출연 배우의 다른 작품",
                                items = actorWorks,
                                imageUrl = relatedImageUrl,
                                token = token,
                                onSelectItem = onSelectRelated,
                            )
                        }
                    }
                    if (similarGenreWorks.isNotEmpty()) {
                        item(key = "genre_works") {
                            RelatedMediaRow(
                                title = "비슷한 장르의 작품",
                                items = similarGenreWorks,
                                imageUrl = relatedImageUrl,
                                token = token,
                                onSelectItem = onSelectRelated,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegacyItemSheet(
    item: PlexItem,
    imageUrl: String?,
    token: String,
    canRemoveFromContinue: Boolean,
    actorWorks: List<PlexItem>,
    similarGenreWorks: List<PlexItem>,
    isRelatedLoading: Boolean,
    relatedImageUrl: (String?) -> String?,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onBrowse: () -> Unit,
    onMarkWatched: () -> Unit,
    onMarkUnwatched: () -> Unit,
    onRemoveFromContinue: () -> Unit,
    onSelectRelated: (PlexItem) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(210.dp),
            ) {
                PlexImage(
                    url = imageUrl,
                    token = token,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = .12f),
                                    MaterialTheme.colorScheme.surface,
                                ),
                            ),
                        ),
                )
                WatchedBadge(
                    watched = item.isPlayable && item.isWatched,
                    modifier = Modifier.align(Alignment.TopStart),
                )
                MediaFeatureBadges(
                    item = item,
                    compact = true,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
                )
            }
            Column(
                Modifier.padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = item.title,
                    fontSize = 27.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Black,
                )
                val metadata = listOfNotNull(
                    typeLabel(item.type),
                    item.year?.toString(),
                    formatDuration(item.durationMs).takeIf { item.durationMs > 0 },
                    item.subtitle,
                ).joinToString("  ·  ")
                Text(
                    metadata,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                MediaFeatureBadges(item = item)
                formatVideoDescription(item)?.let {
                    MediaTypeLine(label = "비디오", value = it)
                }
                formatAudioDescription(item)?.let {
                    MediaTypeLine(label = "오디오", value = it)
                }
                PlaybackCompatibilityLine(item = item)
                if (!item.summary.isNullOrBlank()) {
                    Text(
                        text = item.summary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.progress > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                "이어보기",
                                color = PlexGold,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                formatDuration(item.viewOffsetMs),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { item.progress },
                            modifier = Modifier.fillMaxWidth().clip(CircleShape),
                            color = PlexGold,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (item.isPlayable) {
                        Button(
                            onClick = onPlay,
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(13.dp),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (item.progress > 0) "이어보기" else "재생")
                        }
                    } else {
                        Button(
                            onClick = onBrowse,
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(13.dp),
                        ) {
                            Icon(Icons.Rounded.Folder, null)
                            Spacer(Modifier.width(6.dp))
                            Text("목록 보기")
                        }
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(50.dp),
                        shape = RoundedCornerShape(13.dp),
                    ) {
                        Text("닫기")
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onMarkWatched,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp),
                    ) {
                        Text("시청 완료")
                    }
                    OutlinedButton(
                        onClick = onMarkUnwatched,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp),
                    ) {
                        Text("시청하지 않음으로 표시")
                    }
                }
                if (canRemoveFromContinue) {
                    OutlinedButton(
                        onClick = onRemoveFromContinue,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp),
                    ) {
                        Text("이어보기에서 제거")
                    }
                }
                if (isRelatedLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            "배우·장르 관련 작품을 찾는 중…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                }
                if (actorWorks.isNotEmpty()) {
                    RelatedMediaRow(
                        title = "출연 배우의 다른 작품",
                        items = actorWorks,
                        imageUrl = relatedImageUrl,
                        token = token,
                        onSelectItem = onSelectRelated,
                    )
                }
                if (similarGenreWorks.isNotEmpty()) {
                    RelatedMediaRow(
                        title = "비슷한 장르의 작품",
                        items = similarGenreWorks,
                        imageUrl = relatedImageUrl,
                        token = token,
                        onSelectItem = onSelectRelated,
                    )
                }
            }
        }
    }
}

@Composable
private fun RelatedMediaRow(
    title: String,
    items: List<PlexItem>,
    imageUrl: (String?) -> String?,
    token: String,
    onSelectItem: (PlexItem) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { relatedItem ->
                item(key = "related-${relatedItem.type}-${relatedItem.ratingKey}") {
                    MediaCard(
                        item = relatedItem,
                        imageUrl = imageUrl(relatedItem.thumb),
                        token = token,
                        modifier = Modifier.width(126.dp),
                        onClick = { onSelectItem(relatedItem) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlexImage(
    url: String?,
    token: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (url == null) return
    val context = LocalContext.current
    val request = remember(url, token) {
        ImageRequest.Builder(context)
            .data(url)
            .httpHeaders(
                NetworkHeaders.Builder()
                    .set("X-Plex-Token", token)
                    .build(),
            )
            .crossfade(true)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

private fun typeLabel(type: String): String = when (type) {
    "movie" -> "영화"
    "show" -> "프로그램"
    "season" -> "시즌"
    "episode" -> "에피소드"
    "artist" -> "아티스트"
    "album" -> "앨범"
    "track" -> "음악"
    "photo" -> "사진"
    else -> type.ifBlank { "미디어" }
}

private fun childLabel(item: PlexItem): String = when {
    item.leafCount > 0 -> "${item.leafCount}개 항목"
    item.childCount > 0 -> "${item.childCount}개 항목"
    else -> typeLabel(item.type)
}

@Composable
private fun MediaTypeLine(
    label: String,
    value: String,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(54.dp),
            color = PlexGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            color = Color.White.copy(alpha = .86f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MediaFeatureBadges(
    item: PlexItem,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val labels = remember(item) {
        mediaFeatureLabels(item).let { values ->
            if (compact) values.take(2) else values.take(5)
        }
    }
    if (labels.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp),
    ) {
        labels.chunked(if (compact) 2 else 3).forEach { rowLabels ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(
                    if (compact) 3.dp else 6.dp,
                ),
            ) {
                rowLabels.forEach { label ->
                    Surface(
                        shape = RoundedCornerShape(if (compact) 4.dp else 6.dp),
                        color = if (compact) {
                            Color.Black.copy(alpha = .78f)
                        } else {
                            Color.White.copy(alpha = .12f)
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (compact) 0.5.dp else 1.dp,
                            color = PlexGold.copy(
                                alpha = if (compact) .68f else .9f,
                            ),
                        ),
                    ) {
                        Text(
                            text = label,
                            color = if (compact) Color.White else PlexGold,
                            fontSize = if (compact) 8.sp else 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(
                                horizontal = if (compact) 4.dp else 7.dp,
                                vertical = if (compact) 2.dp else 4.dp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackCompatibilityLine(
    item: PlexItem,
    modifier: Modifier = Modifier,
) {
    if (
        !item.isPlayable ||
        (item.videoCodec.isNullOrBlank() && item.audioCodec.isNullOrBlank())
    ) {
        return
    }
    val context = LocalContext.current
    val compatibility = remember(context, item) {
        detectDevicePlaybackCompatibility(context, item)
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    if (compatibility.directPlaybackSupported) {
                        Color(0xFF52D681)
                    } else {
                        PlexGold
                    },
                ),
        )
        Text(
            text = compatibility.label,
            color = Color.White.copy(alpha = .72f),
            fontSize = 11.sp,
        )
    }
}

private fun formatVideoDescription(item: PlexItem): String? {
    val values = listOfNotNull(
        formatVideoResolution(item.videoResolution),
        formatVideoCodec(item.videoCodec),
        item.videoBitDepth?.takeIf { it >= 10 }?.let { "$it-bit" },
        item.container?.trim()?.takeIf { it.isNotBlank() }?.uppercase(),
    ).distinct()
    return values.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
}

private fun formatAudioDescription(item: PlexItem): String? {
    val values = listOfNotNull(
        formatAudioCodec(item.audioCodec),
        formatAudioChannels(item.audioChannels),
        formatAudioLanguage(item.audioLanguage),
    ).distinct()
    return values.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
}

private fun formatVideoResolution(value: String?): String? {
    val normalized = value?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
    return when (normalized) {
        "4k", "uhd" -> "4K UHD"
        "2k" -> "2K"
        "1080", "1080p" -> "1080p"
        "720", "720p" -> "720p"
        "576", "576p" -> "576p"
        "480", "480p", "sd" -> "480p"
        else -> normalized.replace('x', '×').uppercase()
    }
}

private fun formatVideoCodec(value: String?): String? {
    val normalized = value?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
    return when (normalized) {
        "h264", "avc", "avc1" -> "H.264/AVC"
        "hevc", "h265", "hev1", "hvc1" -> "H.265/HEVC"
        "av1", "av01" -> "AV1"
        "vp9", "vp09" -> "VP9"
        "mpeg2video", "mpeg2" -> "MPEG-2"
        "mpeg4", "mp4v" -> "MPEG-4"
        "vc1", "vc-1" -> "VC-1"
        "wmv3" -> "WMV"
        "prores" -> "ProRes"
        else -> normalized.uppercase()
    }
}

private fun formatAudioCodec(value: String?): String? {
    val normalized = value?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
    return when (normalized) {
        "aac", "aac_latm" -> "AAC"
        "ac3" -> "Dolby Digital (AC-3)"
        "eac3", "e-ac-3" -> "Dolby Digital Plus (E-AC-3)"
        "truehd" -> "Dolby TrueHD"
        "dca", "dts" -> "DTS"
        "dtshd", "dts-hd", "dts_hd" -> "DTS-HD"
        "flac" -> "FLAC"
        "alac" -> "ALAC"
        "mp3" -> "MP3"
        "opus" -> "Opus"
        "vorbis" -> "Vorbis"
        "pcm", "pcm_s16le", "pcm_s24le" -> "PCM"
        else -> normalized.uppercase()
    }
}

private fun formatAudioChannels(channels: Int?): String? = when (channels) {
    null -> null
    1 -> "모노"
    2 -> "2.0 채널"
    6 -> "5.1 채널"
    8 -> "7.1 채널"
    else -> "$channels 채널"
}

private fun formatAudioLanguage(value: String?): String? {
    val normalized = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return when (normalized.lowercase()) {
        "ko", "kor", "korean" -> "한국어"
        "en", "eng", "english" -> "영어"
        "ja", "jpn", "japanese" -> "일본어"
        "zh", "zho", "chi", "chinese" -> "중국어"
        else -> normalized
    }
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}시간 ${minutes}분" else "${minutes}분"
}
