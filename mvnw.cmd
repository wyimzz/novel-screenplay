@echo off
setlocal EnableExtensions EnableDelayedExpansion

where mvn >nul 2>&1
if %errorlevel% equ 0 (
  call mvn %*
  exit /b !errorlevel!
)

for /r "%USERPROFILE%\.m2\wrapper\dists" %%F in (mvn.cmd) do (
  if exist "%%F" (
    call "%%F" -Dmaven.repo.local="%USERPROFILE%\.m2\repository" %*
    exit /b !errorlevel!
  )
)

echo Maven was not found. Install Maven or run a standard Maven Wrapper once. 1>&2
exit /b 1
