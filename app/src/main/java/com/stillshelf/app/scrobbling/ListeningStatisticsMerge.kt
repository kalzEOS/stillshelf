package com.stillshelf.app.scrobbling

/** Only a failed section may reuse its same-account snapshot. A successful empty response stays empty. */
internal fun ListeningStatistics.retainingUnavailable(previous: ListeningStatistics?): ListeningStatistics {
    if (previous == null || unavailableSections.isEmpty()) return this
    val charts = ListeningChart.entries.filter { ListeningMetric.valueOf(it.name) in unavailableSections }.toSet()
    val keepTime = listeningSeconds == null && (ListeningMetric.COUNT in unavailableSections || ListeningMetric.RECENT in unavailableSections)
    return copy(
        listeningSeconds = if (keepTime) previous.listeningSeconds else listeningSeconds,
        listeningTimeNote = if (keepTime && previous.listeningSeconds != null) "Cached recorded listening time." else listeningTimeNote,
        totalScrobbles = if (ListeningMetric.COUNT in unavailableSections) previous.totalScrobbles else totalScrobbles,
        recent = if (ListeningMetric.RECENT in unavailableSections) previous.recent else recent,
        topArtists = if (ListeningMetric.ARTISTS in unavailableSections) previous.topArtists else topArtists,
        topAlbums = if (ListeningMetric.ALBUMS in unavailableSections) previous.topAlbums else topAlbums,
        topTracks = if (ListeningMetric.TRACKS in unavailableSections) previous.topTracks else topTracks,
        moreCharts = (moreCharts - charts) + (previous.moreCharts intersect charts)
    )
}
