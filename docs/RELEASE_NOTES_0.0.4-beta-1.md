# w0y v0.0.4 beta-1

w0y now runs on the computer as a real desktop app: Windows (new) and Linux
(rebuilt), in the Hairline look, sized for a mouse and a keyboard. Android gets
a couple of small fixes.

## On the computer

- **Windows installer and portable zip.** Per-user install, no administrator
  rights. SmartScreen will warn about the unsigned installer; see
  `docs/WINDOWS_INSTALL.md`.
- **Linux AppImage and archive.** Wayland and X11.
- **Sound is built in.** libmpv is called straight from the app, so there is nothing
  to install on Windows. Speed, pitch without changing speed, reverb, equaliser and
  loudness levelling work like on the phone, and each track remembers its own version.
- **A real desktop layout.** A side bar that shrinks to icons in a narrow window, a
  wide player bar along the bottom, a big player with a ticked disc and a View tab,
  eight settings tabs on the left with wide rows on the right.
- **Mouse and keyboard.** Right click opens the track menu, `Ctrl`/`Shift` select
  several rows, double click plays, the queue is reordered only by its handle, focus
  rings everywhere, a hotkey sheet on `?`, media keys work with the window hidden.
- **Mini player** (`Ctrl+M`), tray icon, optional close-to-tray and start-with-system,
  interface scale 80–150%, Motion levels (Auto picks light animations on a weak PC).
- **Your session is no longer a plain-text cookie.** It is stored encrypted: Windows
  DPAPI, or on Linux a key in the system keyring.
- **Backup in one file** and **updates from GitHub** (Update / Later, off switch, six-hour
  limit, checksum check on Windows).
- **Copy debug info**, **Test connection** and a local crash record, all kept on the
  computer. Times are in your time zone.
- Faster while playing: the progress is sent to the interface four times a second
  instead of dozens, which took playback from about 23% to 8% of a CPU core here.

## Android

- The times in *Copy debug info* are now in your time zone and read like
  `17 Oct, 19:08` instead of an odd `01-04 …Z`.
- Build fixes for the new player animations (lint).

## Not in this beta

Visualizer on Windows, the yearly recap and Quick picks on the computer, "My files"
import, the Windows media panel (SMTC). See `docs/DESKTOP_PARITY.md`.
