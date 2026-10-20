# Manual test guide (real phone + HD 4.50 BTNC)

Record for every run: **phone model, Android version, app version, mode, result, notes.**
Mark each item PASS / FAIL / N/A. Nothing below has been run yet.

## 0. Install and launch
1. Install the debug APK from GitHub Actions (see README).
2. Cold start: the launch screen shows "AfterTune" and, at the bottom, "Developed by Sutikshan Mishra"
   above the navigation bar; it moves on within about a second.
3. Sound tab header shows "AfterTune" with the subtitle "HD 4.50 BTNC Companion" and the connection pill beside it;
   nothing clipped or overlapping. Check on the smallest screen/largest font size available. Attribution to
   Sutikshan Mishra stays on the launch screen and in Settings → About.
4. Check dark and light theme (Settings → Appearance).

## 1. Bluetooth routing and permissions
1. Headphones tab → Allow access → grant. Status shows Connected with A2DP/Hands-free rows.
2. Ambient tab → choose Phone mic → Start. Expect a microphone permission prompt (and notification prompt on
   Android 13+). Deny once: status shows the error, nothing runs. Deny twice: "Open app settings" appears.
3. Grant microphone → Start. Notification "Ambient listening on" with Stop must appear.
4. With no headphones connected, Phone mic Start must be disabled with the feedback explanation.

## 2. Ambient passthrough
1. Phone mic, gain −6 dB: speak near the phone; you hear it in the headphones. Note "Delay inside the phone".
2. Move the gain slider −30 → +12 → reset: level changes smoothly, no clicks; meters move.
3. Mute / Unmute: silence ramps in/out without clicks.
4. Start music in Spotify, then start ambient: does music continue? does quality change? (record)
5. **Clap test (total delay):** record with a second device while clapping next to the phone with one ear cup
   held against the recorder; measure the gap between the two clicks in Audacity. Repeat 3×, note median.
6. Turn off the headphones / disconnect Bluetooth while running: ambient must stop with a message and must not
   play through the phone speaker.
7. Background the app and lock the screen: ambient continues with the notification; Stop in the notification ends it.
8. Start/stop 10 times quickly: no crash, no stuck notification.
9. Headset mic (Android 12+): Start. Record: does the headset switch to call audio? does music pause? delay?
   Stop: does music return to normal A2DP quality?
10. Revoke microphone permission in system settings while running: capture must stop (Android kills the app).

## 3. System-wide EQ (Spotify)
1. Sound tab → "Apply to other apps" on. A "System-wide EQ on" notification appears.
2. Open Spotify, start a song (or skip to the next track if already playing).
3. Back in AfterTune: is a session listed for com.spotify.music? Record status (Attached / No control / Failed).
4. With AfterTune in the background, set the 62 Hz fader to +12 then 0 (or switch Bass ↔ Neutral). Audible in Spotify?
5. Toggle EQ off/on: audible bypass in Spotify?
6. Confirm the in-app player is unaffected by this toggle (it is always equalized in-app).
7. If Spotify isn't listed: look in Spotify's settings for an "Equalizer" option (menu location varies by version);
   if it offers the phone's equalizer, choose AfterTune → does AfterTune open with
   "Opened as the equalizer for …"? Then repeat step 4.
8. Optional: "Global output mix (legacy)" on → repeat step 4 with an app that was not listed. Record the result.
9. Note any phone sound effects (Dolby, Samsung Sound, etc.) and whether they were on.

## 4. Regression
In-app player (pink noise + a file), presets, saving/renaming/deleting profiles, auto headroom, NoiseGard
card text (hold Volume + and Volume −), troubleshooting, restore defaults.
