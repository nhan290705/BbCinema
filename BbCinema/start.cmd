@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1" %*
set "BBCINEMA_EXIT=%ERRORLEVEL%"
if not "%BBCINEMA_EXIT%"=="0" pause
exit /b %BBCINEMA_EXIT%
