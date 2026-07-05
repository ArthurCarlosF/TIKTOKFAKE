# TikTok Care - MVP

TikTok Care is a curated vertical-video app for assisted use. It keeps the interaction model familiar to users who already know short-video apps, while using its own name, caregiver controls, and a controlled content source.

## Product Rules

- The app name is `TikTok Care`.
- The app does not request or store TikTok credentials.
- The app does not show direct messages, live streams, gifting, external links, purchases, comments, or open search in the MVP.
- The content feed is fully curated from a Google Sheet.
- A caregiver or clinician can manage videos through a GitHub Pages admin page that calls Google Apps Script.
- The UI may use a full-screen vertical feed, right-side actions, bottom caption area, and simple top navigation, but must keep distinct branding and avoid pretending the user is logged into the official TikTok app.

## Feed Behavior

- Starts directly on the video feed.
- Plays one active video at a time.
- Supports swipe up/down or next/previous buttons.
- Shows title, source label, category, and optional caregiver note.
- Hides inactive videos.
- Sorts by `order`, then `updatedAt`.
- Refreshes the feed when the app starts and can cache the last successful response.

## Google Sheet Columns

| Column | Required | Example | Notes |
| --- | --- | --- | --- |
| id | yes | `vid-001` | Stable unique ID. |
| title | yes | `Receita de bolo simples` | User-facing title. |
| videoUrl | no | `https://example.com/video.mp4` | Direct playable URL or YouTube URL. Required only when `youtubeId` is empty. |
| youtubeId | no | `dQw4w9WgXcQ` | YouTube video ID. Required only when `videoUrl` is empty. |
| thumbnailUrl | no | `https://example.com/thumb.jpg` | Used while loading. |
| active | yes | `TRUE` | Only active rows appear in the app. |
| category | no | `culinaria` | Filter/group metadata. |
| sourceLabel | no | `Canal aprovado` | Short source text. |
| caregiverNote | no | `Conteudo validado` | Optional internal/display note. |
| order | no | `10` | Lower appears earlier. |
| updatedAt | no | `2026-07-05T10:00:00-03:00` | Apps Script can fill this. |

## Public Feed Response

```json
{
  "version": 1,
  "updatedAt": "2026-07-05T10:00:00-03:00",
  "videos": [
    {
      "id": "vid-001",
      "title": "Receita de bolo simples",
      "videoUrl": "https://example.com/video.mp4",
      "youtubeId": "",
      "thumbnailUrl": "https://example.com/thumb.jpg",
      "active": true,
      "category": "culinaria",
      "sourceLabel": "Canal aprovado",
      "caregiverNote": "Conteudo validado",
      "order": 10,
      "updatedAt": "2026-07-05T10:00:00-03:00"
    }
  ]
}
```

## Android Integration Contract

- `GET {APPS_SCRIPT_WEB_APP_URL}?action=feed`
- Response: JSON matching `Public Feed Response`.
- Timeout: 10 seconds.
- Cache: keep last valid JSON locally.
- Empty state: show a simple branded screen asking caregiver to add videos.
- Error state: keep cached feed if available; otherwise show a retry action.
