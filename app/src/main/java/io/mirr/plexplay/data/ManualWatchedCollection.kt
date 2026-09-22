package io.mirr.plexplay.data

import kotlinx.coroutines.CancellationException

/** Only manual watched actions call this; never unwatch, seek or continue-list removal. */
internal suspend fun saveWatchedWithCollection(
    saveWatched: suspend () -> Unit,
    updateCollection: suspend () -> String?,
): String? {
    saveWatched()
    return try {
        updateCollection()?.let { "시청 완료로 표시하고 컬렉션을 $it 하나로 변경했습니다." }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        "시청 완료는 저장됐지만 컬렉션 변경에 실패했습니다. ${error.message.orEmpty()}"
    }
}
