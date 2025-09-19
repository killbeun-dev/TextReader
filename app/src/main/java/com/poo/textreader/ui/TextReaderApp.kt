package com.poo.textreader.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poo.textreader.ui.components.*

/**
 * 텍스트 리더 앱의 메인 컴포넌트
 */
@Composable
fun TextReaderApp(
    viewModel: TextReaderViewModel,
    onRequestFolderSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when {
            uiState.isLoading -> {
                LoadingScreen(
                    message = when {
                        uiState.selectedFolderUri == null && uiState.files.isEmpty() -> "저장된 폴더를 확인하는 중..."
                        uiState.selectedFolderUri == null -> "폴더를 읽는 중..."
                        uiState.selectedFile == null -> "파일을 읽는 중..."
                        else -> "로딩 중..."
                    }
                )
            }
            
            uiState.error != null -> {
                ErrorScreen(
                    message = uiState.error ?: "알 수 없는 오류",
                    onDismiss = { viewModel.clearError() }
                )
            }
            
            uiState.selectedFile != null -> {
                // 스트리밍 텍스트 리더 화면
                ReaderScreen(
                    textContent = uiState.selectedFile!!,
                    readerState = uiState.readerState,
                    onBackClick = { position, offset -> 
                        viewModel.navigateBackWithProgress(position, offset)
                    },
                    onFontSizeChange = { fontSize -> viewModel.updateFontSize(fontSize) },
                    onLineHeightChange = { lineHeight -> viewModel.updateLineHeight(lineHeight) },
                    onToggleSettings = { viewModel.toggleSettings() },
                    onToggleDarkTheme = { viewModel.toggleDarkTheme() },
                    isDarkTheme = uiState.isDarkTheme,
                    onEncodingChange = { encoding -> viewModel.changeEncoding(encoding, context) },
                    onSaveProgress = { position, offset -> 
                        viewModel.updateCurrentScrollPosition(position, offset)
                        viewModel.saveReadProgress(position, offset)
                    },
                    onBrightnessChange = { delta -> viewModel.adjustBrightness(delta) }
                )
            }
            
            else -> {
                // 파일 목록 화면
                FileListScreen(
                    files = uiState.files,
                    selectedFolderUri = uiState.selectedFolderUri,
                    showOnlyTxt = uiState.showOnlyTxt,
                    searchQuery = uiState.searchQuery,
                    sortType = uiState.sortType,
                    breadcrumbPath = uiState.breadcrumbPath,
                    onFileClick = { file ->
                        viewModel.selectFile(file, context)
                    },
                    onFolderClick = { folder ->
                        viewModel.navigateToFolder(folder, context)
                    },
                    onSelectFolder = onRequestFolderSelection,
                    onToggleTxtFilter = {
                        viewModel.toggleTxtFilter(context)
                    },
                    onSearchQueryChange = { query ->
                        viewModel.updateSearchQuery(query)
                    },
                    onSortTypeChange = { sortType ->
                        viewModel.updateSortType(sortType)
                    },
                    onBreadcrumbClick = { breadcrumbItem ->
                        viewModel.navigateToBreadcrumb(breadcrumbItem, context)
                    },
                    onNavigateBack = { viewModel.navigateToParentFolder(context) }
                )
            }
        }
    }
}
