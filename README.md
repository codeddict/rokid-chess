# Rokid Chess · 실물 체스 도우미

**v0.5.2 카메라 수정:** [로키드 촬영 사용법](docs/ROKID_CHESS_CAMERA.md). 휴대폰에서 업데이트 후 **안경 업데이트 → 안경 연결**을 누르고 Hi Rokid **카메라 권한**을 허용하세요. **로키드 카메라 촬영 · 위치 맞추기**로 안경 사진을 받습니다. Rokid 공식 CXR 사진 API로 촬영하며 안경 HUD 앱은 카메라를 직접 점유하지 않습니다. 실제 사진 수신과 연속 촬영 지연은 이번 개발 환경에서 실기 검증 전입니다.

로키드 안경과 Android 휴대폰으로 실제 체스판을 관측하고 다음 이동을 안내하는 앱입니다. 휴대폰의 Stockfish 19가 분석하며 PC 없이 동작합니다. 초기 배치에서 판의 네 모서리를 지정한 뒤 사용합니다. 임의의 중간 판을 사진 한 장으로 자동 복원하지 않으며 실물 인식 정확도는 실기 검증 전입니다.

## 설치 · v0.5.2

[최신 APK 다운로드](https://github.com/codeddict/rokid-chess/releases/download/v0.5.2/rokid-chess-v0.5.2.apk) · [릴리스와 전체 대응 소스](https://github.com/codeddict/rokid-chess/releases/latest)

1. 휴대폰에서 릴리스 Assets의 `rokid-chess-v0.5.2.apk`를 받아 기존 앱 위에 설치합니다. Android 12 이상 ARM64입니다. 기존 앱을 삭제할 필요는 없습니다.
2. Hi Rokid에서 안경을 연결하고 체스 앱의 **안경 업데이트**를 누릅니다.
3. **안경 연결**을 누르고 Hi Rokid **카메라 권한**을 허용한 뒤, **안경 버전·난이도 점검**으로 버전과 모드를 확인합니다.
4. 실제 말을 초기 배치로 놓고 **내 말 색 변경 → 로키드 카메라 촬영 · 위치 맞추기 → 실물 판 안내 시작** 순서로 사용합니다.

v0.5.0 이하는 업데이트 주소가 옛 웹하드로 고정되어 있어 이번 한 번 APK 직접 설치가 필요합니다. v0.5.1부터 **앱 업데이트 확인 / 설치**는 이 GitHub 저장소의 릴리스에서 안내와 APK를 받습니다. 앱 사용자는 GitHub 로그인 없이 다운로드할 수 있습니다. Android 설치 확인은 직접 눌러야 합니다.

[업데이트·점검 안내](docs/ROKID_CHESS_UPDATER.md) · [안경 조작·백/흑 모서리 선택](docs/ROKID_CHESS_GLASS_CONTROLS.md) · [실물판 사용법](docs/ROKID_CHESS_LIVE.md)

## 소스와 빌드

`chessphone`, `chessglasses`, `chessshared`에 앱 소스가 있고 `.tools/stockfish-android/stockfish`에 수정하지 않은 Stockfish SF19 소스가 있습니다. 정확한 NNUE·앱 의존 파일·빌드 스크립트를 포함한 전체 대응 소스는 각 릴리스의 `rokid-chess-source-v버전.zip`으로 제공합니다. APK와 소스 ZIP은 Git 저장소 대신 Releases에 둡니다. 해시는 `SHA256SUMS.txt`, 업데이트 정보는 `update.txt`에 있습니다.

JDK 17, Android SDK 36, NDK 28.2.13676358을 준비하고 `local.properties`에 SDK 경로를 설정합니다. 엔진 빌드 방법은 `scripts/build-chess-engine.py`에 있습니다. `gradlew.bat :chessphone:assembleDebug`로 휴대폰 APK에 안경 APK를 포함해 빌드합니다. 본인 빌드의 서명은 배포 APK 서명과 다르므로 기존 설치본에 덮어쓸 수 없습니다.

Stockfish 19는 GPLv3이며 [라이선스와 고지](chessphone/src/main/assets/licenses/NOTICE.txt)를 확인하세요. chesslib와 OpenCV 등은 각 라이선스를 따릅니다. Rokid SDK는 해당 공급자의 조건을 따릅니다. 계정 인증정보·서명 키·개인 대국 기록은 포함하지 않습니다.
