# Plex Play Universal 1.0.18 검증

검증일: 2026-09-15 (KST)

## 변경 범위

시리즈(show)의 하위 목록에서 서로 다른 비재생 시즌/폴더(season/directory)가 2개 이상이면 해당 시리즈의 `/library/metadata/{id}/allLeaves` 집계 항목만 숨긴다. 제목 비교로 항목을 제거하지 않는다. 폴더가 1개 이하인 경우, 같은 제목의 실제 시즌/회차, 다른 시리즈의 집계 항목, 기존 항목 순서는 보존한다. 중복 폴더 응답은 정규화한 식별자로 한 번만 센다.

`PlexRepository.children()` 반환에 표시 규칙을 적용했다. 유지되는 `allLeaves` 경로에 `/children`을 덧붙이지 않도록 목록 경로 계산도 수정했다. 서버 원본 데이터/폴더/시청 기록을 변경하지 않는다. 다음 재생 및 자막 코드, Shyni Play 소스는 변경하지 않았다.

## 빌드와 테스트

- 오프라인 `testDebugUnitTest assembleDebug assembleDebugAndroidTest`: 성공, 4분 51초.
- JVM 테스트: 14개 묶음, 139개 통과. 실패/오류/건너뜀 0.
- 신규 표시 규칙 회귀 테스트 14개: 다중/단일/빈 폴더, 중복 식별자, 번역된 집계 제목, 실제 동명 시즌/영상 보존, 다른 부모 집계 보존, show 외 목록 불변, 스페셜 시즌, XML의 untyped Directory, 회차 수와 폴더 수 구분, 쿼리/끝 슬래시, allLeaves/children 목록 경로.
- 신규 테스트는 합성 목록/XML을 사용한다. 실제 Plex 서버의 특정 시리즈 화면을 읽어 검증하지는 않았다.

## 휴대폰

- SM-S918N, Android 16 / API 36에 `adb install -r` 업데이트 성공.
- 설치 버전 `io.mirr.plexplay.universal` 1.0.18 / versionCode 19 확인.
- 앱 삭제/데이터 초기화 없음.
- 기존 자막 회귀 기기 테스트 `ExternalSubtitleBridgeDeviceTest`: 2개 통과(1.514초). 샘플 SRT/VTT와 임시 TTF만 사용했으며 서버 미디어/계정/시청 기록은 건드리지 않음.
- Shyni Play 설치 버전 1.0.3 / versionCode 4 유지 확인. Shyni 설치/소스 수정 없음.

## APK

- 파일: `../PlexPlayUniversal-1.0.18-debug.apk`
- 크기: 134,824,349 bytes
- SHA-256: `6479ee65bf57b36389ddfe21c01a66ba45b13bd78fb9619e18e1c9ee9727187f`
- 서명 검증 및 `zipalign -c -P 16 4` 통과.
- 인증서 SHA-256: `74c178f3841343954e4a6c74223c4ef06df5cef270485c557d4f915f389845e2`.
- 디버그 서명 테스트용. 연결된 휴대폰의 기존 설치본과 같은 서명이다.
- GitHub/Ubuntu 서버에는 업로드하지 않았다.
