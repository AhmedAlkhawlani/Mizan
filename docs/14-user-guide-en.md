# 14 — User guide (English)

> This guide describes what Mizan 1.0.0 actually shows, how to read every number, and when not to
> trust one. The Arabic original is [14-user-guide.md](14-user-guide.md); the privacy policy is
> [15-privacy.md](15-privacy.md).

---

## 1) What Mizan is

An Android data counter that runs entirely on the device:

- **Measures** the live rate and today's usage from the system counters.
- **Records** usage by hour and by day, and counts the monthly cycle from the start day you choose.
- **Breaks down** usage per app for today, 7 days, or the billing cycle.
- **Alerts** you at 80% of a budget and when you pass it, with daily and monthly budgets for the
  device and a budget of its own for any app.
- **Blocks** the apps you select from reaching the network through a local `VpnService` tunnel —
  no root, and it can be stopped instantly.
- **Shows** two values on your home screen: a speed widget and a budget widget, plus shortcuts and
  a Quick Settings tile.

No account, no subscription, and no `INTERNET` permission at all — the app **cannot** reach the
network even if it wanted to. Technical detail is in [15-privacy.md](15-privacy.md).

---

## 2) Requirements and installation

| Item | Value |
|---|---|
| Android | 8.0 (API 26) or newer |
| Target | Android 16 (API 36) |
| Download | GitHub Releases or F-Droid |
| Manual install | A signed APK, with "install from unknown sources" allowed for that source |

Always update from the same source you installed from: the GitHub build, the F-Droid build and a
debug build are signed with different keys, so the system will not install one over another.
Switching sources means uninstalling, and the local history goes with it.

---

## 3) First launch

One setup screen appears before the home screen, with three optional steps:

1. **Usage access** — opens the system settings; only the Apps list needs it. Measuring and
   budgets work without it, and the Apps tab keeps asking until it is granted.
2. **Notifications** — on Android 13+ the permission is requested; on older versions the app's
   notification settings open instead. The persistent notification carries the speed and a pause
   action. Refusing it does not stop measurement.
3. **Start measuring** — starts the foreground monitoring service.

A line reading "Guard — optional" reminds you the firewall exists, but its consent is asked for on
the Guard tab itself, not here.

"Start using" and "Skip for now" do the same thing: they close the screen and record that it was
seen, so it does not return unless you clear the app's data. Every step stays available inside the
app afterwards.

---

## 4) The screens

### Home

- **Speed card**: download and upload right now, from the monitoring service.
- **Today's usage**: the total, compared against the daily budget if you set one, with a bar that
  glows as you approach the limit.
- **Mobile / Wi‑Fi**: how today splits by network type.
- **Where the number comes from**: if the day is partial (the app started after midnight), the
  figure is labelled "Partial day" instead of being presented as complete.
- The **gear** at the top opens "Budget and cycle".

### Apps

The list of consuming apps, with a period switcher: Today, 7 days, Cycle. Tapping an app opens its
details: 7 days as a chart, the mobile/Wi‑Fi split, and an editor for its own budget.

If usage access was never granted you get an explanation card and a button straight to the
settings. Numbers are not guessed while the permission is missing — the list stays empty and says
why.

### History

- A **7 days** chart, and a **24 hours** chart for today.
- A **billing cycle** card: choose the start day (1–28) and the monthly budget follows it.
- **Export data** hands a CSV to the system picker: day, hour, direction and bytes. The file goes
  wherever you choose, and the app holds no storage permission to write anywhere else.

### Guard (network guard)

1. **VPN consent** — the system asks once; without it no tunnel can be created. Consent can be
   withdrawn in the system settings at any time, and the app shows the first-step screen again when
   you return.
2. **Apps** — choose which apps may not reach the internet. Blocking works per UID, so two apps
   that share an identifier are blocked together (that is Android's behaviour, not the app's).
3. **Allow on unmetered Wi‑Fi** — when this is on, blocked apps are allowed through on an
   unmetered network and stay blocked on mobile data and on metered networks.
4. **Blocked attempts** — how many packets and bytes the firewall stopped, split per app.
5. **Stop now** — from the switch, or from an action in the Guard notification. Stopping closes
   the tunnel for real and restores normal networking.

Guard is entirely optional; measuring and history do not depend on it.

---

## 5) Budgets and alerts

From the gear on Home:

- **Daily budget** and **Monthly budget** for the device, in any size (MB/GB); leave one empty to
  disable it.
- **Cycle start day** — the day your carrier restarts the month; the monthly budget and the
  history follow it.
- **Per-app budget** from the app's detail screen.

Alerts:

- At **80%** — "approaching your budget…".
- At **the limit** — "you passed your budget…".
- They stay until you tap "OK", so an important warning cannot be lost in a crowd of notifications.

Each alert fires once per level and once per period; it does not repeat with every sample.[^1]

### Status bar icon

At the bottom of "Budget and cycle" there is a card called **Status bar icon**:

- **Download speed** (the default): a small figure with its unit next to the clock, updated every
  second while monitoring runs.
- **Upload speed**: the same, for upload.
- **No figure (static icon)**: the balance glyph stays.
- **Show speed in bytes**: when off, the figure stays still until the rate passes a kilobyte, so
  the bar never pulses over small background traffic.
- **Counter shape**: unit raised beside the figure, unit under the figure, or figure and unit the
  same size — three ways to fit the same reading into a 24dp slot.
- **Bold glyphs**: when off the strokes turn thin and quieter, but read less clearly next to the
  other icons in the bar.

The figure is drawn inside the notification's status-bar icon, so some launchers render it in one
colour or shrink it; in that case the notification itself (full speed and today's usage) remains
the accurate reference.

[^1]: Details and limits in `docs/12-progress.md` §5/23.

---

## 6) Widgets, shortcuts and the tile

| Element | What it does |
|---|---|
| **Speed widget (2×1)** | The live rate, with options for what is shown next to it in the widget's settings screen |
| **Budget widget (2×2)** | Today's ring against the budget, with options for the data beside it |
| **Quick Settings tile** | Shows the download rate while the panel is open, and "stopped" when monitoring is off; tapping opens the app |
| **Apps shortcut** | Opens the Apps tab directly |
| **History shortcut** | Opens History directly |
| **Budget shortcut** | Opens the budget editor directly |
| **Stop monitoring shortcut** | Stops measuring at once and keeps it stopped until you start it again in the app |

Both widgets are added from the home-screen launcher, and their settings screens appear when you
add them; cancelling adds nothing.

---

## 7) Permissions and why

| Permission | Why | Without it |
|---|---|---|
| `PACKAGE_USAGE_STATS` | Reading each app's counters from `NetworkStatsManager` | Overall measuring and budgets still work; the Apps tab asks for it |
| `POST_NOTIFICATIONS` | The persistent notification and budget alerts | Measuring continues with no visible notification |
| `FOREGROUND_SERVICE(_SPECIAL_USE)` | Keeping monitoring alive while you use the phone | No continuous measurement |
| `RECEIVE_BOOT_COMPLETED` | Resuming monitoring after a reboot if it was running | Measuring resumes on the next open |
| `QUERY_ALL_PACKAGES` | Names and icons for the apps in the lists | An incomplete or nameless list |
| `ACCESS_NETWORK_STATE` | Knowing the current network type (mobile / Wi‑Fi / unmetered) | No split, and no unmetered exception |
| `BIND_VPN_SERVICE` | The firewall, through `VpnService` | No Guard |
| **`INTERNET`** | **never declared** | — |

No location, no contacts, no messages, no phone state, no device identifiers.

---

## 8) Troubleshooting

**The numbers on Home do not move.**
Check that measuring is running, from the "start measuring" card on Home or from the shortcut.
After a reboot it resumes on its own if it was running before; if you stopped it by hand it will
not come back by itself.

**The Apps tab says access is required.**
Open the link, choose Mizan, and allow "Use usage access". On some devices the system renews the
session a few seconds later; return to the app and it activates.

**The figures differ from my carrier.**
Mizan counts what the device moved as the system reports it; the carrier counts from its side.
Differences from rounding, compression and changing signal are normal. Detail in
`docs/12-progress.md` §9.1.

**Guard is not blocking an app.**
- Check that the switch is on and that the "Network guard" notification is present.
- If you are on unmetered Wi‑Fi and the exception is enabled, that app is allowed **on purpose**.
- Some system components connect through shared services that do not pass through the same tunnel;
  the firewall works at the connection layer and cannot hide your address or encrypt traffic whose
  origin it does not know.
- Two apps sharing a UID are blocked together; that is a platform limit.

**A widget is stuck on "—".**
The widget reads from the monitoring service. If the service is stopped it shows "—" rather than a
guessed number. Start monitoring.

**The setup screen came back after an update.**
This happens once for installs that predate the screen, then never again. Clearing the app's data
brings it back — and erases the local history with it.

**I lost my data after an update or after switching source.**
The data is local with no cloud copy, by design. Export a CSV now and then if you want an archive.

---

## 9) Known limits (plainly)

- **No `INTERNET`** also means no server time, no sync between devices, and no cloud backup.
- **Measuring runs while the service runs**; days when the app was off show as incomplete and the
  figure is labelled "Partial day" instead of being presented as full.
- **The status bar icon** may be drawn in one colour or at a small size on some launchers; the full
  number stays in the notification and in the app.
- **Blocking is a local VPN tunnel**: it cannot stop DNS that leaked before the tunnel started, and
  it does not protect you from a network that bypasses the app itself.
- **The per-app breakdown** relies on what the system reports and can lag by minutes on some devices.
- **Anomaly detection** exists in the background layer only and does not alert you yet.
- **The English interface exists**, but the broad visual verification was done on Arabic RTL first;
  the theme is always dark and does not follow the system theme (a deliberate decision,
  `docs/12-progress.md` §7/34).
