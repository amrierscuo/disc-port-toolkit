@echo off
setlocal
python -B "%~dp0tools\build.py" windows %*
exit /b %errorlevel%
