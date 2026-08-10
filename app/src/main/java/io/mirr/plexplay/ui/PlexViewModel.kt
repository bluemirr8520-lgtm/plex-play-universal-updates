package io.mirr.plexplay.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.mirr.plexplay.data.PlaybackSource
import io.mirr.plexplay.data.PlaybackQuality
import io.mirr.plexplay.data.PlexConnection
import io.mirr.plexplay.data.PlexItem
import io.mirr.plexplay.data.PlexRepository
import io.mirr.plexplay.data.PlexSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.text.Collator
import java.util.Locale

data class HomeLibraryRow(
    val section: PlexSection,
    val items: List<PlexItem>,
)

data class PlexUiState(
    val connection: PlexConnection = PlexConnection(),
    val serverName: String = "",
    val serverVersion: String = "",
    val sections: List<PlexSection> = emptyList(),
    val continueWatching: List<PlexItem> = emptyList(),
    val libraryWatchedRows: List<HomeLibraryRow> = emptyList(),
    val libraryContinueRows: List<HomeLibraryRow> = emptyList(),
    val homeRows: List<HomeLibraryRow> = emptyList(),
    val homeSearchResults: List<PlexItem> = emptyList(),
    val isHomeSearchLoading: Boolean = false,
    val isHome: Boolean = true,
    val selectedSection: PlexSection? = null,
    val title: String = "홈",
    val items: List<PlexItem> = emptyList(),
    val query: String = "",
    val selectedItem: PlexItem? = null,
    val selectedItemCanBrowse: Boolean = false,
    val relatedActorWorks: List<PlexItem> = emptyList(),
    val relatedGenreWorks: List<PlexItem> = emptyList(),
    val isRelatedLoading: Boolean = false,
    val playback: PlaybackSource? = null,
    val hasPreviousPlayback: Boolean = false,
    val previousPlaybackTitle: String? = null,
    val hasNextPlayback: Boolean = false,
    val nextPlaybackTitle: String? = null,
    val autoPlayNext: Boolean = false,
    val isLoading: Boolean = false,
    val isSettingsVisible: Boolean = false,
    val isLibraryOrderVisible: Boolean = false,
    val isAccountVisible: Boolean = false,
    val canNavigateBack: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val playbackQuality: PlaybackQuality = PlaybackQuality.ORIGINAL,
) {
    val filteredItems: List<PlexItem>
        get() {
            val term = query.trim()
            return if (term.isBlank()) items else items.filter {
                it.title.contains(term, ignoreCase = true) ||
                    it.subtitle.orEmpty().contains(term, ignoreCase = true)
            }
        }
}

class PlexViewModel(
    private val repository: PlexRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        PlexUiState(
            connection = repository.connection(),
            playbackQuality = repository.playbackQuality(),
            autoPlayNext = repository.autoPlayNext(),
        ),
    )
    val state: StateFlow<PlexUiState> = _state.asStateFlow()

    private val history = ArrayDeque<HistoryEntry>()
    private var loadingJob: Job? = null
    private var homeSearchJob: Job? = null
    private var relatedJob: Job? = null
    private var playingItem: PlexItem? = null
    private var playbackQueue: List<PlexItem> = emptyList()
    private var playbackQueueIndex: Int = -1

    init {
        if (_state.value.connection.isConfigured) refresh()
    }

    fun connect(
        username: String,
        password: String,
    ) {
        loadingJob?.cancel()
        loadingJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                repository.signInAndConnect(
                    username = username,
                    password = password,
                )
            }
                .onSuccess { (connection, server) ->
                    repository.saveConnection(connection)
                    _state.update {
                        it.copy(
                            connection = repository.connection(),
                            serverName = server.name,
                            serverVersion = server.version,
                            isSettingsVisible = false,
                        )
                    }
                    loadSectionsAndHome()
                }
                .onFailure(::showError)
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun refresh() {
        loadingJob?.cancel()
        loadingJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val server = repository.connect()
                _state.update {
                    it.copy(serverName = server.name, serverVersion = server.version)
                }
                loadSectionsAndHome()
            } catch (error: Throwable) {
                showError(error)
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun loadSectionsAndHome() {
        val sections = applySavedOrder(repository.sections())
        loadHomeInternal(sections)
    }

    private fun applySavedOrder(sections: List<PlexSection>): List<PlexSection> {
        val savedOrder = repository.libraryOrder()
        if (savedOrder.isEmpty()) return sections
        return sections.sortedBy { section ->
            savedOrder.indexOf(section.key).takeIf { it >= 0 } ?: Int.MAX_VALUE
        }
    }

    private suspend fun loadHomeInternal(sections: List<PlexSection>) =
        supervisorScope {
            homeSearchJob?.cancel()
            val continueDeferred = async {
                runCatching { repository.onDeck() }.getOrDefault(emptyList())
            }
            val rowDeferred = sections.map { section ->
                async {
                    val watchedRow = HomeLibraryRow(
                        section = section,
                        items = runCatching {
                            repository.watched(section)
                        }.getOrDefault(emptyList()),
                    )
                    val continueRow = HomeLibraryRow(
                        section = section,
                        items = runCatching {
                            repository.sectionOnDeck(section.key)
                        }.getOrDefault(emptyList()),
                    )
                    val recentRow = HomeLibraryRow(
                        section = section,
                        items = runCatching {
                            repository.recentlyAdded(section)
                        }.getOrDefault(emptyList()),
                    )
                    Triple(watchedRow, continueRow, recentRow)
                }
            }
            val continueItems = continueDeferred.await()
            val loadedRows = rowDeferred.awaitAll()
            val libraryWatchedRows = loadedRows
                .map { it.first }
                .filter { it.items.isNotEmpty() }
            val libraryContinueRows = loadedRows
                .map { it.second }
                .filter { it.items.isNotEmpty() }
            val rows = loadedRows
                .map { it.third }
                .filter { it.items.isNotEmpty() }
            history.clear()
            _state.update {
                it.copy(
                    sections = sections,
                    continueWatching = continueItems,
                    libraryWatchedRows = libraryWatchedRows,
                    libraryContinueRows = libraryContinueRows,
                    homeRows = rows,
                    homeSearchResults = emptyList(),
                    isHomeSearchLoading = false,
                    isHome = true,
                    selectedSection = null,
                    title = "홈",
                    items = emptyList(),
                    query = "",
                    selectedItem = null,
                    selectedItemCanBrowse = false,
                    canNavigateBack = false,
                )
            }
        }

    fun selectHome() {
        loadingJob?.cancel()
        loadingJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val sections = _state.value.sections.ifEmpty {
                    applySavedOrder(repository.sections())
                }
                loadHomeInternal(sections)
            } catch (error: Throwable) {
                showError(error)
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun selectSection(section: PlexSection) {
        loadingJob?.cancel()
        loadingJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, selectedItem = null) }
            try {
                history.clear()
                loadSectionInternal(section)
            } catch (error: Throwable) {
                showError(error)
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun loadSectionInternal(section: PlexSection) {
        val items = repository.sectionItems(section.key).sortedByKoreanTitle()
        _state.update {
            it.copy(
                selectedSection = section,
                isHome = false,
                title = section.title,
                items = items,
                query = "",
                canNavigateBack = false,
            )
        }
    }

    fun selectItem(item: PlexItem?) {
        relatedJob?.cancel()
        if (item == null) {
            _state.update {
                it.copy(
                    selectedItem = null,
                    selectedItemCanBrowse = false,
                    relatedActorWorks = emptyList(),
                    relatedGenreWorks = emptyList(),
                    isRelatedLoading = false,
                )
            }
            return
        }
        _state.update {
            it.copy(
                selectedItem = item,
                selectedItemCanBrowse = false,
                relatedActorWorks = emptyList(),
                relatedGenreWorks = emptyList(),
                isRelatedLoading = true,
            )
        }
        relatedJob = viewModelScope.launch {
            val detailedItem = try {
                repository.itemDetails(item)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                item
            }
            val hasKnownChildren =
                detailedItem.childCount > 0 ||
                    detailedItem.leafCount > 0 ||
                    item.childCount > 0 ||
                    item.leafCount > 0
            val canBrowse = if (detailedItem.isPlayable) {
                false
            } else {
                try {
                    repository.hasChildren(detailedItem)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    hasKnownChildren
                }
            }
            _state.update { current ->
                if (current.selectedItem?.ratingKey != item.ratingKey) {
                    current
                } else {
                    current.copy(
                        selectedItem = detailedItem,
                        selectedItemCanBrowse = canBrowse,
                    )
                }
            }
            val related = try {
                repository.relatedContent(detailedItem)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                null
            }
            _state.update { current ->
                if (current.selectedItem?.ratingKey != item.ratingKey) {
                    current
                } else {
                    current.copy(
                        relatedActorWorks = related?.actorWorks.orEmpty(),
                        relatedGenreWorks = related?.similarGenreWorks.orEmpty(),
                        isRelatedLoading = false,
                    )
                }
            }
        }
    }

    fun browse(item: PlexItem) {
        loadingJob?.cancel()
        loadingJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val current = _state.value
                val children = repository.children(item)
                history.addLast(
                    HistoryEntry(
                        title = current.title,
                        items = current.items,
                        isHome = current.isHome,
                        selectedSection = current.selectedSection,
                    ),
                )
                _state.update {
                    it.copy(
                        title = item.title,
                        isHome = false,
                        items = children,
                        query = "",
                        selectedItem = null,
                        canNavigateBack = true,
                    )
                }
            } catch (error: Throwable) {
                showError(error)
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun navigateBack(): Boolean {
        if (_state.value.isAccountVisible) {
            showAccount(false)
            return true
        }
        if (_state.value.isLibraryOrderVisible) {
            showLibraryOrder(false)
            return true
        }
        if (_state.value.playback != null) {
            closePlayer()
            return true
        }
        if (_state.value.selectedItem != null) {
            selectItem(null)
            return true
        }
        val previous = history.removeLastOrNull()
        if (previous == null) {
            if (!_state.value.isHome) {
                history.clear()
                _state.update {
                    it.copy(
                        isHome = true,
                        selectedSection = null,
                        title = "홈",
                        items = emptyList(),
                        query = "",
                        selectedItem = null,
                        canNavigateBack = false,
                    )
                }
                selectHome()
                return true
            }
            return false
        }
        _state.update {
            it.copy(
                title = previous.title,
                items = previous.items,
                isHome = previous.isHome,
                selectedSection = previous.selectedSection,
                query = "",
                canNavigateBack = history.isNotEmpty(),
            )
        }
        return true
    }

    fun play(item: PlexItem) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val queue = playbackQueueFor(item, _state.value)
                playbackQueue = queue
                playbackQueueIndex = queue.indexOfFirst {
                    it.ratingKey == item.ratingKey
                }.takeIf { it >= 0 } ?: 0
                val target = playbackQueue.getOrNull(playbackQueueIndex) ?: item
                openPlayback(target)
            } catch (error: Throwable) {
                clearPlaybackQueue()
                showError(error)
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun reportProgress(source: PlaybackSource, positionMs: Long, state: String) {
        viewModelScope.launch {
            runCatching { repository.timeline(source, state, positionMs) }
        }
    }

    fun closePlayer() {
        clearPlaybackQueue()
        _state.update {
            it.copy(
                playback = null,
                hasPreviousPlayback = false,
                previousPlaybackTitle = null,
                hasNextPlayback = false,
                nextPlaybackTitle = null,
            )
        }
    }

    fun completePlayback(positionMs: Long) {
        val source = _state.value.playback ?: return
        val currentState = _state.value
        val returnToHome = currentState.isHome
        val selectedSection = currentState.selectedSection
        val sections = currentState.sections
        val nextItem = if (currentState.autoPlayNext) {
            nextPlaybackItem()
        } else {
            null
        }
        if (nextItem != null) {
            playbackQueueIndex += 1
        } else {
            clearPlaybackQueue()
        }
        _state.update {
            if (nextItem != null) {
                it.copy(
                    isLoading = true,
                    selectedItem = null,
                    notice = "다음화를 자동 재생합니다.",
                )
            } else {
                it.copy(
                    playback = null,
                    selectedItem = null,
                    hasPreviousPlayback = false,
                    previousPlaybackTitle = null,
                    hasNextPlayback = false,
                    nextPlaybackTitle = null,
                    notice = "영상 재생이 끝나 보았음으로 표시했습니다.",
                )
            }
        }
        viewModelScope.launch {
            try {
                runCatching {
                    repository.timeline(source, "stopped", positionMs)
                }
                repository.setWatched(source.ratingKey, watched = true)
                if (nextItem != null) {
                    openPlayback(
                        item = nextItem,
                        resetResume = true,
                        notice = "다음화를 자동 재생합니다.",
                    )
                } else if (returnToHome) {
                    loadHomeInternal(sections)
                } else if (selectedSection != null) {
                    val watchedItems = runCatching {
                        repository.watched(selectedSection)
                    }.getOrNull()
                    val continueItems = runCatching {
                        repository.sectionOnDeck(selectedSection.key)
                    }.getOrNull()
                    _state.update { current ->
                        current.copy(
                            libraryWatchedRows = watchedItems?.let {
                                replaceHomeRow(
                                    current.libraryWatchedRows,
                                    selectedSection,
                                    it,
                                )
                            } ?: current.libraryWatchedRows,
                            libraryContinueRows = continueItems?.let {
                                replaceHomeRow(
                                    current.libraryContinueRows,
                                    selectedSection,
                                    it,
                                )
                            } ?: current.libraryContinueRows,
                        )
                    }
                }
            } catch (error: Throwable) {
                showError(error)
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun playNext(positionMs: Long) {
        val source = _state.value.playback ?: return
        val nextItem = nextPlaybackItem()
        if (nextItem == null) {
            _state.update { it.copy(notice = "다음화가 없습니다.") }
            return
        }
        val previousIndex = playbackQueueIndex
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    notice = "다음화로 이동합니다.",
                )
            }
            try {
                runCatching {
                    repository.timeline(source, "stopped", positionMs)
                }
                playbackQueueIndex = previousIndex + 1
                openPlayback(
                    item = nextItem,
                    resetResume = true,
                    notice = "다음화로 이동했습니다.",
                )
            } catch (error: Throwable) {
                playbackQueueIndex = previousIndex
                showError(error)
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun playPrevious(positionMs: Long) {
        val source = _state.value.playback ?: return
        val previousItem = previousPlaybackItem()
        if (previousItem == null) {
            _state.update { it.copy(notice = "이전화가 없습니다.") }
            return
        }
        val previousIndex = playbackQueueIndex
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    notice = "이전화로 이동합니다.",
                )
            }
            try {
                runCatching {
                    repository.timeline(source, "stopped", positionMs)
                }
                playbackQueueIndex = previousIndex - 1
                openPlayback(
                    item = previousItem,
                    resetResume = true,
                    notice = "이전화로 이동했습니다.",
                )
            } catch (error: Throwable) {
                playbackQueueIndex = previousIndex
                showError(error)
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setAutoPlayNext(enabled: Boolean) {
        repository.saveAutoPlayNext(enabled)
        _state.update {
            it.copy(
                autoPlayNext = enabled,
                notice = if (enabled) {
                    "다음화 자동재생을 켰습니다."
                } else {
                    "다음화 자동재생을 껐습니다."
                },
            )
        }
    }

    fun markWatched(item: PlexItem) {
        performItemAction(
            item = item,
            successMessage = "시청한 콘텐츠로 표시했습니다.",
            removeFromContinue = true,
            updatedOffset = item.durationMs,
            updatedViewCount = maxOf(item.viewCount, 1),
            keepSelectedItem = true,
        ) {
            repository.setWatched(item, watched = true)
        }
    }

    fun markUnwatched(item: PlexItem) {
        performItemAction(
            item = item,
            successMessage = "시청하지 않은 콘텐츠로 표시했습니다.",
            removeFromContinue = true,
            updatedOffset = 0,
            updatedViewCount = 0,
            keepSelectedItem = true,
        ) {
            repository.setWatched(item, watched = false)
        }
    }

    fun removeFromContinueWatching(item: PlexItem) {
        performItemAction(
            item = item,
            successMessage = "이어보기에서 제거했습니다.",
            removeFromContinue = true,
            updatedOffset = null,
            updatedViewCount = null,
            keepSelectedItem = false,
        ) {
            repository.removeFromContinueWatching(item)
        }
    }

    private fun performItemAction(
        item: PlexItem,
        successMessage: String,
        removeFromContinue: Boolean,
        updatedOffset: Long?,
        updatedViewCount: Int?,
        keepSelectedItem: Boolean,
        action: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                action()
                val refreshHome = _state.value.isHome
                fun update(candidate: PlexItem): PlexItem =
                    if (candidate.ratingKey == item.ratingKey) {
                        candidate.copy(
                            viewOffsetMs = updatedOffset ?: candidate.viewOffsetMs,
                            viewCount = updatedViewCount ?: candidate.viewCount,
                        )
                    } else {
                        candidate
                    }
                _state.update { current ->
                    current.copy(
                        items = current.items.map(::update),
                        homeSearchResults = current.homeSearchResults.map(::update),
                        homeRows = current.homeRows.map { row ->
                            row.copy(items = row.items.map(::update))
                        },
                        libraryWatchedRows = current.libraryWatchedRows.map { row ->
                            row.copy(items = row.items.map(::update))
                        },
                        libraryContinueRows = current.libraryContinueRows.map { row ->
                            row.copy(
                                items = if (removeFromContinue) {
                                    row.items.filterNot {
                                        it.ratingKey == item.ratingKey
                                    }
                                } else {
                                    row.items.map(::update)
                                },
                            )
                        }.filter { it.items.isNotEmpty() },
                        continueWatching = if (removeFromContinue) {
                            current.continueWatching.filterNot {
                                it.ratingKey == item.ratingKey
                            }
                        } else {
                            current.continueWatching.map(::update)
                        },
                        selectedItem = if (keepSelectedItem) {
                            current.selectedItem?.let(::update)
                        } else {
                            null
                        },
                        notice = successMessage,
                    )
                }
                val currentState = _state.value
                val section = currentState.selectedSection
                    ?: currentState.sections.firstOrNull {
                        it.key == item.librarySectionId
                    }
                val homeContinueItems = if (refreshHome) {
                    runCatching { repository.onDeck() }.getOrNull()
                } else {
                    null
                }
                val watchedItems = section?.let {
                    runCatching { repository.watched(it) }.getOrNull()
                }
                val continueItems = section?.let {
                    runCatching { repository.sectionOnDeck(it.key) }.getOrNull()
                }
                _state.update { current ->
                    current.copy(
                        continueWatching = homeContinueItems
                            ?: current.continueWatching,
                        libraryWatchedRows = if (section != null && watchedItems != null) {
                            replaceHomeRow(
                                current.libraryWatchedRows,
                                section,
                                watchedItems,
                            )
                        } else {
                            current.libraryWatchedRows
                        },
                        libraryContinueRows = if (section != null && continueItems != null) {
                            replaceHomeRow(
                                current.libraryContinueRows,
                                section,
                                continueItems,
                            )
                        } else {
                            current.libraryContinueRows
                        },
                    )
                }
            } catch (error: Throwable) {
                showError(error)
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setQuery(value: String) {
        if (!_state.value.isHome) {
            _state.update { it.copy(query = value) }
            return
        }

        homeSearchJob?.cancel()
        val term = value.trim()
        _state.update {
            it.copy(
                query = value,
                homeSearchResults = emptyList(),
                isHomeSearchLoading = term.isNotBlank(),
            )
        }
        if (term.isBlank()) return

        homeSearchJob = viewModelScope.launch {
            delay(350)
            try {
                val results = repository.search(term)
                _state.update { current ->
                    if (current.isHome && current.query.trim() == term) {
                        current.copy(
                            homeSearchResults = results,
                            isHomeSearchLoading = false,
                        )
                    } else {
                        current
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _state.update { current ->
                    if (current.isHome && current.query.trim() == term) {
                        current.copy(
                            homeSearchResults = emptyList(),
                            isHomeSearchLoading = false,
                            error = error.message ?: "검색에 실패했습니다.",
                        )
                    } else {
                        current
                    }
                }
            }
        }
    }
    fun showSettings(show: Boolean) = _state.update { it.copy(isSettingsVisible = show) }
    fun showAccount(show: Boolean) = _state.update { it.copy(isAccountVisible = show) }

    fun logout() {
        playingItem = null
        repository.logout()
        history.clear()
        _state.value = PlexUiState(
            connection = repository.connection(),
            playbackQuality = repository.playbackQuality(),
            autoPlayNext = repository.autoPlayNext(),
        )
    }

    fun setPlaybackQuality(
        quality: PlaybackQuality,
        positionMs: Long,
    ) {
        val previousQuality = _state.value.playbackQuality
        if (quality == previousQuality) return
        repository.savePlaybackQuality(quality)
        _state.update {
            it.copy(
                playbackQuality = quality,
                notice = "${quality.label}로 변경하고 있습니다.",
            )
        }
        val item = playingItem ?: return
        if (_state.value.playback == null) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val source = repository.playback(item).copy(
                    resumePositionMs = positionMs,
                )
                _state.update {
                    it.copy(
                        playback = source,
                        notice = "${quality.label}를 현재 재생에 적용했습니다.",
                    )
                }
            } catch (error: Throwable) {
                repository.savePlaybackQuality(previousQuality)
                _state.update {
                    it.copy(playbackQuality = previousQuality)
                }
                showError(error)
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun showLibraryOrder(show: Boolean) =
        _state.update { it.copy(isLibraryOrderVisible = show) }

    fun moveLibrary(fromIndex: Int, toIndex: Int) {
        val sections = _state.value.sections.toMutableList()
        if (fromIndex !in sections.indices || toIndex !in sections.indices) return
        val moved = sections.removeAt(fromIndex)
        sections.add(toIndex, moved)
        repository.saveLibraryOrder(sections.map { it.key })
        val position = sections.mapIndexed { index, section -> section.key to index }.toMap()
        _state.update {
            it.copy(
                sections = sections,
                libraryWatchedRows = it.libraryWatchedRows.sortedBy { row ->
                    position[row.section.key] ?: Int.MAX_VALUE
                },
                libraryContinueRows = it.libraryContinueRows.sortedBy { row ->
                    position[row.section.key] ?: Int.MAX_VALUE
                },
                homeRows = it.homeRows.sortedBy { row ->
                    position[row.section.key] ?: Int.MAX_VALUE
                },
            )
        }
    }

    private suspend fun openPlayback(
        item: PlexItem,
        resetResume: Boolean = false,
        notice: String? = null,
    ) {
        val source = repository.playback(item).let {
            if (resetResume) it.copy(resumePositionMs = 0) else it
        }
        playingItem = item
        val previousItem = previousPlaybackItem()
        val nextItem = nextPlaybackItem()
        _state.update { current ->
            current.copy(
                playback = source,
                selectedItem = null,
                hasPreviousPlayback = previousItem != null,
                previousPlaybackTitle = previousItem?.title,
                hasNextPlayback = nextItem != null,
                nextPlaybackTitle = nextItem?.title,
                notice = notice ?: current.notice,
            )
        }
    }

    private fun clearPlaybackQueue() {
        playingItem = null
        playbackQueue = emptyList()
        playbackQueueIndex = -1
    }

    private fun nextPlaybackItem(): PlexItem? =
        playbackQueue.getOrNull(playbackQueueIndex + 1)
            ?.takeIf { it.isPlayable }

    private fun previousPlaybackItem(): PlexItem? =
        playbackQueue.getOrNull(playbackQueueIndex - 1)
            ?.takeIf { it.isPlayable }

    private suspend fun playbackQueueFor(
        item: PlexItem,
        state: PlexUiState,
    ): List<PlexItem> {
        if (
            item.type == "episode" ||
            item.parentRatingKey != null ||
            item.parentKey != null
        ) {
            val seasonQueue = runCatching {
                repository.seasonSiblings(item)
            }.getOrDefault(emptyList())
                .filter { it.isPlayable }
                .distinctBy { it.ratingKey }
            if (seasonQueue.any { it.ratingKey == item.ratingKey }) {
                return seasonQueue
            }
        }
        val candidates = buildList {
            add(state.filteredItems)
            add(state.items)
            add(state.continueWatching)
            state.libraryContinueRows.forEach { add(it.items) }
            state.libraryWatchedRows.forEach { add(it.items) }
            state.homeRows.forEach { add(it.items) }
        }
        val queue = candidates.firstOrNull { items ->
            items.any { it.ratingKey == item.ratingKey }
        }.orEmpty()
            .filter { it.isPlayable }
            .distinctBy { it.ratingKey }
        return queue.ifEmpty { listOf(item) }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearNotice() = _state.update { it.copy(notice = null) }
    fun imageUrl(path: String?): String? = repository.imageUrl(path)
    fun token(): String = repository.token()

    private fun showError(error: Throwable) {
        if (error is CancellationException) return
        _state.update {
            it.copy(
                error = error.message ?: "알 수 없는 오류가 발생했습니다.",
                isLoading = false,
            )
        }
    }

    private fun replaceHomeRow(
        rows: List<HomeLibraryRow>,
        section: PlexSection,
        items: List<PlexItem>,
    ): List<HomeLibraryRow> {
        val withoutSection = rows.filterNot { it.section.key == section.key }
        return if (items.isEmpty()) {
            withoutSection
        } else {
            withoutSection + HomeLibraryRow(section, items)
        }
    }

    private data class HistoryEntry(
        val title: String,
        val items: List<PlexItem>,
        val isHome: Boolean,
        val selectedSection: PlexSection?,
    )
}

private fun List<PlexItem>.sortedByKoreanTitle(): List<PlexItem> {
    val collator = Collator.getInstance(Locale.KOREAN).apply {
        strength = Collator.PRIMARY
    }
    return sortedWith { left, right ->
        collator.compare(left.title.trim(), right.title.trim())
            .takeIf { it != 0 }
            ?: collator.compare(left.subtitle.orEmpty(), right.subtitle.orEmpty())
                .takeIf { it != 0 }
            ?: left.ratingKey.compareTo(right.ratingKey)
    }
}

class PlexViewModelFactory(
    private val repository: PlexRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PlexViewModel(repository) as T
}
