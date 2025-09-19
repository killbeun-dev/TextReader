package com.poo.textreader.data

import android.net.Uri

/**
 * 텍스트 파일 내용을 나타내는 데이터 클래스
 */
data class TextFileContent(
    val uri: Uri,
    val content: String,
    val encoding: String,
    val totalLines: Int,
    val currentPage: Int = 1,
    val totalPages: Int = 1
)

