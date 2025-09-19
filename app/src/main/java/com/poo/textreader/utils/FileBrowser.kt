package com.poo.textreader.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import com.poo.textreader.data.FileItem
import com.poo.textreader.data.repo.FileRepo
import java.io.IOException

/**
 * SAF를 사용한 파일 브라우징 유틸리티 (개선된 버전)
 */
class FileBrowser {
    
    /**
     * 지정된 디렉토리의 파일 목록을 가져옵니다
     */
    fun getFilesInDirectory(uri: Uri, context: Context, showOnlyTxt: Boolean = false): List<FileItem> {
        val files = mutableListOf<FileItem>()

        try {
            println("DEBUG: getFilesInDirectory called with URI: $uri")

            // URI 타입에 따라 적절한 처리
            val childrenUri = if (uri.toString().contains("/document/")) {
                // 하위 폴더 URI인 경우 (document가 포함된 경우)
                println("DEBUG: Processing subfolder URI")
                val documentId = DocumentsContract.getDocumentId(uri)
                println("DEBUG: Document ID: $documentId")

                // 원본 tree URI를 찾아서 사용
                val treeUri = findTreeUri(uri)
                println("DEBUG: Found tree URI: $treeUri")
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            } else {
                // 루트 폴더 URI인 경우
                val treeDocumentId = DocumentsContract.getTreeDocumentId(uri)
                println("DEBUG: Tree document ID: $treeDocumentId")
                DocumentsContract.buildChildDocumentsUriUsingTree(uri, treeDocumentId)
            }

            println("DEBUG: Children URI: $childrenUri")

            val cursor: Cursor? = context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null,
                null,
                null
            )

            cursor?.use { c ->
                val idColumn = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeTypeColumn = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeColumn = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val lastModifiedColumn = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (c.moveToNext()) {
                    val documentId = c.getString(idColumn)
                    val name = c.getString(nameColumn)
                    val mimeType = c.getString(mimeTypeColumn)
                    val size = c.getLong(sizeColumn)
                    val lastModified = c.getLong(lastModifiedColumn)

                    val documentUri = if (uri.toString().contains("/document/")) {
                        // 하위 폴더 URI인 경우
                        val treeUri = findTreeUri(uri)
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    } else {
                        // 루트 폴더 URI인 경우
                        DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
                    }

                    // 폴더 판단을 더 포괄적으로 처리
                    val isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR ||
                            mimeType == "vnd.android.document/directory" ||
                            mimeType == "resource/folder" ||
                            mimeType == null || // MIME 타입이 null인 경우도 폴더일 수 있음
                            mimeType.isEmpty() || // 빈 MIME 타입도 폴더일 수 있음
                            size == 0L || // 크기가 0인 경우 폴더일 가능성
                            name.endsWith("/") || // 이름이 /로 끝나는 경우
                            mimeType.startsWith("vnd.android.document") // Android document provider의 폴더

                    println("DEBUG: File: $name, MIME Type: $mimeType, Is Directory: $isDirectory")

                    // 필터링: TXT 파일과 폴더만 표시
                    val fileRepo = FileRepo(context)
                    if (isDirectory || !showOnlyTxt || fileRepo.isTextFile(name)) {
                        files.add(
                            FileItem(
                                name = name,
                                uri = documentUri,
                                isDirectory = isDirectory,
                                size = size,
                                lastModified = lastModified
                            )
                        )
                    }
                }
            }
        } catch (e: SecurityException) {
            throw IOException("폴더 접근 권한이 없습니다. 폴더를 다시 선택해주세요.")
        } catch (e: IllegalArgumentException) {
            throw IOException("잘못된 폴더 URI입니다. 폴더를 다시 선택해주세요.")
        } catch (e: Exception) {
            throw IOException("파일 목록을 읽는 중 오류가 발생했습니다: ${e.message}")
        }

        // 폴더를 먼저, 그 다음 파일을 이름순으로 정렬
        return files.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name })
    }


    /**
     * 텍스트 파일인지 확인
     */
    fun isTextFile(fileItem: FileItem): Boolean {
        val textExtensions = listOf(
            ".txt",
            ".text",
            ".log",
            ".md",
            ".json",
            ".xml",
            ".csv"
        )
        return !fileItem.isDirectory && 
               textExtensions.any { fileItem.name.lowercase().endsWith(it) }
    }
    
    /**
     * 하위 폴더 URI에서 원본 tree URI를 찾습니다
     */
    private fun findTreeUri(uri: Uri): Uri {
        val uriString = uri.toString()
        return if (uriString.contains("/document/")) {
            // document URI에서 tree URI로 변환
            val treePart = uriString.substringBefore("/document/") + "/tree/"
            Uri.parse(treePart)
        } else {
            uri
        }
    }
    
    /**
     * 폴더 이름을 가져옵니다
     */
    fun getFolderName(uri: Uri, context: Context): String {
        return try {
            println("DEBUG: getFolderName called with URI: $uri")
            
            // URI에서 직접 폴더 이름 추출 시도
            val documentId = DocumentsContract.getDocumentId(uri)
            println("DEBUG: Document ID: $documentId")
            
            // document ID에서 폴더 이름 추출
            val parts = documentId.split(":")
            if (parts.size > 1) {
                val folderName = parts.last()
                println("DEBUG: Extracted folder name: $folderName")
                folderName
            } else {
                // ContentResolver 쿼리 시도
                val cursor: Cursor? = context.contentResolver.query(
                    uri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null
                )
                
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val nameColumn = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val name = c.getString(nameColumn)
                        println("DEBUG: Found folder name from cursor: $name")
                        name
                    } else {
                        println("DEBUG: No data in cursor")
                        "폴더"
                    }
                } ?: run {
                    println("DEBUG: Cursor is null")
                    "폴더"
                }
            }
        } catch (e: Exception) {
            println("DEBUG: Exception in getFolderName: ${e.message}")
            "폴더"
        }
    }
    
    /**
     * 부모 폴더 URI를 가져옵니다
     */
    fun getParentUri(uri: Uri, context: Context): Uri? {
        return try {
            println("DEBUG: getParentUri called with URI: $uri")
            
            // 현재 URI가 루트 폴더인지 확인
            if (!uri.toString().contains("/document/")) {
                println("DEBUG: Current URI is root folder, no parent")
                return null
            }
            
            // 현재 폴더의 document ID 가져오기
            val currentDocumentId = DocumentsContract.getDocumentId(uri)
            println("DEBUG: Current document ID: $currentDocumentId")
            
            // document ID에서 부모 경로 추출
            val parts = currentDocumentId.split(":")
            if (parts.size <= 1) {
                println("DEBUG: Cannot extract parent from document ID")
                return null
            }
            
            val pathParts = parts[1].split("/")
            if (pathParts.size <= 1) {
                println("DEBUG: No parent path found")
                return null
            }
            
            // 부모 경로 구성
            val parentPath = pathParts.dropLast(1).joinToString("/")
            val parentDocumentId = "${parts[0]}:$parentPath"
            println("DEBUG: Parent document ID: $parentDocumentId")
            
            // tree URI 찾기
            val treeUri = findTreeUri(uri)
            println("DEBUG: Tree URI: $treeUri")
            
            // 부모 폴더 URI 생성
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocumentId)
            println("DEBUG: Parent URI: $parentUri")
            
            parentUri
        } catch (e: Exception) {
            println("DEBUG: Error getting parent URI: ${e.message}")
            null
        }
    }
}
