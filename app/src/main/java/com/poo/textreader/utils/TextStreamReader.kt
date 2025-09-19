package com.poo.textreader.utils

import android.content.Context
import android.net.Uri
import com.poo.textreader.data.TextFileContent
import com.poo.textreader.data.repo.FileRepo
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 대용량 텍스트 파일을 스트리밍 방식으로 읽는 유틸리티
 */
class TextStreamReader {
    
    companion object {
        private const val MAX_LINES_PER_PAGE = 1000
        private const val BUFFER_SIZE = 8192
    }
    
    /**
     * 텍스트 파일을 읽어서 TextFileContent 객체로 반환
     */
    fun readTextFile(
        uri: Uri, 
        context: Context, 
        encodingDetector: EncodingDetector
    ): TextFileContent {
        val encoding = encodingDetector.detectEncoding(uri, context)
        
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(
                    InputStreamReader(inputStream, encoding),
                    BUFFER_SIZE
                )
                
                val content = StringBuilder()
                var lineCount = 0
                var line: String?
                
                // 첫 번째 페이지만 읽기 (성능을 위해)
                while (reader.readLine().also { line = it } != null && lineCount < MAX_LINES_PER_PAGE) {
                    content.append(line).append("\n")
                    lineCount++
                }
                
                // 전체 라인 수 계산 (추정)
                val totalLines = estimateTotalLines(uri, context, encoding)
                val totalPages = (totalLines + MAX_LINES_PER_PAGE - 1) / MAX_LINES_PER_PAGE
                
                TextFileContent(
                    uri = uri,
                    content = content.toString(),
                    encoding = encoding,
                    totalLines = totalLines,
                    currentPage = 1,
                    totalPages = totalPages
                )
            } ?: throw Exception("파일을 열 수 없습니다.")
        } catch (e: Exception) {
            throw Exception("파일을 읽는 중 오류가 발생했습니다: ${e.message}")
        }
    }
    
    /**
     * 특정 페이지의 내용을 읽기
     */
    fun readPage(
        uri: Uri,
        context: Context,
        encoding: String,
        pageNumber: Int
    ): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(
                    InputStreamReader(inputStream, encoding),
                    BUFFER_SIZE
                )
                
                val content = StringBuilder()
                val startLine = (pageNumber - 1) * MAX_LINES_PER_PAGE
                var currentLine = 0
                var line: String?
                
                // 시작 라인까지 건너뛰기
                while (currentLine < startLine && reader.readLine().also { line = it } != null) {
                    currentLine++
                }
                
                // 페이지 내용 읽기
                while (reader.readLine().also { line = it } != null && 
                       currentLine < startLine + MAX_LINES_PER_PAGE) {
                    content.append(line).append("\n")
                    currentLine++
                }
                
                content.toString()
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * 전체 라인 수 추정
     */
    private fun estimateTotalLines(uri: Uri, context: Context, encoding: String): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(
                    InputStreamReader(inputStream, encoding),
                    BUFFER_SIZE
                )
                
                var lineCount = 0
                while (reader.readLine() != null) {
                    lineCount++
                }
                lineCount
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
