# TikTok Care

Projeto Android nativo com feed vertical curado e painel estatico para GitHub Pages.

## GitHub Pages

Publique a pasta `pages` pelo GitHub Pages.

URL esperada:

```text
https://arthurcarlosf.github.io/TIKTOKFAKE/
```

Feed estatico de teste:

```text
https://arthurcarlosf.github.io/TIKTOKFAKE/feed.json
```

## Android

Projeto:

```text
curated-feed-android/ANDROID_BASE
```

Build debug:

```powershell
cd curated-feed-android/ANDROID_BASE
.\build-apk.bat
```

Build com endpoint do feed:

```powershell
.\build-apk.bat -GradleArgs '-PTIKTOK_CARE_FEED_URL=https://script.google.com/macros/s/SEU_DEPLOYMENT_ID/exec?action=feed'
```
