# Spectra Launch Script for PowerShell
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "    Starting Spectra Enterprise AIOps Platform     " -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host ""

$root = $PSScriptRoot

Write-Host "[1/3] Starting AI Intelligence Microservice (Port 8081)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$root\ai-service'; python main.py"

Write-Host "[2/3] Starting Backend Spring Boot Core (Port 8080)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$root'; .\mvnw.cmd spring-boot:run"

Write-Host "[3/3] Starting Frontend React UI (Port 5173)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$root\frontend'; npm run dev"

Write-Host ""
Write-Host "===================================================" -ForegroundColor Green
Write-Host "All 3 services are launching in separate windows!" -ForegroundColor Green
Write-Host "Web UI (Local):    http://localhost:5173" -ForegroundColor White
Write-Host "Web UI (Network):  http://10.127.152.4:5173" -ForegroundColor White
Write-Host "Backend API:       http://localhost:8080/api" -ForegroundColor White
Write-Host "AI Health Check:   http://localhost:8081/health" -ForegroundColor White
Write-Host "===================================================" -ForegroundColor Green
