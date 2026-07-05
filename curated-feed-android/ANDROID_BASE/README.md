# TikTok Care Android

App Android nativo do TikTok Care. Ele abre direto em um feed vertical curado e carrega os videos de um endpoint JSON compatível com `backend-config/apps-script.gs`.

## Configurar endpoint do feed

Passe a URL publicada do Apps Script ou de um JSON no GitHub Pages pela propriedade Gradle `TIKTOK_CARE_FEED_URL`.

```powershell
.\gradlew.bat assembleDebug -PTIKTOK_CARE_FEED_URL="https://script.google.com/macros/s/SEU_DEPLOYMENT_ID/exec?action=feed"
```

O app guarda em cache o ultimo JSON valido. Se a internet falhar, ele tenta usar esse cache.

## Gerar APK no Windows

Use o script portavel:

```powershell
.\build-apk.bat
```

Para gerar ja apontando para o feed real:

```powershell
.\build-apk.bat -GradleArgs '-PTIKTOK_CARE_FEED_URL=https://script.google.com/macros/s/SEU_DEPLOYMENT_ID/exec?action=feed'
```

Endpoint atual:

```text
https://script.google.com/macros/s/AKfycbyIx80T2ihZ-XTt9fWpliKmwBqv3DWgXViUaZRfdUUrI0QNxuJvD0wbhOb-SXr0fTdrpw/exec?action=feed
```

O script usa, nesta ordem:

1. `JAVA_HOME`, se estiver configurado.
2. `java.exe` do `PATH`, se existir.
3. Um JDK instalado em locais comuns do Windows, incluindo o JDK embutido do Android Studio como fallback.

O APK debug fica em:

```text
app\build\outputs\apk\debug\app-debug.apk
```

Para build release:

```powershell
.\build-apk.bat -Variant Release
```

## Recomendacao de ambiente

Para maior portabilidade entre maquinas, instale um JDK 17 ou superior e configure:

```powershell
JAVA_HOME=C:\Program Files\Java\jdk-17
PATH=%JAVA_HOME%\bin;%PATH%
```

O projeto nao fixa `org.gradle.java.home` no `gradle.properties`, porque esse valor normalmente aponta para um caminho local de uma maquina especifica.
