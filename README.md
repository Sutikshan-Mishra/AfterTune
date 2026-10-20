# AfterTune — unofficial HD 4.50 BTNC companion for Android

An unofficial, community-built companion for the **Sennheiser HD 4.50 BTNC**: a real 10-band EQ with presets
and saved profiles, optional system-wide EQ for apps that support it, ambient listening (microphone
passthrough), connection and battery status, and honest NoiseGard guidance.
Developed by Sutikshan Mishra. Not affiliated with or endorsed by Sennheiser.

Native Android · Kotlin · Jetpack Compose · Material 3 · AndroidX Media3. minSdk 26 (Android 8.0), target 35.
Package ID is unchanged (`dev.hd450btnc.companion`, debug: `…companion.debug`), so it updates in place.

## Features and verification status

Legend — **U**: unit-tested logic · **B**: Android build verified · **D**: tested on a device.
Nothing has been built or device-tested yet (see BUILD_STATUS.md); B and D are therefore open for every row.

| Feature | Implementation | U | B | D |
|---|---|---|---|---|
| In-app 10-band EQ, presets, profiles, headroom | Media3 audio processor running RBJ biquads | ✓ | — | — |
| **System-wide EQ** (experimental, opt-in) | Android audio-session protocol + `DynamicsProcessing` (Android 9+) / `Equalizer` (8.x); effect-panel entry; optional legacy global mix | mapping + status ✓ | — | — |
| **Ambient listening** (phone mic or headset mic) | `AudioRecord` → DC block → ramped gain → limiter → `AudioTrack`, `microphone` foreground service | DSP, routing rules, state machine ✓ | — | — |
| Ambient gain slider, mute, reset | −30…+12 dB, default −6 dB at every start, ramped | ✓ | — | — |
| Bluetooth status, battery when reported | A2DP/HFP proxies, no scanning | ✓ | — | — |
| NoiseGard | **Not controllable from the app** — physical shortcut shown | n/a | — | — |
| Rebrand, blue design system, launch-screen credit | Theme tokens (AA contrast checked), in-app launch screen | — | — | — |

### System-wide EQ — scope

Android offers no supported way for a normal app to process *all* other apps' audio. AfterTune uses the
documented route: players announce their audio session, and the EQ attaches to it. That covers only apps that
announce sessions (or that open AfterTune from their own "Equalizer" button). Whether Spotify does so on your
phone is **not verified**. The app shows exactly which sessions it attached to, and whether it has control.
Details: [docs/SYSTEM_EQ_RESEARCH.md](docs/SYSTEM_EQ_RESEARCH.md).

### Ambient listening — scope

- **Phone mic** (default): music keeps full quality and is mixed in; hears near the phone, not at your ears;
  Bluetooth adds delay.
- **Headset mic** (Android 12+): switches the headset to its hands-free call link (mono, call-grade; media usually
  pauses or degrades until Stop). Not verified on this headset.
- Not hearing protection. Never relies on or changes NoiseGard. Never records.
  Details: [docs/AMBIENT.md](docs/AMBIENT.md).

### NoiseGard

Sennheiser publishes no control protocol for the HD 4.50 BTNC, its Smart Control app excludes this model, and
the headset exposes only standard A2DP/AVRCP/HFP/HSP profiles. Switch NoiseGard on the headphones:
**press and hold Volume + and Volume − together** (LED flashes violet 3×). See
[docs/ANC_RESEARCH.md](docs/ANC_RESEARCH.md).

## Get the APK (no Android Studio needed)

The workflow `.github/workflows/android.yml` (unchanged) runs unit tests and builds the debug APK.

1. Commit and push to your GitHub repository (see "Updating your repository" below).
2. Open **Actions → Build debug APK** (runs on push, or **Run workflow**).
3. When green, download the artifact **hd450btnc-companion-debug-apk** → `app-debug.apk`.
   Test reports: **unit-test-reports**.
4. Install on the phone (allow "Install unknown apps" when asked). It replaces the previous debug build.

If the run fails, open the failed step: the log names the file and line.

## Updating your repository

Either copy the contents of the delivered project folder over your repository (keeping your `.git` folder),
or apply the patch:

```
git apply --3way aftertune-phase3.patch
git add -A
git commit -m "AfterTune phase 3: ambient listening, system-wide EQ, rebrand"
git push
```

## Permissions

| Permission | Why | When |
|---|---|---|
| Nearby devices (`BLUETOOTH_CONNECT`, Android 12+) | Connection and battery status | Headphones tab |
| Microphone (`RECORD_AUDIO`) | Ambient listening only | When you press Start |
| Notifications (Android 13+) | Shows ambient/system-EQ notifications with Stop | With Start / when enabling system EQ |
| `MODIFY_AUDIO_SETTINGS` | Attach EQ effects; hands-free routing | Install-time |
| Foreground services (media playback, microphone, special use) | Player, ambient, system-EQ receiver | Only while those features run |

No location, scanning, internet, storage or analytics.

## Project layout

```
app/src/main/java/dev/hd450btnc/companion/
  dsp/       Biquad, EqEngine, EqResponse, PinkNoise                (pure, tested)
  eq/        Presets, EqController, persistence                     (pure, tested)
  ambient/   AmbientDsp, AmbientModel (pure, tested); AmbientEngine, AmbientService, AudioDevices
  systemeq/  SystemEqModel (pure, tested); SystemEqController, SystemEqService
  bt/        HeadsetState (pure, tested); HeadsetMonitor
  audio/     EqAudioProcessor, AudioEngine, PlaybackService
  ui/        Theme, components, LaunchScreen, Sound / Ambient / Headphones / Settings
docs/        ANC, system-EQ and ambient notes, manual test guide, specs
```

## Licence & acknowledgements

AndroidX (Compose, Material 3, Core, Activity, Lifecycle, Media3) — Apache License 2.0. EQ filters follow
R. Bristow-Johnson's Audio EQ Cookbook; pink noise uses Paul Kellet's filter. "Sennheiser", "HD 4.50 BTNC" and
"NoiseGard" are trademarks of their owners, used only to identify compatible hardware.

## Fonts
The UI uses [TeX Gyre Heros](https://www.gust.org.pl/projects/e-foundry/tex-gyre) (Regular, Bold), a free Helvetica-style
typeface, bundled in `app/src/main/res/font/` under the GUST Font License (`docs/TEXGYREHEROS_LICENSE.txt`).
Helvetica itself is a commercial font and is not bundled; Medium text uses Regular and SemiBold uses Bold.
