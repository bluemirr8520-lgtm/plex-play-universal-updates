# Plex Play Universal 1.0.28 검증 기록

- 검증일: 2026-09-30. 시리즈·시즌의 완료 표시 재진입 문제, 수동 완료/미완료 저장 확인 및 시리즈 자체의 컬렉션 적용을 수정했습니다.
- 원인: 기존 `isWatched`는 개별 영상의 `viewCount` 또는 진행률만 확인하고 시리즈의 `leafCount`/`viewedLeafCount`를 사용하지 않았습니다. 화면에서만 `viewCount`를 만들어 표시했기 때문에 메타데이터를 다시 읽으면 풀렸습니다. 컬렉션 정책과 API 유형 매핑에도 시리즈(`show`, type 2)가 빠져 있었습니다.
- JDK 17 / Android SDK 36 / Gradle 8.13 오프라인 `testDebugUnitTest assembleRelease` 성공.
- JVM 테스트 247개 통과, 실패·오류·건너뜀 0. XML 메타데이터 재파싱 후 완료 유지, 부분·빈 시리즈 처리, 미완료 되돌리기, 저장 확인 실패·다른 라이브러리 응답 거부, 지연된 카운터의 읽기 재시도, 시리즈 컬렉션 type 2, 모든 에피소드 페이지 조회, 경로 규칙 충돌·누락·반복·다른 시리즈 혼입 제외를 포함합니다. HTTP 검증은 로컬 합성 서버를 사용했습니다.
- APK 패키지 `io.mirr.plexplay.universal`, versionName `1.0.28`, versionCode `29`, 디버깅 불가 및 16KB zipalign 검증 통과.
- 기존 서명 인증서 SHA-256: `74c178f3841343954e4a6c74223c4ef06df5cef270485c557d4f915f389845e2`.
- APK SHA-256: `b120b5006d1d6f7a76ed9b1878553b09611ce160b1866dbb329077a134ad996f`, 121,233,462 bytes.
- 연결된 Samsung SM-S918N에 `adb install -r --no-incremental` 성공. 1.0.27(code 28)에서 1.0.28(code 29)으로 업데이트됐습니다. 앱 제거와 데이터 초기화를 실행하지 않았습니다.
- lastUpdateTime `2026-09-30 17:25:45`, firstInstallTime `2026-08-10 13:23:30` 유지.
- MainActivity 콜드 실행 `Status: ok`, 실행 프로세스 확인 및 해당 프로세스의 AndroidRuntime 오류 로그 없음. 전면 창은 NotificationShade였으므로 실제 화면 조작은 확인하지 않았습니다.
- 실제 사용자 서버의 시청 기록·컬렉션을 테스트 목적으로 변경하지 않았습니다. 실제 서버에 대한 시리즈 완료/컬렉션 저장은 미검증입니다. 과거 완료 시리즈 자동 처리, 시즌 자체 컬렉션 변경과 하위 에피소드 컬렉션 일괄 변경은 포함하지 않았습니다.
- GitHub 배포는 기존 비공개 저장소에 진행합니다. 업로드한 APK와 문서를 다시 다운로드하여 해시를 비교하고, APK의 서명·버전·정렬을 재확인한 뒤 게시합니다.

개발 시 확인한 참고 자료: [Plex 컬렉션 공식 설명](https://support.plex.tv/articles/201273953-collections/), [Python PlexAPI의 시리즈·시즌 완료 상태 및 에피소드 조회 구현](https://python-plexapi.readthedocs.io/en/latest/_modules/plexapi/video.html).
