# SecureWipe - Development Startup Script (PowerShell)
# SIH25070 - Ministry of Mines, Government of India

# Self-elevate to Administrator (required for raw disk wiping on Windows)
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "[INFO] Administrator privileges are required for raw disk wiping." -ForegroundColor Yellow
    Write-Host "[INFO] Requesting Administrator elevation..." -ForegroundColor Yellow
    Start-Process powershell -ArgumentList "-NoExit -ExecutionPolicy Bypass -File `"$PSCommandPath`"" -Verb RunAs
    exit
}

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " SecureWipe - SIH25070 - Ministry of Mines" -ForegroundColor Cyan  
Write-Host " Development Startup Script" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# Start Backend
Write-Host "`n[1/2] Starting Backend (Spring Boot on :8080)..." -ForegroundColor Yellow
Write-Host "      Make sure MySQL is running: securewipe_db / securewipe_user / SecureWipe@2024"

$backendPath = Join-Path $PSScriptRoot "backend"
Start-Process powershell -ArgumentList "-NoExit -Command `"cd '$backendPath'; .\mvnw.cmd spring-boot:run`"" -WindowStyle Normal

Write-Host "      Waiting for backend to be ready on :8080..." -ForegroundColor Cyan
$ready = $false
for ($i = 0; $i -lt 40; $i++) {
    try {
        $client = New-Object System.Net.Sockets.TcpClient("127.0.0.1", 8080)
        $client.Close()
        $ready = $true
        break
    } catch {
        Start-Sleep -Seconds 1
    }
}
if ($ready) {
    Write-Host "      Backend is ready!" -ForegroundColor Green
} else {
    Write-Host "      Backend startup taking longer than usual, proceeding..." -ForegroundColor Yellow
}

# Start Frontend
Write-Host "`n[2/2] Starting Frontend (React Vite on :5173)..." -ForegroundColor Yellow
$frontendPath = Join-Path $PSScriptRoot "frontend"
Start-Process powershell -ArgumentList "-NoExit -Command `"cd '$frontendPath'; npm run dev`"" -WindowStyle Normal

Start-Sleep 3

Write-Host "`n============================================================" -ForegroundColor Green
Write-Host " SecureWipe is starting!" -ForegroundColor Green
Write-Host " Frontend : http://localhost:5173" -ForegroundColor White
Write-Host " Backend  : http://localhost:8080" -ForegroundColor White
Write-Host " Swagger  : http://localhost:8080/swagger-ui.html" -ForegroundColor White
Write-Host " Login    : admin / Admin@123" -ForegroundColor White
Write-Host "============================================================" -ForegroundColor Green
