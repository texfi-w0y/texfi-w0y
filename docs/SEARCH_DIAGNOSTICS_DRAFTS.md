# Search diagnostics: drafts

Drafts only. Nothing here has been posted or released.

## 1. Reply to the Reddit user (search does not work)

> Thanks for the report, and sorry about that. Search works on our test
> phones, so I can't see the cause from here. I've added a built-in check that
> shows *why* search fails, on your phone only: nothing is sent anywhere.
>
> When the next build is out: open **Settings → About → Diagnostics**, tap
> **Test connection**, then **Copy debug info**, and paste the text here.
> It has your device, Android version, network type (including whether a VPN
> or Private DNS is on) and the last network errors. It has no account data,
> cookies or search text.
>
> Until then, three things that would help: your phone model and Android
> version, whether a VPN / Private DNS / DNS-level ad blocker is on, and
> whether you see an error or just "nothing found". Turning the VPN or
> Private DNS off for a minute is also worth a try.

## 2. beta-3 notes (one or two lines)

> Search errors are now visible with a reason, plus Copy debug info and a
> connection test (no data leaves the phone).

Longer version for the release notes:

- Search no longer shows a server error as "nothing found": a failed request
  and an empty result now look different, with a short reason and a RETRY
  button.
- One automatic retry on timeouts, DNS errors, server errors and rate limits;
  a stale sign-in is retried once without the account; a language YouTube
  doesn't know falls back to English.
- Settings → About → Diagnostics: **Test connection** (network, DNS, TLS, a
  test search, reading the answer) and **Copy debug info** (device, Android,
  language and region used for requests, network type, VPN / Private DNS /
  data saver, signed in yes/no, last 20 network events). It is plain text you
  copy and share yourself; the app never sends it.
