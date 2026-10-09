# Changelog

## v0.0.1 beta-1

First public beta.

- Android app (8.0+): search, playback with queue and sleep timer, account
  sign-in with two-way sync of playlists and likes, downloads (with saving to
  `Music/w0y music`), synchronised lyrics with translation, your own
  speed/pitch/reverb per track, clean mode, speed dial, stats, widget, themes
  and colour schemes, four languages.
- Linux desktop version (Compose Desktop, sound through `mpv`).
- Known limits are listed in the [release notes](docs/RELEASE_NOTES.md).

# w0y v0.0.3 beta-1

The Linux version catches up with the phone.

## Linux desktop

- **Two looks, switchable in Settings → Look:** Pixel and Smooth with Easy
  Glass (or matte). Same icons as on the phone, calm screen transitions.
- **Settings as on the phone:** sound, tone, player, equaliser, visualizer,
  stand mode, downloads (parallel, speed limit, retries, space limit, MP3 via
  ffmpeg, folder, auto-download playlists), search, account, clean mode, look,
  devices, data, full reset.
- **Keyboard:** every action has a shortcut and can be rebound in
  Settings → Keys; `?` shows the list; the Russian layout works too.
  `w0y --do play_pause` (and `w0y --do list`) controls a running app, so any
  action can go on a desktop hotkey.
- **New:** speed dial, stats, shuffle and repeat (also over MPRIS), visualizer
  (needs `pactl` and `parec`), stand mode, notifications on track change and
  when downloads finish.
- **Four languages**, from the same files as the phone.

## Fixed

- A track that played to the end on some streams stopped instead of moving
  on to the next one.
