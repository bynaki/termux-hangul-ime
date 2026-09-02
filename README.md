# 모아쓰기 (Termux 한글 입력기)

안드로이드 Termux에서 **물리 키보드로 한글을 입력**하기 위한 입력기(IME).

핫키를 누르면 화면 아래에 입력 팝업이 뜨고, 물리 키보드로 한글 문장을 쓴 뒤 Enter를 누르면
전체 텍스트가 한 번에 터미널로 들어간다. 이름은 거기서 왔다 — 한 글자씩 흘려보내지 않고
**모았다가 한 번에 쓴다**.

## 왜 필요한가

Termux의 `TerminalView`는 IME에게 자신의 inputType을 `InputType.TYPE_NULL`로 보고한다.
"나는 편집 가능한 텍스트 필드가 아니니 원시 키 이벤트를 달라"는 뜻이라, 한글 IME가 조합을
시작하지 않는다. Termux는 받은 키 이벤트를 `getUnicodeChar()`로 변환하는데 여기서는 ASCII만 나온다.
소프트 키보드는 `commitText()` 경로를 타서 잘 되고, 물리 키보드는 이 경로를 타지 않는 것이 차이다.
(Termux 쪽 미해결 이슈: [termux-app#1839](https://github.com/termux/termux-app/issues/1839))

Termux의 `commitText()` 구현 자체는 살아 있으므로, **조합을 이 앱이 직접 하고 완성된 문자열만
커밋하면** 한글이 그대로 들어간다. 그래서 이 앱은:

- 오버레이 권한, 접근성 권한, 클립보드, 앱 전환을 **쓰지 않는다** (요청 권한 0개)
- 물리 키보드 전용이므로 **화면 자판을 그리지 않는다** — 두벌식 오토마타만 구현한다

## 설치

### 요구사항

- 안드로이드 8.0 (API 26) 이상
- 빌드: JDK 17, Android SDK (compileSdk 35)

### 빌드

SDK 경로는 저장소에 포함되지 않는다. `local.properties`를 만들어 지정한다.

```bash
echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > local.properties
```

위는 homebrew로 command line tools를 깐 경우다. Android Studio를 쓴다면 SDK는 보통
macOS `~/Library/Android/sdk`, 리눅스 `~/Android/Sdk`에 있다.

```bash
./gradlew assembleDebug
```

`java`가 `PATH`에 없으면 `JAVA_HOME`을 함께 넘긴다 (경로는 환경에 맞게 바꾼다):

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug
```

### 기기에 설치

`app/build/outputs/apk/debug/app-debug.apk`를 폰으로 옮겨 설치한다.
USB 디버깅을 켠 기기가 연결돼 있으면:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 첫 설정

앱(**모아쓰기**)을 열어 위에서부터 순서대로 진행한다. 화면 맨 위에 현재 어느 단계인지 표시된다.

1. **키보드 활성화** — 시스템 설정에서 이 입력기를 켠다.
2. **이 키보드 선택** — 입력기 목록에서 "모아쓰기"를 고른다.
3. **핫키 변경** — 팝업을 열 키를 직접 눌러 등록한다. 기본값은 `Shift+Space`.

핫키를 고정하지 않은 이유는 한/영 키가 보내는 키코드가 기기·키보드마다 다르기 때문이다.
등록 화면에서 눌렀는데 아무 반응이 없는 키는 시스템이 먼저 가져가는 키라 이 앱에서 쓸 수 없다.
다른 키를 눌러 보면 된다.

## 사용법

Termux를 열고 핫키를 누르면 팝업이 뜬다. 한글을 쓰고 Enter를 누르면 터미널로 들어간다.

| 키 | 동작 |
|---|---|
| 핫키 (기본 `Shift+Space`) | 팝업 열기 / 팝업 안에서는 한↔영 전환 |
| `Enter` | 작성한 텍스트를 터미널로 전송하고 팝업 닫기 |
| `Shift+Enter` | 팝업 안에서 줄바꿈 |
| `Esc` | 취소 (쓰던 내용을 버리고 닫기) |
| `Backspace` | 자모 단위로 역분해 (`값` → `갑` → `가` → `ㄱ`) |
| `←` `→` `Home` `End` | 커서 이동 |

전송하거나 취소하면 팝업과 IME 창이 함께 내려가, 터미널이 다시 화면을 온전히 쓴다.

**팝업이 닫혀 있는 동안에는 어떤 키도 가로채지 않는다.** 영문 타이핑, `Ctrl+C`, 화살표,
Tab 자동완성이 앱을 설치하기 전과 똑같이 동작한다.

### 설정

앱 화면의 **"전송 후 Enter 자동 입력"**을 켜면 전송 직후 Enter가 한 번 더 들어가 명령이 바로 실행된다.
기본값은 꺼짐이라, 전송된 문장을 터미널에서 더 손본 뒤 실행할 수 있다.

## 구조

| 파일 | 역할 |
|---|---|
| `hangul/HangulAutomaton.kt` | 두벌식 조합 오토마타 (안드로이드 의존성 없음) |
| `hangul/DubeolsikKeyMap.kt` | 키코드 → 자모. 각인·레이아웃과 무관하도록 키의 물리 위치로 매핑 |
| `InputBuffer.kt` | 팝업 편집 버퍼 (확정 텍스트 + 커서 + 조합 중 음절) |
| `HangulImeService.kt` | 키 가로채기, 팝업 표시, `commitText()` 전송 |
| `Hotkey.kt` / `Prefs.kt` | 핫키 비교·표기, 설정 저장 |
| `SetupActivity.kt` / `HotkeyCaptureActivity.kt` | 설치 안내, 핫키 등록 |

## 테스트

```bash
./gradlew test
```

오토마타는 순수 로직이라 JVM 유닛 테스트로 검증한다 (조합, 겹모음, 겹받침, 연음, 역분해).

기기에서 동작을 확인할 때:

```bash
adb logcat -s HangulIme:V
```

`adb shell input`으로 보낸 키도 물리 키보드와 똑같이 IME를 거치므로, 전체 흐름을 스크립트로 돌릴 수 있다.
아래는 `Shift+Space`로 팝업을 열고 두벌식 `dkssud`(안녕)을 친 뒤 전송하는 예다.

```bash
adb shell input keycombination 59 62 && adb shell input keyevent 32 39 47 47 49 32 && adb shell input keyevent 66
```

## 알려진 제한

- 두벌식만 지원한다. 세벌식은 `DubeolsikKeyMap`과 같은 형태로 매핑을 추가하면 된다.
- 한자 변환, 입력 히스토리는 없다.
- 화면 자판을 그리지 않으므로 **물리 키보드를 분리하면 이 입력기로는 입력할 수 없다.** 이때 텍스트
  필드를 누르면 상황 안내와 "키보드 전환" 버튼이 뜨므로, 거기서 다른 입력기로 바꾸면 된다.
- Termux 외의 앱에서도 동작하지만, 그쪽은 별도로 검증하지 않았다.
