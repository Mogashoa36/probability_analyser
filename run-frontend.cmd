@echo off
REM ---------------------------------------------------------------------------
REM Starts the Formline frontend dev server on http://localhost:4200
REM
REM /api calls are proxied to the backend (see frontend/proxy.conf.json), so
REM start the backend first with run-backend.cmd.
REM ---------------------------------------------------------------------------
setlocal
set "ROOT=%~dp0"
set "FRONTEND=%ROOT%frontend"

where npm >nul 2>&1
if errorlevel 1 (
  echo [formline] ERROR: 'npm' is not on PATH. Install Node.js 18+ and reopen the terminal.
  exit /b 1
)

if not exist "%FRONTEND%\node_modules" (
  echo [formline] Installing frontend dependencies ^(first run, this takes a while^)...
  pushd "%FRONTEND%"
  call npm install || (popd & echo [formline] ERROR: npm install failed. & exit /b 1)
  popd
)

pushd "%FRONTEND%"
echo [formline] Starting frontend on http://localhost:4200  (Ctrl+C to stop)
echo.
call npm start
endlocal
