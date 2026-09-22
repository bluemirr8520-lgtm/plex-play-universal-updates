# Plex Play Universal 1.0.23 검증 기록

- 2026-09-22: JDK 17 / SDK 36, 오프라인 release 빌드 성공.
- JVM 테스트 214개 통과: 실패·오류·건너뜀 0. 수동 시청 완료의 저장 순서, KILL/123 결과, 비대상 항목, 부분 실패, 취소 처리를 포함합니다.
- 실제 서버의 사용자 영상·시청 기록·컬렉션은 테스트 목적으로 변경하지 않았습니다. 실제 서버 편집 권한과 통신 오류는 앱에서 안내합니다.
- 앱 ID io.mirr.plexplay.universal / versionName 1.0.23 / versionCode 24.
- release 디버깅 불가, 16KB zipalign, 기존 서명 인증서 검증 통과.
- 인증서 SHA-256: 74c178f3841343954e4a6c74223c4ef06df5cef270485c557d4f915f389845e2.
- APK SHA-256: a5e87069831f48b7d9dbb33995df78ddecaeafb613a662f41116cac2d1619566.
- 이번 변경은 Universal만 대상으로 하며 Shyni Play는 변경하지 않았습니다.
