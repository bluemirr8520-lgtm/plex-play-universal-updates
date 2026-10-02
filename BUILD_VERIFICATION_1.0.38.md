# Plex Play Universal 1.0.38 검증 기록

- 검증일: 2026-10-02 (한국 시간).
- 범위: 원본·VLC의 불필요한 자막 갱신 감소, VLC 화면 프레임 속도 힌트, 자동 네트워크 버퍼 조정. 디코더·영상 품질·Plex 서버 설정·시청 기록은 변경하지 않았습니다.
- 자막: `SubtitleRenderState`가 같으면 기존 `PlaybackViewUpdateCache`로 스타일 갱신을 건너뜁니다. 내용·글꼴·크기가 바뀌는 경우에 필요한 레이아웃은 유지하고, 위치만 바뀔 때 전체 레이아웃을 다시 요청하지 않습니다. 가로 줄바꿈 결과는 내용·글꼴·크기·표시 폭을 키로 재사용합니다. v1.0.37의 실시간 드래그 미리보기는 유지했습니다.
- VLC: 선택된 비디오 트랙의 분수 프레임 속도를 읽어 비디오 `SurfaceView`에 전달합니다. 재생 배속을 반영하고, 일시정지·종료 시 0으로 해제합니다. 새 Surface에는 다시 적용하며 같은 요청은 반복하지 않습니다. 디코더 Surface를 해제하거나 교체하지 않습니다.
- Android 11 이상에서 지원합니다. Android 12 이상은 `CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS`만 요청합니다. 실제 화면 주사율은 플랫폼이 결정합니다. [Android 공식 문서](https://developer.android.com/media/optimize/performance/frame-rate)
- VLC 자동 모드: 일반 네트워크 영상 3,000ms, 4K 또는 50fps 이상 6,000ms, 저메모리 기기 최대 3,000ms. 다른 최적화 모드와 로컬 파일 버퍼는 변경하지 않았습니다. 시작·탐색 대기는 늘 수 있습니다.
- `--offline --no-daemon testDebugUnitTest assembleRelease compileDebugAndroidTestKotlin` 성공: 6분 19초. 기존 deprecation 및 Android 계측 테스트 경고는 남아 있으며 오류는 없었습니다.
- JVM 테스트 **33개 스위트, 387개 통과**, 실패·오류·건너뜀 0.
- 새 테스트 5개: 소수 프레임 속도 보존, 배속 반영, 일시정지 시 해제, 잘못된/없는 메타데이터 처리, 잘못된 배속·오버플로 처리. 기존 버퍼·갱신 캐시·자막 드래그 회귀 테스트도 통과했습니다.
- Android 계측 테스트는 컴파일만 했으며 실행하지 않았습니다. 실제 기기의 장시간 4K 재생, 프레임 드롭·버퍼링 감소 및 주사율 변경은 미측정입니다. 모든 영상의 무끊김 재생을 보장하지 않습니다.
- APK: `io.mirr.plexplay.universal`, versionName `1.0.38`, versionCode `39`, 디버깅 불가, 16KB zipalign 검증 통과.
- 기존 서명 SHA-256: `74c178f3841343954e4a6c74223c4ef06df5cef270485c557d4f915f389845e2`.
- APK SHA-256: `4a72132b5a004df766c9c1fae9a0c94dd327e5aa3a17743fbb14a081e2473281`, 121,301,190 bytes.
- LibVLC ABI: ARM64, ARMv7, x86, x86_64. Release APK에 합성 테스트 영상은 포함하지 않았습니다.
- 휴대폰: 빌드 검증 시 USB 디버깅 기기가 없어 설치·실기기 테스트를 진행하지 못했습니다. GitHub 배포 검증과는 별개입니다.
- GitHub 배포 절차: 테스트한 소스와 APK·체크섬·설명서를 반영하고, 초안의 첨부 파일 7개를 다시 내려받아 해시·APK 서명·버전·16KB 정렬을 검증한 후 공개합니다. 서버 시청 기록·컬렉션 변경은 수행하지 않았습니다.
