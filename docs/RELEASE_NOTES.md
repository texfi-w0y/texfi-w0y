# w0y v0.0.3 beta-3

A new player, a contour-map background, updates straight from GitHub, a
Quick picks shelf on Home and a recap of your year. Plus a long polish pass:
search errors you can read, safer deleting, recommendations that stop repeating.

## Player

- **Two new layouts, Framed cover (the default) and Poster.** A blurred cover
  fills the screen. Framed cover puts the cover in a thin frame with the track
  progress running round it. Poster stretches the cover from the very top and
  melts it into the background. Controls are outlined so it is clear what you
  can tap, and everything fits on one screen without scrolling.
- **The cover flies into the mini player** when you collapse the player, and
  back when you open it. It follows your finger when you drag down.
- **Swipe down collapses the player** in every layout.
- **Tap the artist to open the artist, tap the title to open the album.**
  Featured artists are separate links.
- **Layout can be switched right in the player:** "⋯" → Look.
- **Lyrics slide in from the top** over a blurred background; swipe up to
  close. The edges of the text fade instead of being cut.
- **Tabs in the panels** (Sound / Speed / Look, Queue / Similar) slide
  instead of jumping, and the active underline glides.
- **Closing a panel or the player is smooth:** the sheet follows your finger
  and lands with the speed you threw it.
- A video button is shown only where there is a real video; a song from an
  album on YouTube Music has none.
- In Poster the visualizer is bigger and stands in the free part of the cover,
  never on top of the buttons.

## Home

- **Quick picks** right under the speed dial: your own picks built from what
  you played recently, in columns of four rows you scroll sideways, with
  "Play all". Shelves of tracks from YouTube Music use the same list look.
- A track started from Home continues with similar ones even if you have
  started it before: when everything fresh was already shown, the queue is
  filled with earlier picks instead of staying empty, and the request is
  retried once if the network hiccups.

## Statistics

- **Your year:** a story-style review of the calendar year (minutes, artist of
  the year, top tracks, your rhythm: peak hour, longest streak, busiest month,
  biggest day) and a 9:16 card to share. It is drawn on the phone; nothing is
  uploaded.
- Stats update by themselves, count a play after 30 seconds and are restored
  from your history.

## Updates and "What's new"

- **Updates from GitHub, like Obtainium.** On launch (at most every 6 hours)
  w0y asks GitHub whether a newer release is out. If so, you get
  **Update / Later**. Update downloads the APK, checks its checksum and hands
  it to the system installer. Later leaves it in Settings → App, where you can
  check, update, switch the check off and read the notes.
- **What's new** is shown once after an update, from the release description.
- Only the release page of this repository is contacted: no account, nothing
  about you or your phone. Android checks the signature too, so a file signed
  with a different key will not install. Android asks once to allow w0y to
  install apps.

## Look

- **Contour-map background** is the new default (Settings → Look → Background).
  If you were on the old default (grid) you get the new one; a choice you made
  yourself is kept. On the light theme the grid stays.
- Toggle thumbs are centred in their pills.
- On Downloads, My files, History, Likes and Settings the list ends above the
  island and the background carries on behind it; the dimming behind a
  settings choice panel now covers the island too. On Home, Search and Library
  the screen still runs under the island.
- A Hairline widget in three sizes, in light and dark.
- Settings are back to eight tabs with search behind a magnifier.

## Search and safety

- **Search errors are visible:** a typed reason ("No connection", "Server took
  too long", "Unexpected response") instead of an empty list, with Retry and
  **Copy debug info**, plus **Test connection** in Settings → App. Nothing is
  sent anywhere; you copy the text yourself.
- **Delete protection:** long-press, confirm, then UNDO; reordering only by
  the handle. Your own files stay in playlists after a sync.
- Recommendations no longer repeat and show one shared reason. Crashes are
  recorded locally and appear in Copy debug info.

## Fixed

- Dragging the player sometimes left a black screen; it now always settles.
- Reordering tracks in a playlist works again.
- The widget no longer takes the app down if it fails to draw, and its
  rounding follows the system's.
- A corrupted search-history file no longer breaks search.

## Upgrading

The database moves to version 11 and settings to schema 3. Both migrate
automatically; playlists, likes and history stay.

## Files

- `TexFi-w0y-android.apk` — Android 8.0 (API 26) and newer.
- `w0y-linux-x86_64.tar.gz` — Linux desktop build; needs `mpv`. Unpack it and
  run `./install.sh`.
- `SHA256SUMS.txt` — checksums of the files above.

The APK is signed with the project key: it installs over the previous beta
and your data stays.

## Honest about the limits

- The flying cover works in Framed cover and Poster while the cover is on
  stage; with the visualizer, lyrics, video or another layout the player
  slides down as before.
- Account sync works by reading YouTube's pages the way the web app does, and
  YouTube can change them without notice.
- The Linux version does not have any of the above: its interface is in
  Russian only and without Hairline.
- Video plays over YouTube's SABR protocol; on some tracks the picture can
  stay black while the sound plays normally.

w0y is not affiliated with YouTube or Google.
