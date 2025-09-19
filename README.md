# SAF 폴더 범위 TXT 리더

Google Play 정책을 준수하는 SAF(Storage Access Framework) 기반 텍스트 파일 리더 앱입니다.

> 🤖 이 프로젝트는 **Cursor AI**와의 협업으로 개발되었습니다.

## 주요 기능

### 🔒 Google Play 정책 준수

- **Scoped Storage** 완전 지원
- SAF를 통한 사용자 선택 폴더만 접근
- **권한 최소화**: Storage 권한 없이 SAF만 사용
- 사용자 권한 요청 없음

### 📁 폴더 범위 파일 브라우징

- 사용자가 선택한 폴더 내에서만 파일 탐색
- 폴더와 텍스트 파일 구분 표시
- 파일 크기 및 수정 날짜 정보 제공
- **TXT 파일 필터링**: TXT 파일만 보기/모든 파일 보기 토글
- **정렬 옵션**: 폴더 우선, 파일명 알파벳 순 정렬

### 📖 대용량 텍스트 파일 지원

- **2~10MB** 대용량 파일 스트리밍 읽기
- **청크 단위 렌더링**: LazyColumn을 사용한 순차적 텍스트 표시
- 페이지 단위 로딩으로 메모리 효율성
- 실시간 인코딩 감지
- **폰트 크기 조절**: 12sp~28sp 범위에서 자유롭게 조절
- **줄 간격 조절**: 1.0~2.0 배율로 줄 간격 조절

### 🌐 인코딩 자동 감지

- **한국어 포함** 다국어 인코딩 지원
- UTF-8, EUC-KR, CP949, MS949 자동 감지
- BOM(Byte Order Mark) 인식

### 🎨 현대적인 UI/UX

- **Jetpack Compose** 기반 Material 3 디자인
- 직관적인 파일 탐색 인터페이스
- 반응형 텍스트 뷰어

### 💾 설정 저장 및 복원

- **DataStore** 기반 설정 저장
- **앱 시작 시 자동 복원**: 저장된 폴더가 있으면 자동으로 복원
- **권한 유효성 검사**: 저장된 URI의 권한이 유효한지 확인
- **실패 시 자동 정리**: 접근할 수 없는 URI는 자동으로 삭제
- 앱 재시작 시 마지막 폴더/파일 자동 복원
- 인코딩 설정 기억

## 지원 파일 형식

- `.txt` - 일반 텍스트 파일
- `.text` - 텍스트 파일
- `.log` - 로그 파일
- `.md` - 마크다운 파일
- `.json` - JSON 파일
- `.xml` - XML 파일
- `.csv` - CSV 파일

## 기술 스택

- **언어**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **아키텍처**: MVVM + StateFlow
- **파일 접근**: Storage Access Framework (SAF)
- **설정 저장**: DataStore Preferences
- **인코딩**: 자체 구현 인코딩 감지
- **최소 SDK**: Android 12 (API 31)
- **타겟 SDK**: Android 14 (API 36)

## 설치 및 사용법

### 1. 앱 설치

```bash
# 디버그 APK 빌드
./gradlew assembleDebug

# APK 위치: app/build/outputs/apk/debug/app-debug.apk
```

### 2. 사용 방법

1. 앱 실행
2. "폴더 선택" 버튼 클릭
3. 읽고 싶은 텍스트 파일이 있는 폴더 선택
   - **권장**: Documents, Download, Pictures 등의 구체적인 폴더 선택
   - **주의**: 외부 저장소 루트(primary) 선택 시 제한된 폴더만 표시됩니다
4. 파일 목록에서 원하는 텍스트 파일 선택
5. 텍스트 내용 확인

## 문제 해결

### "Unsupported Uri content://com.android.externalstorage.documents/tree/primary" 오류

이 오류는 Android 11(API 30) 이상에서 외부 저장소 루트에 접근할 때 발생합니다.

**해결 방법:**

1. 폴더 선택 시 구체적인 폴더(Documents, Download 등)를 선택하세요
2. 외부 저장소 루트를 선택한 경우, 앱에서 자동으로 접근 가능한 폴더들을 표시합니다
3. 표시된 폴더 중 하나를 선택하여 계속 진행하세요

## 프로젝트 구조

```
app/src/main/java/com/poo/textreader/
├── data/
│   ├── FileItem.kt              # 파일/폴더 데이터 클래스
│   └── TextFileContent.kt       # 텍스트 내용 데이터 클래스
├── ui/
│   ├── TextReaderViewModel.kt   # 메인 ViewModel
│   ├── TextReaderApp.kt         # 앱 메인 컴포넌트
│   └── components/
│       ├── FileListScreen.kt    # 파일 목록 화면
│       └── TextViewerScreen.kt  # 텍스트 뷰어 화면
├── utils/
│   ├── FileBrowser.kt           # SAF 파일 브라우징
│   ├── EncodingDetector.kt      # 인코딩 자동 감지
│   └── TextStreamReader.kt      # 대용량 파일 스트리밍
└── MainActivity.kt              # 메인 액티비티
```

## 핵심 구현 사항

### SAF 권한 관리

```kotlin
// 폴더 선택 요청
private fun requestFolderSelection() {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }
    folderSelectionLauncher.launch(intent)
}
```

### 인코딩 자동 감지

```kotlin
// BOM 감지 및 UTF-8 유효성 검사
private fun detectEncodingFromStream(inputStream: InputStream): String {
    // BOM 체크
    val bom = detectBOM(buffer, bytesRead)
    if (bom.isNotEmpty()) return bom

    // UTF-8 유효성 검사
    if (isValidUTF8(buffer, bytesRead)) return "UTF-8"

    // 한국어 텍스트 감지
    if (containsKorean(buffer, bytesRead)) {
        return tryKoreanEncodings(buffer, bytesRead)
    }

    return "UTF-8"
}
```

### 대용량 파일 스트리밍

```kotlin
// 페이지 단위 로딩
companion object {
    private const val MAX_LINES_PER_PAGE = 1000
    private const val BUFFER_SIZE = 8192
}
```

## 성능 최적화

- **메모리 효율성**: 페이지 단위 로딩으로 대용량 파일 처리
- **인코딩 감지**: 파일 헤더만 읽어서 빠른 감지
- **UI 반응성**: StateFlow를 통한 비동기 처리
- **파일 필터링**: 텍스트 파일만 표시하여 성능 향상

## 보안 및 개인정보

- **권한 없음**: Storage 권한을 요청하지 않음
- **SAF 전용**: 사용자가 직접 폴더 선택하여 접근
- **로컬 처리**: 모든 파일 처리가 기기 내에서 수행
- **데이터 전송 없음**: 외부 서버로 데이터 전송하지 않음
- **Google Play 정책 완전 준수**: Scoped Storage 및 권한 최소화

## 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다.

## 기여하기

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 문의사항

프로젝트에 대한 문의사항이나 버그 리포트는 GitHub Issues를 통해 제출해주세요.
