package com.vineyard.aivideostudio.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.vineyard.aivideostudio.core.common.AppConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GeminiPreferences(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)

    private val _apiKeyFlow = MutableStateFlow(getApiKey())
    val apiKeyFlow: StateFlow<String> = _apiKeyFlow.asStateFlow()

    fun getApiKey(): String {
        val userSavedKey = prefs.getString("custom_api_key", null)
        if (!userSavedKey.isNullOrBlank()) return userSavedKey
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }
    }

    fun setApiKey(key: String) {
        prefs.edit().putString("custom_api_key", key.trim()).apply()
        _apiKeyFlow.value = key.trim()
    }

    fun clearApiKey() {
        prefs.edit().remove("custom_api_key").apply()
        _apiKeyFlow.value = try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }
    }

    fun getLastModelSync(): Long = prefs.getLong("last_model_sync", 0L)
    fun setLastModelSync(timestamp: Long) = prefs.edit().putLong("last_model_sync", timestamp).apply()
}

class ProcessingPreferences(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("processing_prefs", Context.MODE_PRIVATE)

    var maxQaRetries: Int
        get() = prefs.getInt("max_qa_retries", AppConstants.DEFAULT_MAX_QA_RETRIES)
        set(value) = prefs.edit().putInt("max_qa_retries", value).apply()

    var keepIntermediateVideos: Boolean
        get() = prefs.getBoolean("keep_intermediate_videos", AppConstants.DEFAULT_KEEP_INTERMEDIATE_VIDEOS)
        set(value) = prefs.edit().putBoolean("keep_intermediate_videos", value).apply()

    var autoFinalQa: Boolean
        get() = prefs.getBoolean("auto_final_qa", AppConstants.DEFAULT_AUTO_FINAL_QA)
        set(value) = prefs.edit().putBoolean("auto_final_qa", value).apply()

    var customOutputDirectoryUri: String?
        get() = prefs.getString("custom_output_dir", null)
        set(value) = prefs.edit().putString("custom_output_dir", value).apply()
}

class AppPreferences(
    val gemini: GeminiPreferences,
    val processing: ProcessingPreferences
)
