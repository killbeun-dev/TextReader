package com.poo.textreader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poo.textreader.ui.TextReaderApp
import com.poo.textreader.ui.TextReaderViewModel
import com.poo.textreader.ui.theme.TextReaderTheme

class MainActivity : ComponentActivity() {
    
    private lateinit var viewModel: TextReaderViewModel
    
    // SAF 폴더 선택을 위한 ActivityResultLauncher
    private val folderSelectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // URI 권한을 지속적으로 유지
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    // 권한 설정 실패 시 오류 메시지 표시
                    viewModel.setError("폴더 접근 권한을 설정할 수 없습니다. 다시 시도해주세요.")
                    return@let
                } catch (e: Exception) {
                    // 기타 오류는 무시하고 계속 진행
                }
                
                // 선택된 폴더 URI를 ViewModel에 전달
                viewModel.setSelectedFolderUri(uri, this)
            }
        } else {
            // 사용자가 폴더 선택을 취소한 경우
            viewModel.clearError()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 뒤로가기 콜백 설정
        val backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::viewModel.isInitialized) {
                    val currentState = viewModel.uiState.value
                    
                    when {
                        currentState.selectedFile != null -> {
                            // 텍스트 뷰어에서 파일 목록으로 돌아가기
                            viewModel.navigateBack()
                        }
                        currentState.selectedFolderUri != null -> {
                            // 파일 목록에서 이전 폴더로 이동
                            viewModel.navigateToParentFolder(this@MainActivity)
                        }
                        else -> {
                            // 기본 뒤로가기 (앱 종료)
                            finish()
                        }
                    }
                } else {
                    finish()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
        
        setContent {
            viewModel = viewModel<TextReaderViewModel>()
            
            // SettingsStore 초기화 및 저장된 설정 복원
            LaunchedEffect(Unit) {
                // 초기 로딩 상태 설정
                viewModel.setLoading(true)
                viewModel.initializeSettingsStore(this@MainActivity)
            }
            
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            
            TextReaderTheme(
                darkTheme = uiState.isDarkTheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TextReaderApp(
                        viewModel = viewModel,
                        onRequestFolderSelection = { requestFolderSelection() }
                    )
                }
            }
        }
    }
    
    /**
     * SAF를 사용하여 폴더 선택 요청
     */
    private fun requestFolderSelection() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            // 사용자가 폴더를 선택할 수 있도록 설정
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            
            // 특정 폴더로 시작하도록 설정 (선택사항)
            // putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments"))
        }
        
        folderSelectionLauncher.launch(intent)
    }
}