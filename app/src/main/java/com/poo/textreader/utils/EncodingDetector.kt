package com.poo.textreader.utils

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.nio.charset.Charset

/**
 * 텍스트 파일의 인코딩을 자동으로 감지하는 유틸리티
 */
class EncodingDetector {
    
    /**
     * 파일의 인코딩을 감지합니다
     */
    fun detectEncoding(uri: Uri, context: Context): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                detectEncodingFromStream(inputStream)
            } ?: "UTF-8"
        } catch (e: Exception) {
            "UTF-8" // 기본값
        }
    }
    
    /**
     * 스트림에서 인코딩을 감지합니다
     */
    private fun detectEncodingFromStream(inputStream: InputStream): String {
        val buffer = ByteArray(8192)
        val bytesRead = inputStream.read(buffer)
        
        if (bytesRead <= 0) {
            return "UTF-8"
        }
        
        // BOM 체크
        val bom = detectBOM(buffer, bytesRead)
        if (bom.isNotEmpty()) {
            return bom
        }
        
        // UTF-8 유효성 검사
        if (isValidUTF8(buffer, bytesRead)) {
            return "UTF-8"
        }
        
        // 한국어 텍스트의 경우 EUC-KR 또는 CP949 시도
        if (containsKorean(buffer, bytesRead)) {
            return tryKoreanEncodings(buffer, bytesRead)
        }
        
        // 기본값
        return "UTF-8"
    }
    
    /**
     * BOM(Byte Order Mark) 감지
     */
    private fun detectBOM(buffer: ByteArray, length: Int): String {
        if (length >= 3) {
            // UTF-8 BOM
            if (buffer[0] == 0xEF.toByte() && buffer[1] == 0xBB.toByte() && buffer[2] == 0xBF.toByte()) {
                return "UTF-8"
            }
        }
        
        if (length >= 2) {
            // UTF-16 LE BOM
            if (buffer[0] == 0xFF.toByte() && buffer[1] == 0xFE.toByte()) {
                return "UTF-16LE"
            }
            // UTF-16 BE BOM
            if (buffer[0] == 0xFE.toByte() && buffer[1] == 0xFF.toByte()) {
                return "UTF-16BE"
            }
        }
        
        return ""
    }
    
    /**
     * UTF-8 유효성 검사
     */
    private fun isValidUTF8(buffer: ByteArray, length: Int): Boolean {
        var i = 0
        while (i < length) {
            val byte1 = buffer[i].toInt() and 0xFF
            
            when {
                byte1 < 0x80 -> {
                    // ASCII 문자
                    i++
                }
                byte1 in 0xC2..0xDF -> {
                    // 2바이트 문자
                    if (i + 1 >= length) return false
                    val byte2 = buffer[i + 1].toInt() and 0xFF
                    if (byte2 !in 0x80..0xBF) return false
                    i += 2
                }
                byte1 in 0xE0..0xEF -> {
                    // 3바이트 문자
                    if (i + 2 >= length) return false
                    val byte2 = buffer[i + 1].toInt() and 0xFF
                    val byte3 = buffer[i + 2].toInt() and 0xFF
                    if (byte2 !in 0x80..0xBF || byte3 !in 0x80..0xBF) return false
                    i += 3
                }
                byte1 in 0xF0..0xF4 -> {
                    // 4바이트 문자
                    if (i + 3 >= length) return false
                    val byte2 = buffer[i + 1].toInt() and 0xFF
                    val byte3 = buffer[i + 2].toInt() and 0xFF
                    val byte4 = buffer[i + 3].toInt() and 0xFF
                    if (byte2 !in 0x80..0xBF || byte3 !in 0x80..0xBF || byte4 !in 0x80..0xBF) return false
                    i += 4
                }
                else -> return false
            }
        }
        return true
    }
    
    /**
     * 한국어 문자가 포함되어 있는지 확인
     */
    private fun containsKorean(buffer: ByteArray, length: Int): Boolean {
        // 간단한 휴리스틱: 0x80 이상의 바이트가 많이 있으면 한국어일 가능성
        var highBytes = 0
        for (i in 0 until length) {
            if ((buffer[i].toInt() and 0xFF) >= 0x80) {
                highBytes++
            }
        }
        return highBytes > length / 4
    }
    
    /**
     * 한국어 인코딩 시도
     */
    private fun tryKoreanEncodings(buffer: ByteArray, length: Int): String {
        val encodings = listOf("EUC-KR", "CP949", "MS949")
        
        for (encoding in encodings) {
            try {
                val charset = Charset.forName(encoding)
                val decoded = String(buffer, 0, length, charset)
                
                // 한국어 문자가 제대로 디코딩되었는지 확인
                if (decoded.any { it in '\uAC00'..'\uD7AF' }) {
                    return encoding
                }
            } catch (e: Exception) {
                // 인코딩을 지원하지 않는 경우 무시
            }
        }
        
        return "UTF-8"
    }
}

