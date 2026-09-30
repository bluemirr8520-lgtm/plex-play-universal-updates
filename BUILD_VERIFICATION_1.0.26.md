# Plex Play Universal 1.0.26 검증 기록

- 검증일: 2026-09-30. 범위는 기존 자동/수동 시청 완료 컬렉션 기능 보완과 연결 휴대폰 업데이트입니다. 일괄 적용 버튼, 자동 과거 기록 재처리, Shyni 변경은 포함하지 않았습니다.
- JDK 17 / Android SDK 36 / Gradle 8.13 오프라인 `testDebugUnitTest assembleRelease` 성공.
- JVM 테스트 230개 통과. 실패·오류·건너뜀 0. 모든 파일 버전의 경로 수집, 누락·충돌 경로 제외, 대상 범위, 동일 태그 중복 쓰기 방지, 다른 라이브러리 결과 거부, HTTP 오류 처리 검사를 포함합니다.
- APK 패키지 `io.mirr.plexplay.universal`, versionName `1.0.26`, versionCode `27`, 디버깅 불가 및 16KB zipalign 검증 통과.
- 기존 서명 인증서 SHA-256: `74c178f3841343954e4a6c74223c4ef06df5cef270485c557d4f915f389845e2`.
- APK SHA-256: `97b5a5aa89b9e68873d9a62b750731af0cf39873fb049a0fc422c4961e60e737`, 121,231,606 bytes.
- 연결된 Samsung SM-S918N에 `adb install -r --no-incremental` 성공. 1.0.25(code 26)에서 1.0.26(code 27)으로 업데이트됐습니다.
- 설치 후 lastUpdateTime `2026-09-30 16:16:28`; firstInstallTime `2026-08-10 13:23:30` 유지. 앱 제거 또는 데이터 초기화를 실행하지 않았습니다.
- MainActivity 콜드 실행 `Status: ok`, 실행 프로세스 확인. 당시 전면 창은 NotificationShade여서 홈 화면의 실제 표시·터치 조작은 확인하지 않았습니다.
- 앱 업데이트 검증 과정에서 실제 서버 영상의 시청 상태나 컬렉션을 테스트 목적으로 변경하지 않았습니다. 실제 영상 완료/수동 완료의 휴대폰 서버 연동은 미검증입니다.
- 이 기록은 로컬 빌드와 휴대폰 설치 검증을 설명합니다. GitHub 배포 시 동일 APK의 서명·해시를 확인하고, 업로드한 모든 첨부 파일을 다시 다운로드하여 원본과 비교한 뒤 릴리스를 게시합니다. 저장소는 비공개로 유지합니다.
