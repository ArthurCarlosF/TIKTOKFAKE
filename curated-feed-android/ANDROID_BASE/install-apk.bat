@echo off
setlocal

set "ROOT=%~dp0"
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "APK=%ROOT%app\build\outputs\apk\debug\app-debug.apk"
set "LOG=%ROOT%install-log.txt"

if not exist "%ADB%" (
  echo ADB nao encontrado em "%ADB%".
  exit /b 1
)

if not exist "%APK%" (
  echo APK nao encontrado. Rode build-apk.bat primeiro.
  exit /b 1
)

echo ===== Dispositivos ===== > "%LOG%"
"%ADB%" devices -l >> "%LOG%" 2>&1

echo. >> "%LOG%"
echo ===== Tentando instalar ===== >> "%LOG%"
"%ADB%" install -r -d "%APK%" >> "%LOG%" 2>&1
set "RESULT=%ERRORLEVEL%"

type "%LOG%"
exit /b %RESULT%
