@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat assembleDebug
if errorlevel 1 (
  echo.
  echo Build gagal. Buka proyek di Android Studio dan periksa pesan Gradle Sync.
  exit /b 1
)
echo.
echo APK berhasil dibuat:
echo %CD%\app\build\outputs\apk\debug\app-debug.apk
endlocal
