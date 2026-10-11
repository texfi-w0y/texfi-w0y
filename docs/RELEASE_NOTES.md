# w0y v0.0.3 beta-1

A new look — **Hairline** — your own audio files, better lyrics, and the
Linux version catches up with the phone.

## New look: Hairline

- Thin lines instead of pixels and glass: near-black bluish background with a
  faint grid, sharp corners, Unbounded for titles and a mono for labels. Sand
  stays the default accent. Pixel and Smooth are still in Settings → Look,
  and Hairline has its own settings section (line weight, corners, cover
  shape, caps, density, motion).
- A floating bottom dock whose outline is the track progress; the active tab
  gets a small label that slides between tabs.
- The player: a progress ring around the cover, a thin Play button, seek that
  works while paused, long-press on the title or artist copies it, swipe down
  closes it with a spring, links to the artist and album (also on the first
  play).
- Lyrics as a full page: the cover shrinks to a small header, big bold lines,
  the current line eases up. A button toggles it; the LYRICS tab is gone.
- New visualizer styles in Hairline: Lines, Orbit, Terrain.
- A loading animation, a pull-to-refresh ring on Home, a new intro, five app
  icons to choose from (Settings → Look).
- Home: speed dial in three columns, two rows.

## New

- **My files.** Add mp3, wav and other audio from the phone (Library → My
  files). Title, artist, album and cover are read from the tags and can be
  edited in the app. They play from the disk, go into playlists, speed dial
  and history, and are never sent to YouTube. After a file, recommendations
  are built from the same track found in the catalogue, and a broken file is
  replaced by that track.
- **Lyrics everywhere.** After LRCLIB the app tries NetEase (timed), lyrics.ovh
  and Genius by artist and title (Settings → Player, can be turned off).
- **Lyrics delay** from −0.5 to +2 s for lines that run ahead of the voice.
- **Video** for tracks that are YouTube videos: cover or video in the player.
- Sharper artwork in the notification shade.

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
- Video plays over YouTube's SABR protocol; if a video does not open, the
  player says so and the sound keeps playing.

w0y is not affiliated with YouTube or Google.
