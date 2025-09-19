package com.poo.textreader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poo.textreader.data.TextFileContent
import com.poo.textreader.ui.ReaderState

/**
 * 스트리밍 텍스트 리더 화면
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    textContent: TextFileContent,
    readerState: ReaderState,
    onBackClick: (Int, Int) -> Unit, // 현재 스크롤 위치를 전달하도록 수정
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onToggleSettings: () -> Unit,
    onToggleDarkTheme: () -> Unit,
    isDarkTheme: Boolean,
    onEncodingChange: (String) -> Unit,
    onSaveProgress: (Int, Int) -> Unit,
    onBrightnessChange: (Float) -> Unit = {}, // 밝기 조절 콜백 추가
    modifier: Modifier = Modifier
) {
    // 스크롤 상태를 함수 시작 부분에서 정의
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = readerState.currentPosition,
        initialFirstVisibleItemScrollOffset = readerState.scrollOffset
    )
    
    // UI 표시 상태
    var showUI by remember { mutableStateOf(false) }
    var showProgress by remember { mutableStateOf(false) }
    
    // 슬라이더 값 상태
    var sliderValue by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    
    // 페이지 이동 상태
    var pageNavigation by remember { mutableStateOf(0) } // -1: 이전, 1: 다음
    
    // 진행도 계산
    val totalChunks = readerState.chunks.size
    val currentProgress = if (totalChunks > 0) {
        (listState.firstVisibleItemIndex.toFloat() / totalChunks.toFloat()).coerceIn(0f, 1f)
    } else 0f
    
    // 슬라이더 값 업데이트 (드래그 중이 아닐 때만)
    LaunchedEffect(currentProgress) {
        if (!isDragging) {
            sliderValue = currentProgress
        }
    }
    
    // 슬라이더 드래그가 끝났을 때 스크롤 위치 변경
    LaunchedEffect(sliderValue, isDragging) {
        if (!isDragging && totalChunks > 0) {
            val targetIndex = (sliderValue * totalChunks).toInt().coerceIn(0, totalChunks - 1)
            listState.scrollToItem(targetIndex)
        }
    }
    
    // 페이지 이동 처리
    LaunchedEffect(pageNavigation) {
        if (pageNavigation != 0 && totalChunks > 0) {
            val currentIndex = listState.firstVisibleItemIndex
            val targetIndex = when (pageNavigation) {
                -1 -> (currentIndex - 1).coerceAtLeast(0)
                1 -> (currentIndex + 1).coerceAtMost(totalChunks - 1)
                else -> currentIndex
            }
            if (targetIndex != currentIndex) {
                listState.scrollToItem(targetIndex)
            }
            pageNavigation = 0 // 리셋
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(readerState.brightness) // 밝기 적용
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 상단 앱바 (애니메이션)
            AnimatedVisibility(
                visible = showUI,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                TopAppBar(
                    title = { 
                        Text(
                            text = "텍스트 리더",
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            // 현재 스크롤 위치를 전달
                            onBackClick(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                        }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "뒤로 가기"
                            )
                        }
                    },
                    actions = {
                        // 설정 버튼
                        IconButton(onClick = onToggleSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "설정"
                            )
                        }
                        
                        // 인코딩 정보 표시
                        Text(
                            text = readerState.encodingName,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .align(Alignment.CenterVertically)
                        )
                    }
                )
            }
        
            // 로딩 표시
            if (readerState.loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // 설정창 (조건부 표시)
            if (readerState.showSettings) {
                ReaderSettingsPanel(
                    readerState = readerState,
                    onFontSizeChange = onFontSizeChange,
                    onLineHeightChange = onLineHeightChange,
                    onClose = onToggleSettings,
                    onToggleDarkTheme = onToggleDarkTheme,
                    isDarkTheme = isDarkTheme,
                    onEncodingChange = onEncodingChange
                )
            } else {
                // 스크롤 위치 변경 감지 및 저장 (debounce 처리)
                LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
                    // 500ms debounce 후 저장
                    kotlinx.coroutines.delay(500)
                    onSaveProgress(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                }
                
                if (readerState.chunks.isEmpty()) {
                    // 빈 파일 처리
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "파일이 비어있습니다",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(readerState.chunks.size) { index ->
                                val chunk = readerState.chunks[index]
                                Text(
                                    text = chunk,
                                    fontSize = readerState.fontSizeSp.sp,
                                    lineHeight = (readerState.fontSizeSp * readerState.lineHeightMult).sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                        
                        // 좌측 스와이프 영역 (투명)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(60.dp) // 좌측 60dp 영역
                                .background(Color.Transparent)
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragEnd = { /* 드래그 종료 시 처리 */ }
                                    ) { change, _ ->
                                        val deltaY = change.previousPosition.y - change.position.y
                                        val brightnessDelta = (deltaY / 600f).coerceIn(-0.2f, 0.2f)
                                        onBrightnessChange(brightnessDelta)
                                    }
                                }
                        )
                    }
                }
            }
        }
        
        // 중앙 탭 영역 (3cm = 약 120dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.Center)
                .background(Color.Transparent)
                .clickable { 
                    showUI = !showUI
                    showProgress = !showProgress
                }
        )
        
        // 오른쪽 페이지 이동 영역 (빨간 선 영역 - 약 30dp)
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(30.dp)
                .align(Alignment.CenterEnd)
        ) {
            // 상단 - 이전 페이지 (페이지 업)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Transparent) // 실제 앱에서는 투명
                    .clickable { 
                        pageNavigation = -1
                    }
            )
            
            // 하단 - 다음 페이지 (페이지 다운)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Transparent) // 실제 앱에서는 투명
                    .clickable { 
                        pageNavigation = 1
                    }
            )
        }
        
        // 하단 진행도 슬라이더 (애니메이션)
        AnimatedVisibility(
            visible = showProgress,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // 진행도 텍스트
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "진행도",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${(currentProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 진행도 슬라이더
                    Slider(
                        value = sliderValue,
                        onValueChange = { value ->
                            isDragging = true
                            sliderValue = value
                        },
                        onValueChangeFinished = {
                            isDragging = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    
                    // 현재 위치 정보
                    Text(
                        text = "${listState.firstVisibleItemIndex + 1} / $totalChunks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * 리더 설정 패널 (폰트 크기, 줄 간격 조절)
 */
@Composable
private fun ReaderSettingsPanel(
    readerState: ReaderState,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onClose: () -> Unit,
    onToggleDarkTheme: () -> Unit,
    isDarkTheme: Boolean,
    onEncodingChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 제목
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "읽기 설정",
                    style = MaterialTheme.typography.headlineSmall
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "설정 닫기"
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 폰트 크기 조절
            SliderWithLabel(
                label = "폰트 크기",
                value = readerState.fontSizeSp.toFloat(),
                onValueChange = { onFontSizeChange(it.toInt()) },
                valueRange = 12f..24f,
                valueText = "${readerState.fontSizeSp}sp"
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 줄 간격 조절
            SliderWithLabel(
                label = "줄 간격",
                value = readerState.lineHeightMult,
                onValueChange = onLineHeightChange,
                valueRange = 1.0f..2.0f,
                valueText = "${String.format("%.1f", readerState.lineHeightMult)}x"
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 다크 모드 토글
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "다크 모드",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = { onToggleDarkTheme() }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 인코딩 정보
            Text(
                text = "인코딩: ${readerState.encodingName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 라벨이 있는 슬라이더
 */
@Composable
private fun SliderWithLabel(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}