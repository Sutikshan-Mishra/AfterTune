# Build status

Last updated: 2026-10-12 (Phase 3 — AfterTune)

## Environment constraints (authoring sessions)

- No Android SDK; Google Maven, Maven Central and Gradle download hosts blocked (HTTP 403). Checked once, not retried.
- The user's NOISEGARD GitHub repository was **not accessible** from this session. Phase 3 was applied to the
  Phase 1/2 project this session originally produced, and is also delivered as a patch
  (`aftertune-phase3.patch`) against that baseline.
- Consequence: **no APK built; Android-dependent code not compiled here.** The GitHub Actions workflow
  (unchanged, artifact `app/build/outputs/apk/debug/*.apk`) is the build route.

## Verified in this session

| Check | Result |
|---|---|
| Pure-Kotlin core + all unit tests (Kotlin 2.0.21 bundled with Gradle 8.14.3, JUnit-4-compatible shim) | **57 / 57 pass** (38 existing + 19 new) |
| All Kotlin sources parsed by the compiler | No syntax errors; remaining errors are unresolved Android/Compose symbols (no SDK) |
| Theme contrast (WCAG) | Accent and all text tokens ≥ 4.5:1 on their surfaces, dark and light |
| `DynamicsProcessing` constructor / Config.Builder / Eq / EqBand / Limiter signatures | Checked against Android 14 framework source |
| Media3 1.5.1 APIs (phase 1/2) | Checked against source |

New tests: ambient gain mapping and labels, limiter ceiling/recovery, DC blocker, processor fade-in / ramping /
mute, ambient routing (speaker-feedback guard, Android 12 + hands-free requirement, stop-on-route-loss),
ambient state machine, system-EQ band mapping and legacy Equalizer mapping with headroom, truthful status
summaries, own-session skipping, new settings persistence.

## Feature status

| Feature | Implemented | Unit-tested | Build-tested | Device-tested |
|---|---|---|---|---|
| In-app EQ, presets, profiles, player (phase 1/2) | Yes | Yes | No | No |
| Rebrand, blue palette, exact header text, launch-screen credit | Yes | n/a | No | No |
| Ambient — phone mic | Yes | Core yes | No | **No** |
| Ambient — headset mic (HFP, Android 12+) | Yes, experimental | Rules yes | No | **No** |
| Ambient gain / mute / reset / meters / latency estimate | Yes | Core yes | No | No |
| System-wide EQ via session broadcasts + effect panel | Yes, experimental | Mapping/status yes | No | **No — Spotify NOT VERIFIED** |
| Global output mix (session 0) | Yes, opt-in, labelled unverified | n/a | No | No |
| NoiseGard control | Not implemented (no verified protocol) | n/a | n/a | n/a |

## Known limitations

- System-wide EQ reaches only apps that announce their audio session or open AfterTune's effect panel.
- Headset-mic ambient forces the hands-free call link (mono, call-grade, media interrupted).
- Total ambient delay over Bluetooth is not measurable by the app; use the clap test.
- Launch screen has a 700 ms minimum so the credit is readable; it otherwise waits only for start-up checks (≤ 1.5 s).

## Next steps

1. Commit/push (README "Updating your repository"); run **Actions → Build debug APK**.
2. If compilation fails, fix the reported file/line (most likely Compose/Media3/audiofx parameter details).
3. Install and run `docs/MANUAL_TEST_GUIDE.md`; record results here (device model, Android version, route, mechanism).
