package com.poo.textreader.ui.components

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.poo.textreader.data.FileItem
import com.poo.textreader.ui.SortType
import java.text.SimpleDateFormat
import java.util.*

/**
 * 파일 목록을 표시하는 화면
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    files: List<FileItem>,
    selectedFolderUri: Uri? = null,
    showOnlyTxt: Boolean = false,
    searchQuery: String = "",
    sortType: SortType = SortType.NAME_ASC,
    onFileClick: (FileItem) -> Unit,
    onFolderClick: (FileItem) -> Unit,
    onSelectFolder: () -> Unit,
    onToggleTxtFilter: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortTypeChange: (SortType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 상단 앱바
        TopAppBar(
            title = { Text("텍스트 리더") },
            actions = {
                // 정렬 버튼
                if (selectedFolderUri != null) {
                    IconButton(onClick = { 
                        val nextSortType = when (sortType) {
                            SortType.NAME_ASC -> SortType.NAME_DESC
                            SortType.NAME_DESC -> SortType.DATE_ASC
                            SortType.DATE_ASC -> SortType.DATE_DESC
                            SortType.DATE_DESC -> SortType.SIZE_ASC
                            SortType.SIZE_ASC -> SortType.SIZE_DESC
                            SortType.SIZE_DESC -> SortType.NAME_ASC
                        }
                        onSortTypeChange(nextSortType)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "정렬"
                        )
                    }
                }
                
                // TXT 필터 토글
                if (selectedFolderUri != null) {
                    IconButton(onClick = onToggleTxtFilter) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = if (showOnlyTxt) "모든 파일 보기" else "TXT 파일만 보기"
                        )
                    }
                }
                
                // 폴더 선택
                IconButton(onClick = onSelectFolder) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "폴더 선택"
                    )
                }
            }
        )
        
        // 검색창
        if (selectedFolderUri != null) {
            SearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onApplySearch = { onSearchQueryChange(searchQuery) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
        
        if (files.isEmpty()) {
            // 폴더가 선택되지 않았거나 파일이 없는 경우
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    if (selectedFolderUri != null) {
                        Text(
                            text = "선택한 폴더에 텍스트 파일이 없습니다",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "다른 폴더를 선택하거나 이 폴더에 텍스트 파일을 추가해주세요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "폴더를 선택해주세요",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "읽고 싶은 텍스트 파일이 있는 폴더를 선택하세요\n(예: Documents, Download 폴더 등)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Button(onClick = onSelectFolder) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (selectedFolderUri != null) "다른 폴더 선택" else "폴더 선택")
                    }
                }
            }
        } else {
            // 파일 목록
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files) { file ->
                    FileItemCard(
                        file = file,
                        onClick = { 
                            if (file.isDirectory) {
                                onFolderClick(file)
                            } else {
                                onFileClick(file)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FileItemCard(
    file: FileItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (file.isDirectory) Icons.Default.Add else Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (file.isDirectory) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!file.isDirectory) {
                        Text(
                            text = formatFileSize(file.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Text(
                        text = formatDate(file.lastModified),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (file.isDirectory) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

private fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(date)
}

/**
 * 검색바 컴포넌트
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onApplySearch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // debounce 처리를 위한 LaunchedEffect
    LaunchedEffect(query) {
        delay(300) // 300ms 대기
        onApplySearch(query)
    }
    
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("파일명 검색...") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "검색"
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "지우기"
                    )
                }
            }
        },
        singleLine = true,
        modifier = modifier
    )
}
