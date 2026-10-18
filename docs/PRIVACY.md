# Privacy: what leaves the app

w0y has **no telemetry, no analytics and no crash reporting**. There is no server of this project.
Everything below is the complete list of network requests.

| Where | Why | Can you turn it off? |
|---|---|---|
| `music.youtube.com`, `*.googlevideo.com`, `i.ytimg.com`, `*.googleusercontent.com` | search, your library, stream links, the audio itself, covers. If you sign in, the session cookie is sent there like a web browser would | no (it is the music source) |
| `accounts.google.com` | only the sign-in page, shown in a separate browser window | only when you sign in |
| `lrclib.net` | lyrics (artist, title, duration of the track) | Settings → Player → Lyrics |
| `api.lyrics.ovh` | lyrics fallback (artist, title) when LRCLIB has nothing | Settings → Player → «Search lyrics everywhere» (on by default, can be turned off) |
| `translate.googleapis.com` | only if you turn lyrics translation on; the lyric lines are sent | off by default |
| `api.github.com`, `github.com` | the update check and the download of a new version | yes: Settings → App → Check for updates |

The Android app additionally uses `music.163.com`, `genius.com` (lyrics fallbacks) and public Piped
servers (audio fallback); the desktop version does not.

## What stays on the device

- Library, likes, playlists, history, downloads, statistics, taste profile and settings.
  Android: app storage. Windows: `%APPDATA%\w0y`. Linux: `~/.local/share/w0y` (settings and
  logs in `~/.config/w0y`).
- The sign-in session. Desktop: stored **encrypted** (Windows DPAPI; Linux AES-GCM with a key in the
  system keyring through Secret Service, or in a file readable only by you). It never appears in
  logs, in *Copy debug info* or in a backup file.
- A short local record of unhandled errors (`crash.log`, last ~48 KB, no cookies). It is never sent anywhere.

*Copy debug info* and *Test connection* only copy text to your clipboard / make one ordinary request
each; you decide whether to share the text.
