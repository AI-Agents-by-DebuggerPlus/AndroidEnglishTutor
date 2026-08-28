package com.englishtutor.data.supabase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class SupabaseSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: SupabaseLogSettings? = null

    fun getSettings(): SupabaseLogSettings {
        cached?.let { return it }
        val settings = runCatching {
            context.assets.open(SETTINGS_FILE).use { input ->
                json.decodeFromString<SupabaseLogSettings>(input.reader().readText())
            }
        }.getOrElse {
            runCatching {
                context.assets.open(SETTINGS_EXAMPLE).use { input ->
                    json.decodeFromString<SupabaseLogSettings>(input.reader().readText())
                }
            }.getOrDefault(SupabaseLogSettings())
        }
        cached = settings
        return settings
    }

    companion object {
        private const val SETTINGS_FILE = "default_settings.json"
        private const val SETTINGS_EXAMPLE = "default_settings.json.example"
    }
}
