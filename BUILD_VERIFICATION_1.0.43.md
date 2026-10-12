# Plex Play Universal 1.0.43 검증 기록

검증일: 2026-10-12.

## 수정 범위

- 기존 홈 화면에서는 뒤로가기 대상이 없으면 앱의 BackHandler가 비활성화되어 Android 기본 종료 처리로 넘어갈 수 있었습니다. 항상 루트 처리를 등록하고, 더 뒤로 갈 화면이 없으면 종료 확인창을 표시합니다. 플레이어·설정의 하위 BackHandler 우선순위는 유지합니다.
- Activity의 리모컨 Back 입력은 짝이 맞는 키 해제에서 한 번만 전달합니다. 반복 down, 취소, 창 포커스 상실 이후 해제는 별도 화면 이동을 일으키지 않습니다. 연결 설정은 목록보다 먼저 닫습니다.
- 일반·VLC 진행 막대에 고정 `setKeyTimeIncrement(10_000)`을 지정합니다. 기존에는 명시적인 시간 간격 없이 기본 분할 간격을 사용했습니다. [Android DefaultTimeBar API](https://developer.android.com/reference/androidx/media3/ui/DefaultTimeBar)
- 공통 반복 입력 제한으로 길게 누르는 탐색을 최대 500ms마다 10초로 조정합니다. 처리 시점의 단조 증가 시계를 사용하므로 UI 지연 후 이전 타임스탬프의 키가 몰려도 한꺼번에 따라잡지 않습니다. 짧은 개별 누름과 방향 전환은 유지합니다.
- 누적 목표의 무입력 확정 시간을 600ms로 조정하고, 필터링한 반복 신호는 이동량을 늘리지 않고 대기만 유지합니다. 진행 막대의 스크럽 시작은 현재 누름의 반복 제한 상태를 초기화하지 않습니다.
- 영상 화질·디코더·자막·컬렉션 규칙·서버 및 rclone 설정과 터치 진행 막대의 직접 위치 지정은 변경하지 않았습니다.

## 최종 빌드 및 테스트

- 앱 ID `io.mirr.plexplay.universal`, 버전 `1.0.43`, versionCode `44`.
- Java 17 / Android SDK 36 / Gradle 8.13.
- `testDebugUnitTest assembleRelease compileDebugAndroidTestKotlin` 최종 성공.
- JVM 테스트 **450개 / 39개 스위트**, 실패·오류·건너뜀 **0**.
- 새 단위 테스트: Back 누름/해제·반복·취소·창 전환 7개, 탐색 속도 제한·중복 전달·짧은 누름·방향 전환·지연 입력·무입력 대기 12개.
- 최종 재검증 전에 추가 테스트의 정수 자료형 컴파일 오류를 수정했습니다. 앱 소스와 UI 테스트 컴파일은 통과했으며, 최종 단위 테스트 결과에는 해당 추가 시나리오도 포함됩니다.

## APK 검증

- 파일: `PlexPlayUniversal.apk`, **121,333,598 bytes**.
- SHA-256: `3db5420d4e15ff096969bbaf430ecb5b5021a44bbc0369886a7dca9be8204253`.
- 기존 서명 인증서 SHA-256: `74c178f3841343954e4a6c74223c4ef06df5cef270485c557d4f915f389845e2`.
- `apksigner verify --verbose --print-certs`, 앱 ID·버전·비디버그 구성, `zipalign -c -P 16 4` 확인.
- LibVLC ABI ARM64, ARMv7, x86, x86_64 확인. 테스트용 UHD 합성 영상은 배포 APK에 포함하지 않습니다.
- 빌드 APK와 별도 보관 APK의 SHA-256 일치를 확인했습니다. 기계 판독 결과는 `BUILD_VERIFICATION.json`에 기록합니다.

## 실기 미확인 범위

새 `AppBackHandlerTest` 3개와 `RemoteTimeBarTest` 2개는 **컴파일만 완료**했습니다. 연결된 ADB 기기가 없고 SDK에 에뮬레이터가 없어 실행하지 않았습니다. 실제 OTT의 리모컨 이벤트, 재생 화면 복귀, 종료 확인창 포커스 및 일반/VLC 장편 영상 탐색은 실기 확인이 필요합니다. 사용자가 보고한 현상을 기기에서 재현했다고 주장하지 않습니다.

이 기록은 로컬 빌드 검증 결과이며 GitHub 게시 결과와는 구분합니다. 업로드한 파일은 다시 내려받아 해시·서명·버전을 검증하고 별도 `PUBLISH_VERIFICATION.json`에 기록합니다. 휴대폰/OTT 설치와 서버 변경은 하지 않으며, 기존 v1.0.42 배포 파일은 보존합니다.
