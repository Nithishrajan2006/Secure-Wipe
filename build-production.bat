@echo off
echo ============================================================
echo  SecureWipe - Production Build Script (No GitHub Required)
echo ============================================================
echo.

echo [1/2] Building Frontend (React Production Bundle)...
cd /d %~dp0frontend
call npm run build
if %errorlevel% neq 0 (
    echo [ERROR] Frontend build failed!
    pause
    exit /b %errorlevel%
)
echo Frontend build complete! Output in frontend\dist\
echo.

echo [2/2] Building Backend (Spring Boot Executable JAR)...
cd /d %~dp0backend
call .\mvnw.cmd clean package -DskipTests
if %errorlevel% neq 0 (
    echo [ERROR] Backend build failed!
    pause
    exit /b %errorlevel%
)
echo Backend build complete! JAR file created in backend\target\
echo.

echo ============================================================
echo  Production Build Successful!
echo ============================================================
echo  - Frontend bundle: frontend\dist
echo  - Backend JAR:     backend\target\securewipe-backend-1.0.0.jar
echo ============================================================
pause
