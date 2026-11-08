# Release notes 1.0.1

One bug fix, no new features. Version 1.0.0 stays untouched in `Mizan Final/`.

## Fix

- **The protection switch now answers every press.** In 1.0.0 the switch was silently disabled
  until at least one app had been chosen: no dialog, no message, no change of state — so the app
  looked broken on any fresh install. Every press now has a visible consequence: it opens the app
  picker when nothing is selected, turns protection on once an app is chosen, and asks for the
  VPN consent when it is missing.
- **The reason protection stayed off is now readable:** a tinted bar with an icon instead of a
  faint grey line, and an explicit sentence when the VPN consent is refused.

## Install

Installs straight over 1.0.0 without uninstalling and without losing data: the signing
certificate is the same (`9af970c1…`).

## Digests

```text
APK  SHA-256 : 1eb4e7dc5ecc9f040c424c34bb177b439005926343081bac35a22ee83aca9fd9
signing cert : 9af970c1494188a2485e5a31dad8f5e020a86a742c574165df03e5fbfc25dd1d
versionCode 2 · versionName 1.0.1 · minSdk 26 · targetSdk 36
```

## Not verified

The Guard screen has not been seen on a real device after the fix. It passes the build and test
gate (328 unit tests, 0 failures) and the regression is locked by five tests, but the touch
behaviour and the consent dialog still need a phone.
