# Release notes 1.0.0

The first stable release of Mizan, running on Android 8.0 (API 26) and newer.

## What is new

- **Resilient accounting:** a traffic accountant that survives reboots and keeps today's total in a local database.
- **Live speed:** on the dashboard, in a silent ongoing notification, a status-bar badge in three templates, and a Quick Settings tile.
- **History:** the last 7 days and today's hours, with partial days labelled and CSV export through the system picker.
- **Per-app usage:** today / 7 days / billing cycle, with a daily detail chart and a mobile/Wi-Fi split.
- **Budgets:** daily and monthly for the device and for each app, **in MB or GB**, with 80% and 100% alerts once per level that stay until acknowledged.
- **Optional protection:** block chosen apps from the network through a local tunnel with no root, automatic allowance on unmetered networks, and a live status screen.
- **Widgets and shortcuts:** a 2×1 speed widget and a 2×2 budget widget, each with its own settings, plus four shortcuts.
- **Arabic and English:** Arabic RTL by default, with “English” or “System” selectable from the About screen.
- **In-app guide:** a “User guide” screen explaining every screen, every figure, and the common questions.
- **Privacy:** no Internet permission, no server, no analytics, and cloud backup disabled for the app's data.

## Fixes

- The website link in the Maknoon team card now points to `ahmedalkhawlani.github.io/maknoonApp`.
- The English interface now lays out left-to-right even on a phone set to Arabic.
- The hour bucket is written in a form old SQLite parses on Android 10.
- Measurement continues when a sample cannot be stored instead of dropping the whole difference.

## Note

Traffic from devices connected to the phone's hotspot is not shown in this release; it is the first item in the 2.0 pack.
