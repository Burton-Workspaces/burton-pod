package com.burton.pod.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.podStore: DataStore<Preferences> by preferencesDataStore(name = "burton_pod")

@Singleton
class LocalPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val catalog = stringPreferencesKey("catalog")
    }

    suspend fun loadCatalog(): CatalogCache {
        val json = context.podStore.data.map { it[Keys.catalog].orEmpty() }.first()
        return CatalogCodec.decode(json)
    }

    suspend fun saveCatalog(cache: CatalogCache) {
        context.podStore.edit { prefs ->
            prefs[Keys.catalog] = CatalogCodec.encode(
                podcasts = cache.podcasts,
                episodes = cache.episodes,
                downloads = cache.downloads,
                lastEpisodeId = cache.lastEpisodeId,
                lastPositionMs = cache.lastPositionMs,
            )
        }
    }
}
