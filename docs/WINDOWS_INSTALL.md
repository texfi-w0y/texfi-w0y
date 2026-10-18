# Installing w0y on Windows

w0y for Windows is an installer (`w0y-windows-x64-setup.exe`) and a portable archive
(`w0y-windows-x64-portable.zip`), 64-bit, Windows 10 and 11. Both are on the
[release page](https://github.com/texfi-w0y/texfi-w0y/releases). No administrator rights are needed.

## 1. Check the download

Each release has `SHA256SUMS-desktop.txt`. In PowerShell, from the folder with the file:

```powershell
Get-FileHash .\w0y-windows-x64-setup.exe -Algorithm SHA256
```

The hash must match the line for that file in `SHA256SUMS-desktop.txt`. If it does not, delete the file and download it again.

## 2. The SmartScreen warning

The installer is **not signed with a paid code-signing certificate** (the project is free and
open source), so Windows SmartScreen may say "Windows protected your PC".
That is expected for a new unsigned file; it does not mean the file is infected.
After you checked the hash above:

1. Click **More info**.
2. Click **Run anyway**.

## 3. What gets installed and where

- The program goes to `%LOCALAPPDATA%\Programs\w0y` (only for your user).
- Your library, history and settings live in `%APPDATA%\w0y`. The uninstaller does **not** delete them.
- The sign-in session is stored encrypted with Windows DPAPI (tied to your Windows account).
- Settings → App → *Open data folder* opens the folder.

## 4. Sign in to YouTube

*Library → Sign in with Google* opens a window of Microsoft Edge (or Chrome/Brave/Vivaldi if
installed) with Google's real sign-in page. You type the password there; w0y never sees it and
only takes the YouTube cookie. If that does not work, paste the cookie by hand.

## 5. Updates

w0y checks GitHub for a new version at start (at most every six hours; Settings → App can turn
it off). If one exists it offers **Update** or **Later**; the installer is downloaded, its SHA-256
is checked, and then it runs.

## 6. Media keys and tray

The keyboard media keys (play/pause, next, previous, stop) work while the window is minimised.
The tray icon menu controls playback; *Settings → App* has "Close button hides to tray" and
"Start when you sign in" (both off by default).

## Troubleshooting

Settings → App → **Copy debug info** gives a text with the version, system, audio engine and
recent errors (no cookies or tokens); **Test connection** checks YouTube Music and GitHub.
When reporting a problem please paste that text.
