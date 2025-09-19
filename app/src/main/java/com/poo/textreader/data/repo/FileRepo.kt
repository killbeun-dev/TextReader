package com.poo.textreader.data.repo

import android.content.ContentResolver
import android.content.Context
import android.net.Uri

class FileRepo(private val context: Context) {
    
    fun openInput(uri: Uri) = context.contentResolver.openInputStream(uri)
    
    fun resolver(): ContentResolver = context.contentResolver
    
    /**
     * 텍스트 파일인지 확인
     */
    fun isTextFile(fileName: String): Boolean {
        val textExtensions = listOf(
            ".txt",
            ".text", 
            ".log",
            ".md",
            ".json",
            ".xml",
            ".csv"
        )
        return textExtensions.any { fileName.lowercase().endsWith(it) }
    }
}
