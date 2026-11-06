# ⚡ Spectra - Enterprise AIOps & Observability Platform

**Spectra** is an intelligent, full-stack Cloud Observability and Automated Operations (AIOps) platform. It continuously monitors distributed microservices, ingests telemetry metrics, executes statistical and machine-learning-based anomaly detection, and accelerates incident response using AI-driven Root Cause Analysis (RCA).

---

## 🏗️ Architecture Overview

```
                      ┌────────────────────────────────────────┐
                      │        Spectra React Frontend          │
                      │     (Vite + React 18 + Dashboard)      │
                      └───────────────────┬────────────────────┘
                                          │  Port 5173 / 3000
                                          ▼
                      ┌────────────────────────────────────────┐
                      │       Spectra Spring Boot Core         │
                      │      (Java 21/26 + Spring Security)    │
                      └─────────────┬────────────────────┬─────┘
                                    │                    │
              Port 5432             ▼                    ▼  Port 8081 / 8001
    ┌───────────────────────────┐         ┌───────────────────────────────┐
    │   PostgreSQL Database     │         │ Spectra AI Intelligence Svc   │
    │  (Relational Data Store)  │         │ (FastAPI + ML + RAG Runbooks) │
    └───────────────────────────┘         └───────────────────────────────┘
```

The system is decoupled into three primary microservices:
1. **Frontend (`/frontend`):** Modern React single-page application providing real-time dashboards, service registries, metric telemetry visualizers, and incident management boards.
2. **Backend Core (`/src`):** High-throughput Spring Boot API server with Spring Security (RBAC), JPA persistence, alert rule evaluation engine, and incident lifecycle management.
3. **AI Intelligence Microservice (`/ai-service`):** Python FastAPI service providing Z-score/IQR anomaly detection, failure forecasting, automated Root Cause Analysis (RCA), and retrieval-augmented runbook recommendations (RAG).

---

## ✨ Key Features

- **Microservice Fleet Monitoring:** Real-time health tracking, latency distribution, and operational status across development, staging, and production environments.
- **High-Frequency Telemetry Ingestion:** Streams CPU, Memory, Latency, and Error Rate metrics with historical aggregation.
- **Intelligent Anomaly Detection:** Real-time statistical analysis flagging abnormal service behavior before widespread outages occur.
- **Incident Lifecycle Management:** State-machine-driven workflow (`OPEN` ➔ `IN_PROGRESS` ➔ `RESOLVED`) with engineer audit notes and resolution logging.
- **AI Root Cause Analysis (RCA):** Automatically correlates anomalies, active alerts, and error logs to identify probable root causes.
- **Runbook RAG Recommendations:** Suggests concrete remediation procedures matched against incident symptoms.
- **Role-Based Access Control:** Secure authentication powered by Spring Security with BCrypt hashing.

---

## 📁 Repository Structure

```
spectra/
├── frontend/               # React + Vite frontend application
│   ├── src/                # Components, pages, contexts, and API clients
│   └── package.json
├── src/main/java/          # Spring Boot backend source code
│   └── com/spectra/
│       ├── config/         # Security and database configurations
│       ├── controller/     # REST controllers (Services, Metrics, Alerts, Incidents, AI)
│       ├── entity/         # JPA entities
│       ├── repository/     # Spring Data JPA repositories
│       └── service/        # Core business logic and external AI client
├── ai-service/             # FastAPI AI & ML Intelligence microservice
│   ├── services/           # Anomaly detection, RCA, Copilot, and RAG services
│   ├── knowledge_base/     # Incident resolution runbooks
│   └── main.py             # FastAPI entrypoint
├── Dockerfile.backend      # Multi-stage Dockerfile for Spring Boot
├── Dockerfile.frontend     # Multi-stage Dockerfile for React + Nginx
├── Dockerfile.ai           # Dockerfile for FastAPI AI microservice
├── docker-compose.yml      # Orchestration for full stack + Prometheus
├── start-spectra.bat       # 1-Click Windows launcher script
└── start-spectra.ps1       # 1-Click PowerShell launcher script
```

---

## 🚀 Quick Start Guide

### Option 1: One-Click Local Launcher (Windows)
Double-click `start-spectra.bat` or run in PowerShell:
```powershell
.\start-spectra.ps1
```
This automatically launches the AI service, Spring Boot backend, and React frontend in separate windows.

---

### Option 2: Running Services Manually

#### 1. AI Intelligence Microservice
```bash
cd ai-service
pip install -r requirements.txt
python main.py
```
*Runs on `http://localhost:8081` (Health: `http://localhost:8081/health`).*

#### 2. Backend API (Spring Boot)
Ensure PostgreSQL is running on port 5432 with database `spectra_db`:
```bash
# In the root directory:
./mvnw spring-boot:run
```
*Runs on `http://localhost:8080`.*

#### 3. Frontend UI (React + Vite)
```bash
cd frontend
npm install
npm run dev
```
*Runs on `http://localhost:5173`.*

---

### Option 3: Full Stack Containerized (Docker Compose)
To run the entire platform with PostgreSQL and Prometheus using Docker:
```bash
docker compose up --build
```
- **Web UI:** `http://localhost:3000`
- **Backend API:** `http://localhost:8080`
- **AI Microservice:** `http://localhost:8001`
- **Prometheus:** `http://localhost:9090`

---

## 🛡️ Default Service Ports

| Service | Port | Endpoint |
|---|---|---|
| Frontend Web UI | `5173` / `3000` | `/` |
| Spring Boot Backend | `8080` | `/api` |
| AI Microservice | `8081` / `8001` | `/health`, `/api/v1/*` |
| PostgreSQL | `5432` | `jdbc:postgresql://localhost:5432/spectra_db` |
| Prometheus | `9090` | `/` |

---

## 📄 License
This project is developed for enterprise observability and academic evaluation.
