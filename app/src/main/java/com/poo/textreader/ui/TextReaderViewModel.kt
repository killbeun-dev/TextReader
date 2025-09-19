package com.poo.textreader.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poo.textreader.data.FileItem
import com.poo.textreader.data.TextFileContent
import com.poo.textreader.data.preferences.SettingsStore
import com.poo.textreader.data.repo.FileRepo
import com.poo.textreader.utils.EncodingDetector
import com.poo.textreader.utils.FileBrowser
import com.poo.textreader.utils.TextStreamReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * 텍스트 리더 앱의 메인 ViewModel
 */
class TextReaderViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(TextReaderUiState())
    val uiState: StateFlow<TextReaderUiState> = _uiState.asStateFlow()
    
    private val fileBrowser = FileBrowser()
    private val encodingDetector = EncodingDetector()
    private val textStreamReader = TextStreamReader()
    private var settingsStore: SettingsStore? = null
    private var fileRepo: FileRepo? = null
    
    /**
     * SettingsStore 초기화
     */
    fun initializeSettingsStore(context: Context) {
        settingsStore = SettingsStore(context)
        fileRepo = FileRepo(context)
        loadSavedSettings(context)
    }
    
    /**
     * 저장된 설정 불러오기
     */
    private fun loadSavedSettings(context: Context) {
        viewModelScope.launch {
            settingsStore?.let { store ->
                try {
                    // 각 Flow에서 첫 번째 값만 가져오기
                    val savedRootUri = store.rootFolderUri.first()
                    val savedLastFileUri = store.lastFileUri.first()
                    val savedLastEncoding = store.lastEncoding.first()
                    
                    if (savedRootUri != null) {
                        val uri = Uri.parse(savedRootUri)
                        
                        // 권한 유효성 검사
                        if (isUriPermissionValid(uri, context)) {
                            try {
                                val files = fileBrowser.getFilesInDirectory(uri, context)
                                
                                _uiState.value = _uiState.value.copy(
                                    selectedFolderUri = uri,
                                    allFiles = files,
                                    lastFileUri = savedLastFileUri?.let { Uri.parse(it) },
                                    lastEncoding = savedLastEncoding,
                                    isLoading = false
                                )
                                applyFiltersAndSort()
                            } catch (e: Exception) {
                                // 폴더 접근 실패 시 저장된 URI 삭제
                                store.saveRootUri(null)
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    error = "저장된 폴더에 접근할 수 없습니다. 폴더를 다시 선택해주세요."
                                )
                            }
                        } else {
                            // 권한이 유효하지 않은 경우 저장된 URI 삭제 (조용히 처리)
                            store.saveRootUri(null)
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = null // 오류 메시지 표시하지 않음
                            )
                        }
                    } else {
                        // 저장된 폴더가 없는 경우
                        _uiState.value = _uiState.value.copy(
                            isLoading = false
                        )
                    }
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "설정을 불러오는 중 오류가 발생했습니다."
                    )
                }
            }
        }
    }
    
    /**
     * URI 권한 유효성 검사
     */
    private fun isUriPermissionValid(uri: Uri, context: Context): Boolean {
        return try {
            // URI에 대한 권한이 있는지 확인
            val persistedUriPermissions = context.contentResolver.persistedUriPermissions
            val hasPermission = persistedUriPermissions.any { permission ->
                permission.uri == uri && 
                (permission.isReadPermission || permission.isWritePermission)
            }
            
            if (!hasPermission) {
                return false
            }
            
            // 실제로 폴더에 접근할 수 있는지 테스트
            try {
                val testFiles = fileBrowser.getFilesInDirectory(uri, context)
                true // 접근 가능
            } catch (e: Exception) {
                false // 접근 불가능
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 폴더 선택 요청
     */
    fun requestFolderSelection() {
        _uiState.value = _uiState.value.copy(
            showFolderSelection = true
        )
    }
    
    /**
     * 선택된 폴더 URI 설정
     */
    fun setSelectedFolderUri(uri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                showFolderSelection = false
            )
            
            try {
                val files = fileBrowser.getFilesInDirectory(uri, context)
                
                _uiState.value = _uiState.value.copy(
                    selectedFolderUri = uri,
                    allFiles = files,
                    isLoading = false,
                    error = null
                )
                applyFiltersAndSort()
                
                // 선택된 폴더 URI 저장
                settingsStore?.saveRootUri(uri.toString())
            } catch (e: Exception) {
                // 더 자세한 오류 정보 제공
                val errorMessage = when {
                    e.message?.contains("Permission") == true -> "폴더 접근 권한이 없습니다. 다시 폴더를 선택해주세요."
                    e.message?.contains("SecurityException") == true -> "보안 정책으로 인해 폴더에 접근할 수 없습니다."
                    e.message?.contains("FileNotFoundException") == true -> "폴더를 찾을 수 없습니다."
                    else -> "폴더를 읽는 중 오류가 발생했습니다: ${e.message ?: "알 수 없는 오류"}"
                }
                
                _uiState.value = _uiState.value.copy(
                    selectedFolderUri = null, // 오류 시 URI 초기화
                    files = emptyList(),
                    isLoading = false,
                    error = errorMessage
                )
            }
        }
    }
    
    /**
     * 파일 선택
     */
    fun selectFile(fileItem: FileItem, context: Context) {
        if (!fileItem.isDirectory) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    selectedFile = null,
                    readerState = _uiState.value.readerState.copy(loading = true, chunks = emptyList())
                )
                
                try {
                    val content = textStreamReader.readTextFile(
                        fileItem.uri, 
                        context,
                        encodingDetector
                    )
                    
                    // 텍스트를 청크로 분할 (스트리밍 효과를 위해)
                    val chunks = splitTextIntoChunks(content.content, 1000) // 1000자씩 청크로 분할
                    
                    _uiState.value = _uiState.value.copy(
                        selectedFile = content,
                        readerState = _uiState.value.readerState.copy(
                            chunks = chunks,
                            encodingName = content.encoding,
                            loading = false
                        ),
                        isLoading = false,
                        error = null
                    )
                    
                    // 마지막 파일 URI와 인코딩 저장
                    settingsStore?.saveLastFileUri(fileItem.uri.toString())
                    settingsStore?.saveLastEncoding(content.encoding)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        selectedFile = null,
                        readerState = _uiState.value.readerState.copy(loading = false, chunks = emptyList()),
                        isLoading = false,
                        error = e.message ?: "파일을 읽는 중 오류가 발생했습니다."
                    )
                }
            }
        }
    }
    
    /**
     * 텍스트를 청크로 분할
     */
    private fun splitTextIntoChunks(text: String, chunkSize: Int): List<String> {
        if (text.isEmpty()) return emptyList()
        
        val chunks = mutableListOf<String>()
        var startIndex = 0
        
        while (startIndex < text.length) {
            val endIndex = minOf(startIndex + chunkSize, text.length)
            val chunk = text.substring(startIndex, endIndex)
            chunks.add(chunk)
            startIndex = endIndex
        }
        
        return chunks
    }
    
    /**
     * 폴더로 이동
     */
    fun navigateToFolder(fileItem: FileItem, context: Context) {
        if (fileItem.isDirectory) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    isLoading = true
                )
                
                try {
                    val files = fileBrowser.getFilesInDirectory(fileItem.uri, context)
                    _uiState.value = _uiState.value.copy(
                        allFiles = files,
                        isLoading = false,
                        error = null
                    )
                    applyFiltersAndSort()
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "폴더를 읽는 중 오류가 발생했습니다."
                    )
                }
            }
        }
    }
    
    /**
     * 텍스트 뷰어에서 뒤로 가기
     */
    fun navigateBack() {
        _uiState.value = _uiState.value.copy(
            selectedFile = null
        )
    }
    
    /**
     * 선택된 폴더 초기화 (폴더 선택 화면으로 돌아가기)
     */
    fun clearSelectedFolder() {
        _uiState.value = _uiState.value.copy(
            selectedFolderUri = null,
            files = emptyList(),
            selectedFile = null,
            showOnlyTxt = false,
            error = null
        )
        
        // 저장된 폴더 URI도 삭제
        viewModelScope.launch {
            settingsStore?.saveRootUri(null)
        }
    }
    
    /**
     * TXT 필터 토글
     */
    fun toggleTxtFilter(context: Context) {
        val currentState = _uiState.value
        val newShowOnlyTxt = !currentState.showOnlyTxt
        
        _uiState.value = _uiState.value.copy(
            showOnlyTxt = newShowOnlyTxt
        )
        applyFiltersAndSort()
    }
    
    /**
     * 로딩 상태 설정
     */
    fun setLoading(loading: Boolean) {
        _uiState.value = _uiState.value.copy(
            isLoading = loading
        )
    }
    
    /**
     * 에러 메시지 설정
     */
    fun setError(message: String) {
        _uiState.value = _uiState.value.copy(
            error = message
        )
    }
    
    /**
     * 폰트 크기 변경
     */
    fun updateFontSize(fontSize: Int) {
        _uiState.value = _uiState.value.copy(
            readerState = _uiState.value.readerState.copy(fontSizeSp = fontSize)
        )
    }
    
    /**
     * 줄 간격 변경
     */
    fun updateLineHeight(lineHeightMult: Float) {
        _uiState.value = _uiState.value.copy(
            readerState = _uiState.value.readerState.copy(lineHeightMult = lineHeightMult)
        )
    }
    
    /**
     * 설정창 토글
     */
    fun toggleSettings() {
        _uiState.value = _uiState.value.copy(
            readerState = _uiState.value.readerState.copy(
                showSettings = !_uiState.value.readerState.showSettings
            )
        )
    }
    
    /**
     * 검색 쿼리 업데이트
     */
    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query
        )
        // debounce 처리를 위해 즉시 적용하지 않음
    }
    
    /**
     * 검색 쿼리 적용 (debounce 후 호출)
     */
    fun applySearchQuery() {
        applyFiltersAndSort()
    }
    
    /**
     * 정렬 타입 변경
     */
    fun updateSortType(sortType: SortType) {
        _uiState.value = _uiState.value.copy(
            sortType = sortType
        )
        applyFiltersAndSort()
    }
    
    /**
     * 필터링 및 정렬 적용
     */
    private fun applyFiltersAndSort() {
        val currentState = _uiState.value
        val allFiles = currentState.allFiles
        
        // 검색 필터 적용
        val filteredFiles = if (currentState.searchQuery.isBlank()) {
            allFiles
        } else {
            allFiles.filter { file ->
                file.name.contains(currentState.searchQuery, ignoreCase = true)
            }
        }
        
        // TXT 필터 적용
        val txtFilteredFiles = if (currentState.showOnlyTxt) {
            filteredFiles.filter { file ->
                !file.isDirectory && file.name.lowercase().endsWith(".txt")
            }
        } else {
            filteredFiles
        }
        
        // 정렬 적용
        val sortedFiles = when (currentState.sortType) {
            SortType.NAME_ASC -> txtFilteredFiles.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
            SortType.NAME_DESC -> txtFilteredFiles.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.name.lowercase() })
            SortType.DATE_ASC -> txtFilteredFiles.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.lastModified })
            SortType.DATE_DESC -> txtFilteredFiles.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.lastModified })
            SortType.SIZE_ASC -> txtFilteredFiles.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.size })
            SortType.SIZE_DESC -> txtFilteredFiles.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.size })
        }
        
        _uiState.value = currentState.copy(
            files = sortedFiles
        )
    }
    
    /**
     * 에러 메시지 클리어
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(
            error = null
        )
    }
}

/**
 * UI 상태를 나타내는 데이터 클래스
 */
data class TextReaderUiState(
    val selectedFolderUri: Uri? = null,
    val allFiles: List<FileItem> = emptyList(), // 원본 파일 목록
    val files: List<FileItem> = emptyList(),    // 필터링/정렬된 파일 목록
    val selectedFile: TextFileContent? = null,
    val lastFileUri: Uri? = null,
    val lastEncoding: String = "Auto",
    val showOnlyTxt: Boolean = false,
    val isLoading: Boolean = false,
    val showFolderSelection: Boolean = false,
    val error: String? = null,
    val readerState: ReaderState = ReaderState(),
    val searchQuery: String = "",
    val sortType: SortType = SortType.NAME_ASC
)

/**
 * 정렬 타입
 */
enum class SortType {
    NAME_ASC,    // 이름 오름차순
    NAME_DESC,   // 이름 내림차순
    DATE_ASC,    // 날짜 오름차순 (오래된 것부터)
    DATE_DESC,   // 날짜 내림차순 (최신 것부터)
    SIZE_ASC,    // 크기 오름차순
    SIZE_DESC    // 크기 내림차순
}

/**
 * 리더 화면 상태
 */
data class ReaderState(
    val chunks: List<String> = emptyList(),
    val encodingName: String = "Auto",
    val fontSizeSp: Int = 16,
    val lineHeightMult: Float = 1.2f,
    val loading: Boolean = false,
    val showSettings: Boolean = false
)
