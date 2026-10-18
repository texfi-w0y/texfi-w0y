# w0y with desktop — v0.0.4 beta-1

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
- **A new big player.** The cover, title, seek bar, big buttons and every action (like, pin,
  download, add to playlist, link, sleep timer, volume) are on the left; the lyrics are huge on
  the right, the line that is sung stays in focus and the rest fades out. The Queue tab is laid
  out as Now playing / Up next (drag by the handle, Clear) / Already played, with History beside it.
  The bottom bar is not needed there, so it is hidden in this layout.
- **Stand mode** grows a glow out of the cover, a big cover, a clock (can be switched off), three
  lines of lyrics and a thin progress line along the bottom edge.
- **Hide the side bar and the bottom player bar** when you want more room: the button at the bottom
  of the side bar, `Ctrl+B` and `Ctrl+Shift+B`, or Settings → Look. A small tab at the edge brings
  them back.
- **Search like on the phone.** Tabs All / Tracks / Videos / Albums / Artists, search as
  you type, suggestions over the results, a "You already have it" block from your own
  library, artists first, wide covers for videos, readable errors with Retry, and a
  keyboard-layout fix when the result is empty (`ghbdtn` finds "привет").
- **Speed dial like on the phone.** A two-row grid you page through; pin tracks, artists,
  albums and playlists (a pin button in the player, on the artist page and in the
  playlist header); right click or long press for a menu, hide with Undo.
- **My files.** Add your own mp3 / flac / m4a / ogg / wav by button or drag and drop,
  edit title, artist, album and cover, put them in playlists and likes. They are
  never sent to your YouTube account.
- **Playlist editor.** Cover from a picture, name, description, eight colours and ten
  glyphs, delete with a confirmation, the same as on the phone.
- **Player background** that grows from the cover itself and fades to black
  (Settings → View: glow, blurred cover, aurora, plain).
- **Quick picks** and the weekly **Discoveries** playlist on Home; the equaliser is on by default.
- Faster while playing: the progress is sent to the interface four times a second
  instead of dozens, which took playback from about 23% to 8% of a CPU core here.

## Android

- The times in *Copy debug info* are now in your time zone and read like
  `17 Oct, 19:08` instead of an odd `01-04 …Z`.
- Build fixes for the new player animations (lint).

## Files

- `w0y-windows-x64-setup.exe` and a portable zip — Windows 10/11 (unsigned installer).
- `w0y-linux-x86_64.AppImage` and `.tar.gz` — Linux, Wayland and X11.
- `TexFi-w0y-android.apk` — Android 8.0 and newer; this beta only fixes small things there.
- `SHA256SUMS.txt` — checksums.

## Not in this beta

Visualizer on Windows, the yearly recap on the computer, the Windows media panel
(SMTC). Windows builds are made and smoke-tested only by CI: they have not been run
on a real Windows machine by the author. See `docs/DESKTOP_PARITY.md`.
