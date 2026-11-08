# Release notes 1.0.2

Two fixes on the Guard screen, no new features. Earlier releases stay untouched in their folders.

## The two fixes

- **Blocking now works on every network.** In 1.0.0 and 1.0.1 the tunnel was built only on
  metered connections, so on home Wi-Fi (unmetered) no tunnel was built at all and blocked apps
  opened normally — with no error, which made the app look broken. Blocking now follows your
  decision, not the platform's metered flag.
- **New switch in the “Current network” card: “Protect on every network” — on by default.** Turn
  it off to restore the old rule: block on metered connections only, allow on unmetered ones.
- **The protection switch answers every press** (it used to be silently disabled until an app was
  chosen), and the reason protection stayed off is now a tinted bar with an explicit sentence
  when the VPN consent is refused.

## Install

Installs straight over 1.0.0 or 1.0.1 without uninstalling and without losing data: the signing
certificate is the same (`9af970c1…`).

## Digests

```text
APK  SHA-256 : bc0b6a1a3c44892355adf53c16bba16bf3fdd6add53b72e0cd531209012ab479
signing cert : 9af970c1494188a2485e5a31dad8f5e020a86a742c574165df03e5fbfc25dd1d
versionCode 3 · versionName 1.0.2 · minSdk 26 · targetSdk 36
```

## Not verified

The tunnel has not been seen on a device after this change. Verify by opening a blocked app on
Wi-Fi: it must stop, and `tun0` must appear in `ip link`. The tests cover the decision only (330
unit tests, 0 failures), not platform behaviour.
