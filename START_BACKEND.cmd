@echo off
setlocal EnableExtensions DisableDelayedExpansion
title The Signal - News Aggregator API
cd /d "%~dp0"
if not exist "%~dp0start.ps1" goto incomplete
if not exist "%~dp0run.ps1" goto incomplete
if not exist "%~dp0src\main\java\newsaggregator\Main.java" goto incomplete
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1"
set "signal_exit_code=%errorlevel%"
if not "%signal_exit_code%"=="0" (
    echo.
    echo Could not start the project. See the message above.
    pause
)
exit /b %signal_exit_code%

:incomplete
echo Please extract the entire ZIP before starting the project.
echo Right-click the ZIP and choose Extract All.
echo Open the extracted news-aggregator folder and double-click START_BACKEND.cmd.
echo Do not run START_BACKEND.cmd from inside the ZIP archive.
pause
exit /b 1
