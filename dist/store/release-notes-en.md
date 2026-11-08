# Release notes 1.0.3

Published 2026‑11‑08. A **stable, final** release for the 1.0 line: wording, a redesigned dashboard,
and display fixes. No data or permission changes.

## New

- **The dashboard now answers “where did my data go?”** — a “Top apps today” card with three apps
  and relative bars, and a card that shows the daily budget or offers to set one. The cards
  scroll; the status line and the start/stop control stay pinned so neither can fall off screen.
- **A “Protected” chip** beside the network chip: the first screen tells you whether the wall is
  actually up, not merely whether the switch is on.
- **History states its own scale** — “Busiest day this week” under the chart, so a short bar is
  not mistaken for an empty one.

## Fixes

- **“مقنّن” became “شبكة محدودة”** everywhere: screens, notifications and the guide. The old word
  was a literal rendering of “metered” that nobody says.
- **App names keep their own direction:** “1DM+” was painted “+1DM” inside an Arabic paragraph.
- **The “blocked apps cannot reach the network” line** used the warning colour while blocking was
  exactly what the operator asked for — it is green now.
- **Fraction bars** were drawn in two colours (cyan and pink) and read as two data series, with a
  stray dot at the end. They are one clean bar now, on the dashboard and in the apps list.
- **The documented permission list** claimed to be final while the APK also carries two merged
  library permissions (`WAKE_LOCK` from WorkManager, an internal receiver permission from
  AndroidX). Both are documented now.

## Install

Installs straight over any earlier release without uninstalling and without losing data: the
signing certificate is the same.

```text
APK  SHA-256 : 282a00a2862e7b1a7e336c626ecd6d7e13742883c5e966a373c5fc86e92f7507
signing cert : 9af970c1494188a2485e5a31dad8f5e020a86a742c574165df03e5fbfc25dd1d
versionCode 4 · versionName 1.0.3 · minSdk 26 · targetSdk 36
```

## Not verified

No new screenshots were taken; `screenshots/` still shows 1.0.0, and both the Guard screen and the
dashboard have changed since.
