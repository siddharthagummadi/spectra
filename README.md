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

The system is decoupled into four core components:
1. **Frontend (`/frontend`):** Modern React single-page application providing real-time dashboards, service registries, metric telemetry visualizers, and incident management boards.
2. **Backend Core (`/backend`):** High-throughput Spring Boot API server with Spring Security (RBAC), JPA persistence, alert rule evaluation engine, and incident lifecycle management.
3. **AI Intelligence Microservice (`/ai-service`):** Python FastAPI service providing Z-score/IQR anomaly detection, failure forecasting, automated Root Cause Analysis (RCA), and retrieval-augmented runbook recommendations (RAG).
4. **Monitoring Agent (`/agent`):** Lightweight standalone Java telemetry agent for host-level metric collection, local buffering, and authenticated ingestion.

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
├── agent/                      # Standalone Java monitoring agent
│   ├── src/                    # Agent collectors, local buffer, API client
│   └── pom.xml
├── ai-service/                 # FastAPI AI & ML Intelligence microservice
│   ├── knowledge_base/         # Incident resolution runbooks
│   ├── services/               # Anomaly detection, RCA, Copilot, and RAG services
│   ├── tests/                  # AI microservice unit & API tests
│   ├── Dockerfile              # Dockerfile for FastAPI AI microservice
│   ├── main.py                 # FastAPI entrypoint
│   ├── requirements.txt
│   └── schemas.py
├── backend/                    # Spring Boot backend core API
│   ├── .mvn/                   # Maven wrapper metadata
│   ├── src/main/java/          # Application source (config, controller, dto, entity, repository, security, service)
│   ├── src/test/java/          # Unit and integration tests
│   ├── Dockerfile              # Multi-stage Dockerfile for Spring Boot
│   ├── mvnw / mvnw.cmd         # Maven wrapper scripts
│   └── pom.xml
├── frontend/                   # React + Vite frontend application
│   ├── src/                    # API clients, components, contexts, pages, styles
│   ├── Dockerfile              # Multi-stage Dockerfile for React + Nginx
│   ├── nginx.conf              # Nginx reverse-proxy configuration
│   ├── package.json
│   └── vite.config.js
├── infrastructure/             # Infrastructure & observability configurations
│   └── prometheus/
│       └── prometheus.yml      # Prometheus scrape configuration
├── .env.example                # Root environment variable template
└── docker-compose.yml          # Orchestration for full stack + Prometheus
```

---

## 🚀 Quick Start Guide

### Option 1: Running Services Locally

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
cd backend
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

### Option 2: Full Stack Containerized (Docker Compose)
To run the entire platform with PostgreSQL and Prometheus using Docker:
```bash
docker compose up --build
```
- **Web UI:** `http://localhost:3000`
- **Backend API:** `http://localhost:8080`
- **AI Microservice:** `http://localhost:8001`
- **Prometheus:** `http://localhost:9090`

---

### Option 3: Free Cloud Deployment (Vercel + Render)
1. **Backend + AI Service + PostgreSQL on Render:**
   - Create a **New Blueprint** in Render connected to this GitHub repository (uses [render.yaml](render.yaml)).
   - Copy the deployed `spectra-backend` URL (e.g., `https://spectra-backend.onrender.com`).
2. **Frontend on Vercel:**
   - Import this GitHub repository in Vercel and set **Root Directory** to `frontend`.
   - Set environment variable `VITE_API_BASE_URL` to your Render backend URL (without a trailing slash).
   - Deploy to get your live HTTPS link.

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
