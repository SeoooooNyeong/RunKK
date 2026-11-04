@echo off
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  echo Please install Node.js 20 or newer.
  pause
  exit /b 1
)
if not exist ".env" (
  copy /Y ".env.example" ".env" >nul
  echo Enter OPENAI_API_KEY, save, then close Notepad.
  start /wait notepad.exe ".env"
)
if not exist "node_modules\openai\package.json" (
  call npm install
  if errorlevel 1 (
    echo npm install failed.
    pause
    exit /b 1
  )
)
echo RunKK v5: Open http://localhost:3000 after the server starts.
echo Stop any previous RunKK server with Ctrl+C first.
node server.mjs
pause
