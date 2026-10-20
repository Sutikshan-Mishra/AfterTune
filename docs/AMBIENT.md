# Ambient listening (microphone passthrough)

Status: **implemented, unit-tested core, not compiled or tested on a device** (see BUILD_STATUS.md).

## Signal path

`AudioRecord` (mono 16-bit) → DC blocker → smoothed gain → peak limiter (−2 dBFS) → `AudioTrack`
(low-latency performance mode), on a dedicated `THREAD_PRIORITY_URGENT_AUDIO` thread. Sample rate and
block size come from the device (`PROPERTY_OUTPUT_SAMPLE_RATE` / `FRAMES_PER_BUFFER`) and are validated
with `getMinBufferSize`. The output queue is kept to about two blocks; reads/writes block, so buffering
cannot grow without bound. The platform's automatic gain control and echo canceller are disabled on the
capture session (AGC would boost quiet rooms; AEC would cancel the passthrough). Nothing is written to
storage.

## Modes

| Mode | Route | Expected trade-offs (from Android/Bluetooth behaviour — not verified on the HD 4.50 BTNC) |
|---|---|---|
| Phone mic (default) | Built-in mic → media output (A2DP/wired) | Full-quality music continues and is mixed with ambient; hears near the phone, not at the ears; A2DP adds a delay the app can't measure |
| Headset mic (Android 12+) | `setCommunicationDevice(SCO)` + `MODE_IN_COMMUNICATION`; HFP mic → HFP output | Headset switches to its call link: mono, call-grade bandwidth; other media typically pauses or degrades until Stop; restoring the previous mode/device on Stop |

Phone-mic mode refuses to start without a headphone-type output (wired, USB, Bluetooth media, LE audio,
hearing aid) to prevent speaker feedback, and stops automatically if that output disappears.

## Safety controls

- Start is explicit; microphone permission is requested only then.
- Gain range −30 … +12 dB (digital, relative to the mic signal), default −6 dB, reset at every start.
- All gain changes ramp (30 ms); output fades in from silence; mute ramps to true silence.
- Peak limiter with instant attack, plus a hard ceiling at −2 dBFS.
- Visible foreground notification (type `microphone`) with Stop; capture stops if the service ends,
  permission is lost, the route disappears, or the hands-free link is taken over.
- On-screen warning: not hearing protection; don't rely on it for traffic awareness.
- Ambient is independent of NoiseGard; the app never claims to control ANC.

## Latency

The app shows "Delay inside the phone": capture block + frames still queued in the `AudioTrack`
(from `AudioTrack.getTimestamp`). The Bluetooth link adds more delay that the app cannot observe.
Total delay must be measured externally — see `docs/MANUAL_TEST_GUIDE.md` §2 (clap test).
