@echo off
:: Check for Administrator elevation (required for raw disk access)
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [INFO] Administrator privileges are required for raw disk wiping.
    echo [INFO] Requesting elevation...
    powershell -NoProfile -Command "Start-Process cmd -ArgumentList '/k \"\"%~f0\"\"' -Verb RunAs"
    exit /b
)

echo ============================================================
echo  SecureWipe - Production Server
echo ============================================================
echo.

if not exist "%~dp0backend\target\securewipe-backend-1.0.0.jar" (
    echo [WARN] Backend JAR not found! Running build first...
    call "%~dp0build-production.bat"
)

echo [1/2] Starting Production Backend (Port 8080)...
start "SecureWipe Production Backend" cmd /k "cd /d %~dp0backend\target && java -jar securewipe-backend-1.0.0.jar"

echo Waiting 10 seconds for backend to start...
timeout /t 10 /nobreak > nul

echo [2/2] Starting Production Frontend (Port 5173)...
start "SecureWipe Production Frontend" cmd /k "cd /d %~dp0frontend && npx --yes serve -s dist -l 5173"

echo.
echo ============================================================
echo  SecureWipe Production running at:
echo  Frontend : http://localhost:5173
echo  Backend  : http://localhost:8080
echo  Login    : admin / Admin@123
echo ============================================================
pause
