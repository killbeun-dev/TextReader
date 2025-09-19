package com.poo.textreader.data.preferences

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val ROOT_URI = stringPreferencesKey("root_uri")
        val LAST_FILE_URI = stringPreferencesKey("last_file_uri")
        val LAST_ENCODING = stringPreferencesKey("last_encoding")
        val IS_DARK_THEME = stringPreferencesKey("is_dark_theme")
        val LAST_READ_POSITION = stringPreferencesKey("last_read_position")
        val LAST_SCROLL_OFFSET = stringPreferencesKey("last_scroll_offset")
    }

    val rootFolderUri: Flow<String?> = context.dataStore.data.map { it[Keys.ROOT_URI] }
    val lastFileUri: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_FILE_URI] }
    val lastEncoding: Flow<String> = context.dataStore.data.map { it[Keys.LAST_ENCODING] ?: "Auto" }
    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { it[Keys.IS_DARK_THEME]?.toBoolean() ?: false }
    val lastReadPosition: Flow<Int> = context.dataStore.data.map { it[Keys.LAST_READ_POSITION]?.toInt() ?: 0 }
    val lastScrollOffset: Flow<Int> = context.dataStore.data.map { it[Keys.LAST_SCROLL_OFFSET]?.toInt() ?: 0 }

    suspend fun saveRootUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(Keys.ROOT_URI) else prefs[Keys.ROOT_URI] = uri
        }
    }

    suspend fun saveLastFileUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(Keys.LAST_FILE_URI) else prefs[Keys.LAST_FILE_URI] = uri
        }
    }

    suspend fun saveLastEncoding(enc: String) {
        context.dataStore.edit { it[Keys.LAST_ENCODING] = enc }
    }
    
    suspend fun saveDarkTheme(isDark: Boolean) {
        context.dataStore.edit { it[Keys.IS_DARK_THEME] = isDark.toString() }
    }
    
    suspend fun saveReadPosition(position: Int) {
        context.dataStore.edit { it[Keys.LAST_READ_POSITION] = position.toString() }
    }
    
    suspend fun saveScrollOffset(offset: Int) {
        context.dataStore.edit { it[Keys.LAST_SCROLL_OFFSET] = offset.toString() }
    }
    
    /**
     * 파일별 진행도 저장
     */
    suspend fun saveFileProgress(fileUri: Uri, position: Int, scrollOffset: Int, totalLines: Int = 0) {
        val encodedUri = URLEncoder.encode(fileUri.toString(), StandardCharsets.UTF_8.toString())
        val positionKey = stringPreferencesKey("file_progress_pos_$encodedUri")
        val offsetKey = stringPreferencesKey("file_progress_offset_$encodedUri")
        val totalLinesKey = stringPreferencesKey("file_total_lines_$encodedUri")
        
        context.dataStore.edit { prefs ->
            prefs[positionKey] = position.toString()
            prefs[offsetKey] = scrollOffset.toString()
            if (totalLines > 0) {
                prefs[totalLinesKey] = totalLines.toString()
            }
        }
    }
    
    /**
     * 파일별 진행도 불러오기
     */
    suspend fun getFileProgress(fileUri: Uri): Triple<Int, Int, Int> {
        val encodedUri = URLEncoder.encode(fileUri.toString(), StandardCharsets.UTF_8.toString())
        val positionKey = stringPreferencesKey("file_progress_pos_$encodedUri")
        val offsetKey = stringPreferencesKey("file_progress_offset_$encodedUri")
        val totalLinesKey = stringPreferencesKey("file_total_lines_$encodedUri")
        
        val prefs = context.dataStore.data.first()
        val position = try { prefs[positionKey]?.toInt() ?: 0 } catch (e: NumberFormatException) { 0 }
        val offset = try { prefs[offsetKey]?.toInt() ?: 0 } catch (e: NumberFormatException) { 0 }
        val totalLines = try { prefs[totalLinesKey]?.toInt() ?: 0 } catch (e: NumberFormatException) { 0 }
        
        return Triple(position, offset, totalLines)
    }
    
    /**
     * 모든 파일의 진행도 정보 불러오기
     */
    suspend fun getAllFileProgress(): Map<String, Pair<Int, Int>> {
        val prefs = context.dataStore.data.first()
        val progressMap = mutableMapOf<String, Pair<Int, Int>>()
        
        prefs.asMap().forEach { (key, value) ->
            if (key.name.startsWith("file_progress_pos_")) {
                val encodedUri = key.name.removePrefix("file_progress_pos_")
                val offsetKey = stringPreferencesKey("file_progress_offset_$encodedUri")
                val position = try { (value as String).toInt() } catch (e: Exception) { 0 }
                val offset = try { prefs[offsetKey]?.toInt() ?: 0 } catch (e: NumberFormatException) { 0 }
                
                try {
                    val decodedUri = java.net.URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.toString())
                    progressMap[decodedUri] = Pair(position, offset)
                } catch (e: Exception) {
                    // 잘못된 URI는 무시
                }
            }
        }
        
        return progressMap
    }
}

