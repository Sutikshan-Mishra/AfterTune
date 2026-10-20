# System-wide EQ — research and implementation notes

Goal: apply AfterTune's 10-band EQ to audio from other apps (e.g. Spotify) on an ordinary,
non-rooted phone. Date: 2026-10-12. **Physical testing: none.**

## Mechanisms considered

| Mechanism | Works for a normal app? | Used? |
|---|---|---|
| **Audio-session broadcast protocol** — players send `android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION` / `CLOSE_…` with `EXTRA_AUDIO_SESSION` + `EXTRA_PACKAGE_NAME`; an EQ app attaches an `AudioEffect` to that session | Yes (documented in `android.media.audiofx.AudioEffect`). Since Android 8 these implicit broadcasts only reach a *running* app's context-registered receiver | **Yes — primary path** |
| **Effect control panel** — a player's "Equalizer" button launches `android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL` with its session | Yes, documented; only for players that offer the button | **Yes** |
| **Global output mix (session 0)** | Deprecated by Android; still honoured by some devices, ignored by many | **Experimental opt-in**, reported as "created — audibility unverified" |
| `DUMP` permission + parsing `dumpsys audio` to discover sessions | Needs `adb pm grant`; parses undocumented text output | No (not a normal-install mechanism) |
| Playback capture (`MediaProjection`/`AudioPlaybackCapture`) and re-play | Captures a copy; cannot silence the original, so you'd hear both; apps can opt out | No |
| Microphone loopback | Not an EQ of the source; forbidden by the brief | No |
| AccessibilityService / root / custom audio HAL | Can't apply DSP / not allowed | No |

Effect classes: `DynamicsProcessing` (Android 9+) is used with a 24-band pre-EQ sampled from the app's
real biquad curve, input gain = the automatic headroom, and a −1 dBFS limiter. On Android 8.x the legacy
`Equalizer` is used with the device's fixed bands; headroom is emulated by shifting all bands down.

**SoundID** (Sonarworks) advertises Android support for Spotify and other streaming apps, but publishes
no mechanism. Nothing is assumed about how it works.

## What this means

- Coverage is **per app**, never universal: only players that announce their session (or open our panel).
  Spotify's behaviour on a real phone is **NOT VERIFIED** here.
- If the phone's own sound effects (e.g. Dolby) or another EQ app hold a higher priority on that session,
  Android reports `hasControl() == false`; the app shows "No control" rather than claiming success.
- The status line reflects only observed backend state (attached / failed / no control / waiting).
  Attachment success does not prove audibility, so the UI asks the user to listen.

## Implementation

- `systemeq/SystemEqService.kt` — `specialUse` foreground service + exported receiver for OPEN/CLOSE.
- `systemeq/SystemEqController.kt` — creates/updates/releases effects, tracks control changes.
- `systemeq/SystemEqModel.kt` — pure mapping + status logic (unit tested).
- `MainActivity` — handles `DISPLAY_AUDIO_EFFECT_CONTROL_PANEL`.

## Next practical experiment (on a real phone)

See `docs/MANUAL_TEST_GUIDE.md` §3. If Spotify never appears in the session list, the next step is to
check whether its Equalizer setting lists AfterTune (panel route). If neither works, Spotify is not
reachable by this mechanism on that phone.
