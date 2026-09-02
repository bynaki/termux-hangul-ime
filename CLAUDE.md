# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

This machine has no system Java on `PATH`, so **every Gradle invocation needs `JAVA_HOME`**:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug
```

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test
```

Run one test class, or one method (test names are Korean and contain spaces, so quote them):

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest --tests "io.github.bynaki.moa.hangul.HangulAutomatonTest.겹받침을 조합한다"
```

`adb` is not on `PATH` either — it lives at `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`.
The SDK root is pinned in `local.properties` (untracked).

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

```bash
adb logcat -s HangulIme:V
```

## The constraint that shapes the whole design

Termux's `TerminalView` reports `InputType.TYPE_NULL` to the IME — "I am not an editable text field, send me raw key events." So a Korean IME never starts composition with a hardware keyboard, and Termux turns the raw key events into ASCII via `getUnicodeChar()`. Its soft-keyboard path works only because that path goes through `commitText()`.

Termux's `commitText()` implementation is intact and forwards straight to the terminal. **That single fact is why this app is an IME rather than an overlay app**: an overlay/accessibility design cannot push text into `TerminalView` at all, whereas an IME can compose Hangul itself and commit finished text. Consequences that are easy to undo by accident:

- **The app requests zero runtime permissions.** No `SYSTEM_ALERT_WINDOW`, no accessibility service, no clipboard. If a change starts needing one, the design has drifted — check `AndroidManifest.xml`, which deliberately has no `uses-permission`.
- **Composing text is never sent to the app.** `setComposingText()` would be dropped by Termux. Only completed strings leave, via `commitText()` in `HangulImeService.commitAndClose()`.
- **No soft keyboard is drawn.** This is a hardware-keyboard input method; the IME window holds a text buffer, not keys.
- There are no androidx dependencies (plain `android.app.Activity`, framework views). Keep it that way unless there is a reason.

## Architecture

Key flow: `HangulImeService.onKeyDown` → `handlePopupKey` → `InputBuffer` → `HangulAutomaton`, then on Enter `InputBuffer.take()` → `InputConnection.commitText()`.

| File | Role |
|---|---|
| `hangul/HangulAutomaton.kt` | 두벌식 syllable state machine (초성/중성/종성 indices). No Android imports — this is why it is JVM-testable. |
| `hangul/DubeolsikKeyMap.kt` | Key → jamo. Maps from **physical key position** (`KEYCODE_A`..`KEYCODE_Z`), not `getUnicodeChar()`, so it is independent of the keyboard's engraving and the system layout. |
| `InputBuffer.kt` | Popup edit buffer: committed text + cursor + the in-flight syllable held inside the automaton. |
| `HangulImeService.kt` | Key interception, popup window, `commitText()`. |
| `Hotkey.kt` / `Prefs.kt` | Hotkey matching/labelling, SharedPreferences. |

### Invariants worth preserving

- **While the popup is closed, no key may be consumed** except the hotkey. `onKeyDown` returns `super.onKeyDown(...)` for everything else. Breaking this breaks ordinary Termux use — English typing, `Ctrl+C`, arrows, Tab completion. That regression check belongs in any manual test pass.
- **Every key consumed on DOWN must have its UP swallowed too**, via `consumedKeys`, or the app receives half an event. Note the ordering in `onKeyDown`: `consumedKeys += keyCode` runs *after* `handlePopupKey`, which matters because Enter/Esc call `closePopup()` and that clears the set.
- `HangulAutomaton.input()` returns only the text that just became final; the still-composing syllable stays in `composing`. Callers append the return value and render `composing` separately.
- `InputBuffer` calls `flushComposing()` on anything that breaks composition (cursor moves, mode switch, non-jamo insert). Buffering the whole line is what removes the need to send correcting backspaces to the terminal for 연음 — that was the reason for choosing batch-send over live typing.
- The hotkey is user-captured (`HotkeyCaptureActivity`), not a fixed constant, because the 한/영 key emits different keycodes per device and some keys are swallowed by the system before any app sees them. Do not replace it with a hardcoded default list.

## Verified end-to-end

The three load-bearing assumptions were confirmed on device (SM-X526N, Android 16, Corne Keyboard, Termux `com.termux`):

1. The IME **does** receive hardware key events while Termux reports `TYPE_NULL`.
2. Termux's `commitText()` **does** deliver Hangul to the terminal.
3. `onEvaluateInputViewShown()` **does** force the IME window open with a hardware keyboard attached.

Regression checked too: with the popup closed, `Ctrl+C` and English typing reach Termux untouched.

Should any of these regress on another device, the fallbacks are:

1. Keys not reaching the IME → an `AccessibilityService` with `FLAG_REQUEST_FILTER_KEY_EVENTS` captures them globally, leaving the IME as a `commitText()` sink (costs one accessibility permission).
2. `commitText()` not landing in the terminal → Termux's `enforce-char-based-input = true` in `~/.termux/termux.properties` makes it report a real text inputType, where `commitText` is guaranteed.
3. The IME window not opening → draw the popup in a `TYPE_APPLICATION_OVERLAY` window instead (costs `SYSTEM_ALERT_WINDOW`).

Each fallback trades away the zero-permission property above, so treat them as last resorts.

### Driving the device without touching it

`adb shell input` events go through the IME exactly like the physical keyboard, which makes the whole flow scriptable. Keycodes are `KEYCODE_A`=29 through `KEYCODE_Z`=54, so 두벌식 `dkssud` (안녕) is `32 39 47 47 49 32`.

```bash
adb shell input keycombination 59 62 && adb shell input keyevent 32 39 47 47 49 32 && adb shell input keyevent 66
```

That opens the popup with Shift+Space, types 안녕, and commits. Pair it with `adb exec-out screencap -p > shot.png` and `adb logcat -s HangulIme:V` to see both the popup and what the service received.

## Known gap

The IME still draws no soft keyboard, so with the hardware keyboard detached nothing can be typed through it. What used to be a dead end is now signposted: `popup_input.xml` holds two layers under one root, and `onEvaluateInputViewShown()` returns `popupActive || !hasHardwareKeyboard()`, so when `Configuration.keyboard == KEYBOARD_NOKEYS` the window opens on `nokeys_layer` — an explanation plus a button calling `showInputMethodPicker()`. `onConfigurationChanged` closes any open popup on detach with `hideWindow = false`, because hiding the window there would take the explanation away with it.

Note what this means for the invariant above: the input view is now shown while the popup is closed. Key handling is unaffected — `popupActive` is still false, so `onKeyDown` consumes nothing. Any change here must keep those two conditions separate.
