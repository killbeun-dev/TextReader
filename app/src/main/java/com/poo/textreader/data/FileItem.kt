package com.poo.textreader.data

import android.net.Uri

/**
 * 파일 또는 폴더를 나타내는 데이터 클래스
 */
data class FileItem(
    val name: String,
    val uri: Uri,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val readProgress: Float = 0f // 읽은 진행도 (0.0 ~ 1.0)
)
