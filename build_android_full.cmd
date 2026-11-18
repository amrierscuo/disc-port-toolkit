@echo off
setlocal
python -B "%~dp0tools\build.py" android-full %*
exit /b %errorlevel%
