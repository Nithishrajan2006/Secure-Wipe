@echo off
:: Check for Administrator elevation (required for raw disk access to USB devices)
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [INFO] Administrator privileges are required for raw disk wiping.
    echo [INFO] Requesting elevation...
    powershell -NoProfile -Command "Start-Process cmd -ArgumentList '/c \"\"%~f0\"\"' -Verb RunAs"
    exit /b
)

echo ============================================================
echo  SecureWipe - SIH25070 - Ministry of Mines
echo  Development Startup Script
echo ============================================================
echo.

echo [1/2] Starting Backend (Spring Boot)...
echo NOTE: Make sure MySQL is running on port 3306
echo       DB: securewipe_db, User: securewipe_user, Pass: SecureWipe@2024
echo.
start "SecureWipe Backend" cmd /k "cd /d %~dp0backend && mvnw.cmd spring-boot:run"

echo Waiting for backend to be ready on port 8080 (this may take up to 25s)...
powershell -NoProfile -Command "$ready = $false; for ($i = 0; $i -lt 40; $i++) { try { $client = New-Object System.Net.Sockets.TcpClient('127.0.0.1', 8080); $client.Close(); $ready = $true; break } catch { Start-Sleep -Seconds 1 } }; if ($ready) { Write-Host 'Backend is ready!' -ForegroundColor Green } else { Write-Host 'Backend startup taking longer than usual, proceeding...' -ForegroundColor Yellow }"

echo.
echo [2/2] Starting Frontend (React + Vite)...
start "SecureWipe Frontend" cmd /k "cd /d %~dp0frontend && npm run dev"

echo.
echo ============================================================
echo  App URLs:
echo  Frontend : http://localhost:5173
echo  Backend  : http://localhost:8080
echo  Swagger  : http://localhost:8080/swagger-ui.html
echo.
echo  Login    : admin / Admin@123
echo ============================================================
echo.
pause
