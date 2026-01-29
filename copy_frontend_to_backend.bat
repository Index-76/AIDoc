@echo off
setlocal enabledelayedexpansion

REM AIDoc project frontend build files copy script
REM Purpose: Copy contents of frontend/build/web to backend/src/main/resources/static

echo.
echo ================================================
echo        AIDoc Frontend Build Copy Script
echo ================================================
echo.

REM Change to script's directory (project root)
cd /d "%~dp0"

REM Check if frontend/build/web directory exists
set "FRONTEND_DIR=frontend\build\web"
if not exist "!FRONTEND_DIR!" (
    echo Error: Frontend build directory does not exist: !FRONTEND_DIR!
    echo Please run "flutter build web" first to build the frontend
    pause
    exit /b 1
)

REM Check if backend/src/main/resources/static directory exists, create if not
set "BACKEND_DIR=backend\src\main\resources\static"
if not exist "!BACKEND_DIR!" (
    echo Creating backend static resources directory: !BACKEND_DIR!
    mkdir "!BACKEND_DIR!"
    if errorlevel 1 (
        echo Error: Could not create backend static resources directory: !BACKEND_DIR!
        pause
        exit /b 1
    )
)

echo Copying frontend build files to backend static resources directory...
echo Source directory: !FRONTEND_DIR!
echo Target directory: !BACKEND_DIR!
echo.

REM Use robocopy to copy files, preserve directory structure, overwrite existing files
robocopy "!FRONTEND_DIR!" "!BACKEND_DIR!" /E /NFL /NDL /NJH /NJS

REM Check the result of the copy operation
if errorlevel 16 (
    echo Fatal error: A serious error occurred during robocopy
    pause
    exit /b 16
) else if errorlevel 8 (
    echo Info: Some files may still be copying in the background
) else if errorlevel 4 (
    echo Info: Some files were copied
) else if errorlevel 1 (
    echo Success: Frontend build files successfully copied to backend static resources directory
) else (
    echo Success: Frontend build files copied to backend static resources directory (no new files to copy)
)

echo.
echo Copy operation completed!
echo.