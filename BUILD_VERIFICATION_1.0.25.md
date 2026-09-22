# Plex Play Universal 1.0.25 검증 기록

- 2026-09-23: JDK 17 / Android SDK 36, release 빌드 성공.
- JVM 테스트 220개 통과(실패·오류·건너뜀 0). 라이브러리 루트/검색/하위 폴더 이어보기 영역 표시 조건과 화면 확대 단계 검사를 포함합니다.
- 앱 ID io.mirr.plexplay.universal, versionCode 26, versionName 1.0.25.
- APK 기존 서명 인증서, 디버깅 불가, 16KB zipalign 검증 통과.
- APK SHA-256: 4bfb24d1341dff3b295da28beb99c28e598105f78cf4fd4b7d3a50257aaaac3b.
- 실제 서버 영상 재생, 이어보기 API의 서버별 실응답 및 OTT 리모컨 화면은 이번 자동 테스트에서 검증하지 않았습니다. 사용자 시청 기록을 테스트 목적으로 변경하지 않았습니다.
- GitHub 소스와 빌드 소스는 빈 줄/줄바꿈 외 차이가 없음을 확인했습니다.
