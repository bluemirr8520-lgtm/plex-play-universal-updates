# Plex Play Universal 1.0.27 검증 기록

- 검증일: 2026-09-30. 시청 완료 컬렉션의 4개 라이브러리 이름 제한을 제거하여 Plex에 등록된 모든 라이브러리의 개별 영상으로 확대했습니다. 음악·사진 및 시리즈·시즌 묶음은 제외합니다. 과거 기록 자동 일괄 처리, Ubuntu 일괄 스크립트와 Shyni 변경은 포함하지 않았습니다.
- JDK 17 / Android SDK 36 / Gradle 8.13 오프라인 `testDebugUnitTest assembleRelease` 성공.
- JVM 테스트 232개 통과. 실패·오류·건너뜀 0. 신규·이름 변경 라이브러리와 영화·에피소드·클립·비디오의 자동/수동 완료 규칙 일치, KILL/123 경로 예외, 파일 버전 충돌 제외 및 기존 중복 쓰기 방지·저장 검증 테스트를 포함합니다.
- APK 패키지 `io.mirr.plexplay.universal`, versionName `1.0.27`, versionCode `28`, 디버깅 불가 및 16KB zipalign 검증 통과.
- 기존 서명 인증서 SHA-256: `74c178f3841343954e4a6c74223c4ef06df5cef270485c557d4f915f389845e2`.
- APK SHA-256: `f9abd12ef39f4c55b9ec99a9bd8c55874be86214aeff013b5669d22af4ac04ea`, 121,231,666 bytes.
- 연결된 Samsung SM-S918N에 `adb install -r --no-incremental` 성공. 1.0.26(code 27)에서 1.0.27(code 28)으로 업데이트됐습니다.
- 설치 후 lastUpdateTime `2026-09-30 16:55:26`; firstInstallTime `2026-08-10 13:23:30` 유지. 앱 제거 또는 데이터 초기화를 실행하지 않았습니다.
- MainActivity 콜드 실행 `Status: ok`, 실행 프로세스 확인 및 해당 프로세스의 AndroidRuntime 오류 로그 없음. 전면 창은 NotificationShade여서 실제 화면 표시·터치 조작은 확인하지 않았습니다.
- 실제 서버 영상의 시청 상태나 컬렉션을 테스트 목적으로 변경하지 않았습니다. 실제 영상 완료/수동 완료의 휴대폰 서버 연동은 미검증입니다.
- 이 기록은 로컬 빌드와 휴대폰 설치 검증을 설명합니다. GitHub 배포 시 동일 APK의 서명·해시를 확인하고, 업로드한 모든 첨부 파일을 다시 다운로드하여 원본과 비교한 뒤 릴리스를 게시합니다. 저장소는 비공개로 유지합니다.
