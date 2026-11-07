# rethink — v0.0.4 beta-4

A round of fixes and rework for Hairline New after the first feedback on beta-3.
The computer version is the same as in beta-2.

## Fixes

- **Visualizer** kept still after switching to the next track until the player was
  reopened. It now keeps moving across track changes.
- **Removing a track from a playlist** that is linked to the account no longer brings
  it back after the next sync.
- **Album tracks** played from an album page now keep the artist name (it was empty
  in Recent and in the player). The album page shows the artist too.
- **Lyrics page:** taps on empty space no longer go through to the screen under the
  player.
- **Home:** rows with long titles no longer make everything below jump while you swipe
  through them.
- **Sleep timer** now runs inside the playback service, so it stops the music even when
  the app is in the background or closed. The last 8 seconds fade out; "End of track"
  pauses right at the start of the next one.
- **Reverb** works on every phone. The system effect it relied on is missing on some
  devices (Pixel among them), so the dial did nothing; w0y now makes the echo itself.
- **Your version of a track** (slowed, pitch, reverb) no longer spills into the first
  seconds of the next track.
- **Your own background picture** stays when you switch between styles. Switching to
  Hairline New used to replace it with the dots, and it had to be picked again.
- **Sound panel:** the Slowed / Normal / Sped up switch no longer slides off the screen
  when none of them matches. Pitch goes from 0.70 to 1.30 instead of 0.90 to 1.10.

## After the update

- **What's new, then your call.** After updating, w0y shows what changed; "Continue" opens
  a list of this version's new things that you can switch off — the intro, the new
  playlist page, the artist in Speed dial, My playlists on Home, the magnet on panels and
  the fade before sleep. They are ordinary settings, so each can be changed later too.
  The window now reads these notes as text, without Markdown symbols.

## Hairline New

- **Player.** The second "Queue" pill at the top is gone — the top is just collapse and
  the menu; the queue stays at the bottom. Crop marks around the poster
  are gone. The title slides in when the track changes, and the player steps back
  slightly while a panel is open. Repeat one is shown as the repeat icon with a "1"
  inside, without a caption.
- **Panels** (queue, sound, sleep timer, track menu, settings choices) rise on a spring
  and unfold, their content follows a moment later. Pulling a panel down feels like a
  magnet: it holds until you pull far enough, then lets go with a tick; let go early and
  it snaps back.
- **Track menu** (long press) is now a panel: the track with its cover, three tiles —
  Play next, Add to queue, Download — then link, artists, album and playlists. Row menus
  in Downloads and playlists use the same panel.
- **Lyrics:** the translation picker is a quiet row of labels instead of boxed buttons.
- **Home:** a new "My playlists" row; Recent and Likes are shelves like the rest of Home
  instead of a long list at the bottom.
  Speed dial shows the artist under a track or album instead of the word "Track".
- **Playlists** open on a new page: the cover large on its own colours, flowing like the
  poster in the player, then the name, owner, description and an island with Shuffle,
  a big Play and Download all. Hold a playlist on Home or in Yours and drag it to move it
  forward or back; the order is shared by both and stays on this phone.
- **Back** closes one layer at a time: from lyrics or video it returns to the cover, from
  the player over an artist it closes only the player, and the artist stays.
- **Yours:** "+" opens a Create panel — a playlist or a folder, and for a playlist where it
  goes: to the YouTube Music account or only into w0y. The avatar opens an Account panel:
  sync now, mirror playlists, where new playlists go by default, albums from the account,
  sign out. Downloads, History and My files scroll as one page, so the list gets the
  whole screen.
- **Settings:** search is a circle in the top right corner; tap it and it stretches into the
  search bar, then the keyboard comes up. Rows have
  more room and the chosen value sits under the description instead of being squeezed on
  the right.
- **Start:** a new intro after the app icon — the "W" is drawn by pen, the cat is put
  together stroke by stroke and blinks, "w0y" is typed under it, then the sign flies into
  the bottom island.

Reports and screenshots are welcome in [issues](https://github.com/texfi-w0y/texfi-w0y/issues).

## Install

**Android 8.0 or newer.** Download `TexFi-w0y-android.apk` below, or update from the app
(Settings → App). Checksums are in `SHA256SUMS.txt`.
