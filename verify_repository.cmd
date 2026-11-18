@echo off
setlocal
python -B "%~dp0tools\verify_repository.py" %*
exit /b %errorlevel%
