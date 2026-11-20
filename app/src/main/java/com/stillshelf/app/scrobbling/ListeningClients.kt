package com.stillshelf.app.scrobbling

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListeningClients @Inject constructor(
    private val koito: KoitoClient, private val maloja: MalojaClient,
    private val lastFm: LastFmClient, private val listenBrainz: ListenBrainzClient
) {
    fun forProvider(provider: ListeningProvider): ListeningProviderClient = when (provider) {
        ListeningProvider.KOITO -> koito
        ListeningProvider.MALOJA -> maloja
        ListeningProvider.LAST_FM -> lastFm
        ListeningProvider.LISTEN_BRAINZ -> listenBrainz
    }
}
