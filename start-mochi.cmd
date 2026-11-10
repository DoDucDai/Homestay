@echo off
setlocal
cd /d "%~dp0"
echo Starting Mochi AI RAG on http://127.0.0.1:8091
echo Keep this window open while using Mochi on the web.
echo The Homestay backend must also be running on port 8080.
powershell.exe -NoLogo -NoProfile -File "%~dp0ai-rag\start.ps1" -BackendUrl http://127.0.0.1:8080
if errorlevel 1 (
  echo Mochi stopped with an error. See the message above.
  pause
)
endlocal
