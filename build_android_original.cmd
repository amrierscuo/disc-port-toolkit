@echo off
setlocal
python -B "%~dp0tools\build.py" android-original %*
exit /b %errorlevel%
