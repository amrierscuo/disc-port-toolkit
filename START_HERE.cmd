@echo off
setlocal DisableDelayedExpansion
set "PSModulePath=%SystemRoot%\System32\WindowsPowerShell\v1.0\Modules;%ProgramFiles%\WindowsPowerShell\Modules"
set "start_shell=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
if defined PROCESSOR_ARCHITEW6432 set "start_shell=%SystemRoot%\Sysnative\WindowsPowerShell\v1.0\powershell.exe"
"%start_shell%" -NoProfile -STA -ExecutionPolicy Bypass -File "%~dp0tools\start_here.ps1" %*
exit /b %errorlevel%
