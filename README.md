# Rokid Chess · 안경 단독 체스 v0.6.2

**v0.6.2 프레임 수신 수정:** 버리던 YUV 미리보기를 직접 사진/연속 관측으로 전달합니다. 프레임 미수신 시 지원 해상도·출력 조합을 자동 시도하며 Full HD도 처리합니다. 안경의 **버전·상태**에서 마지막 경로와 결과/이미지 수치를 볼 수 있습니다. 프리뷰만 있는 조건부터 보정·두 수의 안정 관측·실제 Stockfish 추천까지 호스트 통합 시험을 통과했지만, 실제 안경 카메라 성공은 기기 미연결로 아직 검증하지 못했습니다.

촬영, 사진 위 네 모서리 조정, OpenCV 판 추적, Stockfish 19 분석, 세 난이도 설정과 기보 저장을 안경 내부로 옮겼습니다. 대국 중 휴대폰·Hi Rokid·인터넷 연결을 사용하지 않는 구조입니다. 휴대폰 앱은 안경 앱 설치와 업데이트에만 사용합니다.

**실제 안경의 Camera2 접근·권한 화면·터치패드 조작·가독성·처리 속도·발열·체스판 정확도는 현재 기기 미연결로 실기 검증 전입니다.** 펌웨어가 일반 앱의 카메라 접근을 제한하면 기기에서 오류 확인이 필요합니다.

## 설치와 시작

[최신 릴리스와 APK·전체 대응 소스](https://github.com/codeddict/rokid-chess/releases/latest) · [안경 단독 조작 안내](docs/ROKID_CHESS_STANDALONE.md)

1. 휴대폰 앱의 **앱 업데이트 확인 / 설치**로 v0.6.2을 설치합니다. 직접 설치할 때는 릴리스 Assets의 `rokid-chess-v0.6.2.apk`를 사용합니다. 기존 앱을 삭제하지 마세요.
2. 새 휴대폰 화면의 **안경 앱 설치 / 업데이트**를 누르고 전송 완료를 기다립니다. 전송 중 휴대폰 앱을 열어 두세요.
3. 안경 앱 목록에서 **실물 체스 도우미**를 직접 열고 **안경 단독 v0.6.2** 표시를 확인합니다. 이후 설치 연결을 종료할 수 있습니다.
4. 안경에서 **탭 → 메뉴 → 새 대국(백/흑) → 초기화 확인 → 카메라 권한 허용** 순서로 진행합니다. 사진 위 커서를 스와이프/탭으로 조정하고 a8 → h8 → h1 → a1 네 모서리를 확정합니다.
5. 메뉴에서 난이도와 **안내 시작**을 선택합니다. 두 번 탭하면 안내로 돌아갑니다.

## 소스와 검증

`chessglasses/src/main/java`와 `chesscore/java`는 안경 UI·카메라·규칙·추적·엔진 코드입니다. `chessphone/src/installer/java`는 설치/업데이트용 휴대폰 앱입니다. 이전 `chessphone/src/main/java`는 역사적 코드로 남아 있으며 v0.6.2 휴대폰 APK에 컴파일되지 않습니다. 안경 APK에는 인터넷/Bluetooth 권한과 CXR 런타임이 없고 자체 Stockfish/OpenCV를 포함합니다.

총 71개 검사(실패·오류·건너뜀 0), 두 앱 빌드/lint 오류 0을 확인했습니다. 실제 Stockfish/OpenCV 검사는 호스트 Windows에서 수행했으며 실제 안경 검증을 대신하지 않습니다. 코너 조작, EXIF 좌표, YUV 변환, 게임/난이도/추적과 업데이트 계약을 포함합니다.

전체 대응 소스와 정확한 Stockfish NNUE는 릴리스의 `rokid-chess-source-v0.6.2.zip`에 있습니다. APK/소스 ZIP/NNUE는 Git 저장소 대신 Releases에 둡니다. 해시는 `SHA256SUMS.txt`, 업데이트 정보는 `update.txt`입니다. v0.5.1 이상 앱은 GitHub 업데이트 버튼을 사용할 수 있습니다. v0.5.0 이하는 APK 직접 설치가 필요합니다.

빌드에는 JDK17·SDK36·NDK28.2.13676358을 준비하고 SDK 경로를 `local.properties`에 설정합니다. `scripts/build-chess-engine.py`는 Stockfish를 `chessglasses/src/main/jniLibs`로 빌드합니다. `gradlew.bat :chessphone:clean :chessphone:assembleDebug`로 안경 APK를 포함한 휴대폰 설치본을 만듭니다. 본인 빌드는 배포본 서명과 달라 기존 설치본에 덮어쓸 수 없습니다.

Stockfish 19는 GPLv3입니다. [라이선스 고지](chessglasses/src/main/assets/licenses/NOTICE.txt)와 각 의존성 조건을 확인하세요. 계정 인증정보·서명 키·개인 대국 사진/기보는 포함하지 않습니다.
