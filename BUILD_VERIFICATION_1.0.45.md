# Plex Play Universal 1.0.45 검증 기록

- 앱 ID: `io.mirr.plexplay.universal`, 버전 코드: 46
- `testDebugUnitTest`: 41개 스위트, 464개 통과, 실패·건너뜀 없음
- `assembleRelease`: 성공. `compileDebugAndroidTestKotlin`: 성공; UI 테스트 실기 실행은 하지 않음
- 기존 앱과 같은 인증서 서명 및 APK 정렬 확인
- VLC ABI: arm64-v8a, armeabi-v7a, x86, x86_64. MPV ABI: arm64-v8a, armeabi-v7a, x86_64
- APK 크기: 161,010,428 bytes
- APK SHA-256: `407af48a6281be59ada61202d7d79f34bc8d09f10cc8d872d7ebd68793219284`
- 서명 인증서 SHA-256: `74c178f3841343954e4a6c74223c4ef06df5cef270485c557d4f915f389845e2`
- MPV 소스: `PlexPlayUniversal-mpv-native-source.zip`과 이 릴리스 태그의 Universal 앱 소스. 네이티브 파일 30개와 공식 런타임의 바이트 일치를 확인했습니다.

연결된 기기가 없어 MPV/VLC 재생, 자막 이동, 품질 변환, HDR, TV 리모컨과 오디오 출력은 실제 기기에서 확인하지 못했습니다. 단위 테스트 통과가 이 동작들의 실기 성공을 의미하지는 않습니다. 설치는 수행하지 않았습니다.
