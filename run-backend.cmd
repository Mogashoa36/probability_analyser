@echo off
REM ---------------------------------------------------------------------------
REM Starts the Formline backend on http://localhost:8081
REM
REM Does not require Maven on PATH: it reuses the packaged jar in backend\target
REM and only builds when that jar is missing or older than the sources. If a
REM build IS needed, Maven is looked up on PATH and then in the wrapper cache
REM under %USERPROFILE%\.m2\wrapper\dists.
REM
REM Usage:  run-backend.cmd [port]      e.g.  run-backend.cmd 8082
REM ---------------------------------------------------------------------------
setlocal

set "ROOT=%~dp0"
set "HELPER=%ROOT%tools\dev-tools.ps1"
set "BACKEND=%ROOT%backend"
set "JAR=%BACKEND%\target\bet-analyzer-1.0.0.jar"
set "PORT=%~1"
if "%PORT%"=="" set "PORT=8081"

where java >nul 2>&1
if errorlevel 1 (
  echo [formline] ERROR: 'java' is not on PATH. Install JDK 17+ and reopen the terminal.
  exit /b 1
)

REM Both lookups run outside the if-block below: a for/f inside a parenthesised
REM block silently yields nothing on some cmd builds.
set "NEEDS_BUILD=0"
for /f "usebackq delims=" %%T in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%HELPER%" Stale -Jar "%JAR%" -SourceRoot "%BACKEND%"`) do if /i "%%T"=="STALE" set "NEEDS_BUILD=1"

set "MVN="
for /f "usebackq delims=" %%M in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%HELPER%" FindMaven`) do if not defined MVN set "MVN=%%M"

if "%NEEDS_BUILD%"=="1" (
  if not defined MVN (
    echo [formline] ERROR: a build is needed but no Maven was found.
    echo             Options: install Maven, or in VS Code run the
    echo             "Backend: Spring Boot ^(:8081^)" launch config, which uses
    echo             the VS Code Java extension and does not need Maven.
    exit /b 1
  )
  echo [formline] Building backend ^(jar missing or out of date^)...
  echo [formline] Using Maven: %MVN%
  pushd "%BACKEND%"
  call "%MVN%" -DskipTests package
  if errorlevel 1 (
    popd
    echo [formline] ERROR: build failed.
    exit /b 1
  )
  popd
) else (
  echo [formline] Existing jar is up to date: %JAR%
)

echo [formline] Starting backend on http://localhost:%PORT% ^(Ctrl+C to stop^)
echo.
java -jar "%JAR%" --server.port=%PORT%
endlocal
