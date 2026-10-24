# Bug fixed — v0.0.4 beta-2

A clean-up release for Android: the rough spots from beta-1 are fixed, the Hairline
look is calmer (no glow anywhere), and a few screens that felt unfinished are redone.
The computer version is the same as in beta-1.

## Fixed

- **Stray `&` and `,` in track subtitles.** With several artists, the separator ended
  up where the album should be ("Aarne & BUSHIDO ZHO · &"). Old entries in History and
  Likes no longer show it either.
- **Home feeds in English.** One failed request used to switch every YouTube feed to
  English until the app was restarted ("Listen again" in a Russian interface). The app
  language now stays.
- **Dates in Stats** follow the app language instead of the phone's.
- **Search** shows one arrow before "Recent", not two.
- **Playlist tiles** get a short subtitle (Own / On YouTube / Read-only) instead of a cut-off one.
- **The Stats tile** on Yours counts plays together with History, so it no longer lags by one.
- **Queue and Similar:** the panel outline no longer breaks off at the bottom, the list fades
  out at the edge, and the tab underline stretches as it moves.
- **The cover flies into the mini player** also when the visualizer is on.
- **Poster layout:** the cover fades into a soft blur instead of a hard edge.
- **Player title** fades out at both edges while it scrolls instead of being cut.
- **Visualizers:** Terrain no longer draws a dark box over the cover, Lines keeps margins.
- **Settings:** descriptions take two lines and can be read in full, the active tab is
  centred and the tab strip fades at its edges, Stand rows no longer repeat "Stand:".
- **Home:** sections are numbered in order, a "For you" section holds your own picks, and
  cards no longer repeat the row title under every track; the two pumpkins in the header are gone.

## New

- **Only the new launch screen.** The old system splash with the pixel feather is gone.
- **Seasonal effects:** small, slow snow in winter and a quiet Halloween (embers, a bat,
  a pair of eyes) behind the screens. Off / Light / Full in Settings → View. Light draws
  fewer particles and every other frame; the effect pauses while the player is open.
- **Three visualizers:** Pulse, Horizon and Dots — lines and dots only.
- **Lyrics without the player:** hide the player and read the whole text; a thin bar keeps
  progress, time and play/pause.
- **Welcome after signing in:** a ring draws around your avatar, then your playlists, likes
  and albums count up as they arrive. "Continue" works right away.
- **Downloads:** tracks, megabytes and queue as three numbers, the folder as one row,
  "Where downloads live" opens on tap, groups for failed and downloaded.
- **My files:** count and minutes on top, a progress line while adding, an empty state you
  tap to add files. The file editor has a large cover, underlined fields and Delete kept apart.
- **History** is grouped by day — Today, Yesterday, then the date — with the time each track played.

## Changed

- **Bottom bar:** no glow and no breathing; the track progress is a thin accent line along
  the top edge instead of a white outline. The "Bottom bar glow" setting is removed.
- **Cover glow in the player** is now off by default (Settings → View).

## Files

- `TexFi-w0y-android.apk` — Android 8.0 and newer.
- `w0y-windows-x64-setup.exe`, a portable zip, `w0y-linux-x86_64.AppImage` and `.tar.gz` —
  the computer version, unchanged since beta-1.
- `SHA256SUMS.txt` — checksums.
