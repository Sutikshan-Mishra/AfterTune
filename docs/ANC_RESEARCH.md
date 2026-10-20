# NoiseGard (ANC) control — research notes

Target: **Sennheiser HD 4.50 BTNC** only. Findings for the HD 450BT, HD 4.40 BT, HD 4.50 SE or other
models were not used.

Date: 2026-10-12. Hardware testing: **none** (no headset was available to the developer).

## Evidence

| Source | Finding |
|---|---|
| Sennheiser HD 4.50 BTNC / HD 4.50R user manual (official, DE edition, assets.sennheiser.com file 7902) | NoiseGard has no dedicated switch. Over Bluetooth: press and hold **Volume +** and **Volume −** together; the LED flashes violet 3×. With the audio cable: press On/Off once. Profiles listed: **HFP, HSP, A2DP, AVRCP**; codec: aptX. No companion app is mentioned. Battery level "can appear on the smartphone display". |
| Sennheiser Smart Control app page / FAQ (uk.sennheiser-hearing.com/pages/smartcontrol) | The app explicitly **does not support** headphones launched before 2018, naming the HD 4.50 BTNC. |
| Web search for open-source protocol work on this exact model | No credible reverse-engineering project, GATT profile or command set found. |

Note: a third-party quick-guide page (manualsnet) describes a "NoiseGard button". This contradicts the
official manual and was disregarded.

## Analysis

- **Bluetooth Classic**: A2DP carries audio; AVRCP carries transport/absolute-volume commands; HFP/HSP
  carry calls and HFP battery indicators. None of these standard profiles defines an ANC command, and
  Android exposes no API to send vendor AT commands over another app's HFP link.
- **BLE GATT**: the manual lists no BLE features, and no GATT service for this model is documented.
  Inventing a UUID or command would violate the project rules and could misbehave.
- Simulating the button chord (Vol+ and Vol− together) is not possible from the phone: AVRCP volume
  changes are sent as absolute volume, not as presses of the headset's physical keys.

## Decision

App-controlled ANC is **not implemented**. The Headphones screen shows a clearly marked
"App control unavailable" state, the physical shortcut, and "Current state: not readable by the phone".
No simulated toggle or invented state is ever displayed.

## What would change this

A verified capture (for example an Android HCI snoop log while the official app — if any existed —
toggled ANC) showing a documented command on a profile Android apps can open (RFCOMM/SPP or GATT).
None is known for this model.

## Battery

The headset reports battery over HFP. Android records it and exposes it via the framework's
`BluetoothDevice#getBatteryLevel()` and the `BATTERY_LEVEL_CHANGED` broadcast (not public SDK API).
The app reads these defensively; `-1`/missing/any failure is shown as **"Not reported"**. Values are never
estimated.

## Phase 3 note (AfterTune)

Ambient listening (microphone passthrough) is a separate feature and does **not** control NoiseGard.
Direct HD 4.50 BTNC NoiseGard control remains unverified and unimplemented; the physical shortcut
(hold Volume + and Volume − together) is still the only supported way to switch it.
