@echo off
setlocal
if defined RECOMP_GAME_FILES goto launch
if exist "%~dp0inputs\windows\disc\default.xbe" goto launch
if not exist "%~dp0cache\start-here\windows-disc.txt" goto launch
"%~dp0START_HERE.cmd" -Play
:launch
set "game_exe=%~dp0dist\windows\blinx1_recomp.exe"
set "workspace=%~dp0build\windows-user"
if not defined RECOMP_GAME_FILES set "RECOMP_GAME_FILES=%~dp0inputs\windows\disc"
if not defined RECOMP_HDD_DIR set "RECOMP_HDD_DIR=%workspace%\hdd"
if not defined RECOMP_SAVE_DIR set "RECOMP_SAVE_DIR=%workspace%\saves"
if not exist "%game_exe%" (
    echo Build Clone 1 with build_windows.cmd first. 1>&2
    exit /b 2
)
if not exist "%RECOMP_GAME_FILES%\default.xbe" (
    echo Missing the PAL disc. See docs\BUILD_WINDOWS.md. 1>&2
    exit /b 2
)
if not exist "%workspace%\" mkdir "%workspace%"
if not exist "%RECOMP_HDD_DIR%\" mkdir "%RECOMP_HDD_DIR%"
if not exist "%RECOMP_HDD_DIR%\" exit /b 2
if not exist "%RECOMP_SAVE_DIR%\" mkdir "%RECOMP_SAVE_DIR%"
if not exist "%RECOMP_SAVE_DIR%\" exit /b 2
rem Select the visible Direct3D 11 renderer for an ordinary Windows launch.
if not defined RECOMP_PB_BACKEND if not defined RECOMP_FB_WINDOW set "RECOMP_PB_BACKEND=d3d11"
pushd "%workspace%" || exit /b 2
"%game_exe%"
set "game_exit=%errorlevel%"
popd
exit /b %game_exit%
