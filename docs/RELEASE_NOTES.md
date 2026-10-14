# w0y v0.0.3 beta-2

Hairline grows up: a new dock, settings in tabs, a bento "Yours" screen,
playlists you can fully restyle, and a background you choose.

## Look and navigation

- **A new floating dock.** The mini player and the tabs live in one flat
  island, like the nav on the site: a thin line slides under the active tab.
  The track progress is a white line running all the way round the island.
  Settings → Hairline: island or full-width bar, colour (cover / accent /
  none), tab labels (all / active / hidden), glow.
- **The screen runs under the dock** instead of being cut off above it, and
  the last row still scrolls clear of it.
- **App background**, Settings → Look: grid, plain black, cover light,
  aurora, or your own picture (copied into the app and dimmed so text stays
  readable). The black band at the top of Settings is gone.
- **Settings in tabs**, each setting a card, with an underline that follows
  the active tab. Search is still there.
- **Seasonal events**: pumpkins for Halloween, snowflakes and trees for the
  New Year. Settings → Look: Auto (follows the date), Off, or pick one.
- A proper gear for Settings, and the notification shade now shows the w0y
  "W" instead of a generic play circle.

## Library

- **Yours as bento cards**: likes, history, downloads, my files and recap as
  tiles with counters that count up.
- **Playlists, fully yours:** cover from the gallery, colour, icon and a
  description. Deleting a playlist or folder takes two taps, so it cannot
  happen by accident.
- **Folders** for playlists.

## Player

- **The "⋯" menu** in the player: sleep timer (the old block at the bottom of
  the queue is gone), visualizer style and stand mode.
- **Stand mode** redesigned in Hairline: a clock you can hide and the controls
  moved below the camera.
- **Copy the YouTube Music link** of the current track.
- A flat Play/Pause button: no circle around it.

## Fixed

- Switching tabs from an artist or album page now takes you to that tab.
- The sleep timer no longer sits in two places.

## Upgrading

The playlist look needs a database change (version 9). It is migrated
automatically and your playlists, likes and history stay.

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

- Tracks from other playlists no longer show up in the playlist you open.
- A track that played to the end on some streams stopped instead of moving
  on to the next one.
- Video, lyrics taps near the end of a track and artist/album links on the
  first play work as intended.

## Files

- `TexFi-w0y-android.apk` — Android 8.0 (API 26) and newer.
- `w0y-linux-x86_64.tar.gz` — Linux desktop build; needs `mpv`. Unpack it and
  run `./install.sh`.
- `SHA256SUMS.txt` — checksums of the files above.

The APK is signed with the project key: it installs over the previous beta
and your data stays.

## Honest about the limits

- If recommendations already got into a playlist on YouTube itself (for
  example after "upload again"), remove them there by hand: the app cannot
  tell them from tracks you added.
- MP3 at 320 kbps does not make the sound better than the source: YouTube's
  audio is compressed more than that.
- Account sync works by reading YouTube's pages the way the web app does,
  and YouTube can change them without notice.
- The Linux version's interface is in Russian only and does not have
  Hairline, your own files or the new lyrics sources.
- Genius and NetEase lyrics are read from their public pages and can stop
  working when those sites change; they may be for a different version of
  the song.
- Video plays over YouTube's SABR protocol. Known issue in this beta: on some
  tracks the picture stays black while the sound plays normally. It is being
  worked on and is not fixed here.

w0y is not affiliated with YouTube or Google.
