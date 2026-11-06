@echo off
title Spectra Enterprise Platform Launcher
echo ===================================================
echo     Starting Spectra Enterprise AIOps Platform
echo ===================================================
echo.

echo [1/3] Starting AI Intelligence Microservice (Port 8081)...
start "Spectra [AI Service - Port 8081]" cmd /k "cd /d ""%~dp0ai-service"" && python main.py"

echo [2/3] Starting Backend Spring Boot Core (Port 8080)...
start "Spectra [Backend - Port 8080]" cmd /k "cd /d ""%~dp0"" && mvnw.cmd spring-boot:run"

echo [3/3] Starting Frontend React UI (Port 5173)...
start "Spectra [Frontend - Port 5173]" cmd /k "cd /d ""%~dp0frontend"" && npm run dev"

echo.
echo ===================================================
echo All services are launching in separate windows!
echo.
echo Web UI Local:   http://localhost:5173
echo Web UI Network: Check your Wi-Fi IP (e.g. http://10.127.152.4:5173)
echo Backend API:    http://localhost:8080/api
echo AI Service:     http://localhost:8081/health
echo ===================================================
echo.
pause
