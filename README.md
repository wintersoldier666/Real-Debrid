# Facebook Lite – Marketplace & Messages Only

A minimal Android app that gives you Facebook **Marketplace** and **Messages** — nothing else.
No Feed, no Reels, no Stories, no Watch, no Gaming.

## Features

| Feature | Details |
|---|---|
| **Marketplace only** | Browse, buy, and sell on Facebook Marketplace |
| **Messages only** | Read and send Facebook Messages / Messenger |
| **URL filter** | Any navigation to Feed, Reels, Stories, Watch, Groups, etc. is blocked |
| **CSS injection** | Facebook's own nav bars and sidebars are hidden |
| **Offline handling** | Shows a friendly error screen when there's no internet |
| **Swipe-to-refresh** | Pull down to reload the current page |
| **Back navigation** | Hardware back button navigates within allowed pages only |

## Blocked sections

- News Feed (`/`)
- Reels & Short Videos (`/reels`, `/video`, `/watch`)
- Stories (`/stories`)
- Events (`/events`)
- Groups (`/groups`)
- Pages (`/pages`)
- Gaming (`/gaming`)
- Jobs (`/jobs`)
- Friends (`/friends`)
- Notifications (`/notifications`)
- Memories, Saved, Fundraisers, Ads, and more

## Build

```bash
./gradlew assembleDebug
```

APK will be at `app/build/outputs/apk/debug/app-debug.apk`

## Requirements

- Android 7.0+ (API 24)
- Internet permission (required to load Facebook)

## Architecture

```
MainActivity
├── FacebookWebViewClient   — URL interception & blocking, CSS injection
├── FacebookWebChromeClient — Progress bar & title updates
└── ActivityMainBinding     — ViewBinding for layout
```
