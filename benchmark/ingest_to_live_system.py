import os
import sys
import json
import base64
import requests

def ingest_dataset_to_spectra(username="operator@spectra.local", password="Password123!"):
    base_url = "http://localhost:8080"
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    dataset_path = os.path.join(root_dir, "benchmark", "synthetic_microservice_dataset.json")

    print("=" * 70)
    print("      SPECTRA LIVE REST API - SYNTHETIC DATASET INGESTION      ")
    print("=" * 70)

    # 1. Health check
    try:
        r = requests.get(f"{base_url}/api/health", timeout=3)
        if r.status_code != 200:
            print(f"[!] Backend returned status {r.status_code}. Is Spring Boot running on port 8080?")
            return
    except Exception:
        print("[!] Could not connect to http://localhost:8080. Please ensure Spring Boot is running (Window 2)!")
        return

    print("[1/4] Connected to Spring Boot core on http://localhost:8080")

    # 2. Register / Authenticate operator user
    auth_header = {"Authorization": "Basic " + base64.b64encode(f"{username}:{password}".encode()).decode()}
    
    # Try registration
    try:
        reg_resp = requests.post(f"{base_url}/api/users/register", json={
            "fullName": "Spectra Benchmark Operator",
            "email": username,
            "password": password
        }, timeout=3)
        if reg_resp.status_code in [201, 200]:
            print(f"[2/4] Created benchmark operator account: {username}")
        else:
            print(f"[2/4] Operator account exists or ready, authenticating as: {username}")
    except Exception as e:
        print(f"[!] User setup warning: {e}")

    # 3. Create or Fetch Monitored Services
    services_map = {}
    services_to_create = [
        {"name": "Payment-Service", "serviceUrl": "http://payment-gateway.cluster.local:8080", "environment": "PROD"},
        {"name": "Order-Processing-Service", "serviceUrl": "http://order-processor.cluster.local:8081", "environment": "PROD"},
        {"name": "Auth-Identity-Service", "serviceUrl": "http://auth-service.cluster.local:8082", "environment": "PROD"},
        {"name": "Database-Gateway", "serviceUrl": "http://postgres-pool.cluster.local:5432", "environment": "PROD"}
    ]

    print("[3/4] Registering benchmark microservices in cluster...")
    for s in services_to_create:
        try:
            resp = requests.post(f"{base_url}/api/services", json=s, headers=auth_header, timeout=3)
            if resp.status_code in [200, 201]:
                data = resp.json()
                services_map[s["name"]] = data.get("id")
            else:
                # If already exists, fetch list
                list_resp = requests.get(f"{base_url}/api/services", headers=auth_header, timeout=3)
                for item in list_resp.json():
                    services_map[item["name"]] = item["id"]
        except Exception:
            pass

    print(f"      Active services registered: {list(services_map.keys())}")

    # 4. Ingest synthetic telemetry metrics & trigger incidents
    with open(dataset_path, "r", encoding="utf-8") as f:
        dataset = json.load(f)

    print(f"[4/4] Ingesting {len(dataset)} telemetry data points into database...")
    ingested_metrics = 0
    created_incidents = 0

    for case in dataset:
        svc_name = case["service_name"]
        svc_id = services_map.get(svc_name, 1)

        # Ingest recent metrics
        for m in case.get("recent_metrics", []):
            metric_payload = {
                "serviceId": svc_id,
                "metricName": m["metricName"],
                "metricValue": m["metricValue"],
                "unit": m["unit"]
            }
            try:
                m_resp = requests.post(f"{base_url}/api/metrics", json=metric_payload, headers=auth_header, timeout=2)
                if m_resp.status_code in [200, 201]:
                    ingested_metrics += 1
            except Exception:
                pass

        # For critical categories, create an active incident with AI RCA
        if case["ground_truth_category"] in ["MEMORY_LEAK", "DATABASE_CONTENTION", "CPU_STARVATION", "NETWORK_DEPENDENCY"]:
            inc_payload = {
                "serviceId": svc_id,
                "title": case["incident_title"],
                "severity": "CRITICAL" if case["ground_truth_category"] in ["MEMORY_LEAK", "DATABASE_CONTENTION"] else "HIGH",
                "description": f"Benchmark injected operational failure [{case['ground_truth_category']}]: {case['logs'][1]}",
                "assignedTo": username
            }
            try:
                inc_resp = requests.post(f"{base_url}/api/incidents", json=inc_payload, headers=auth_header, timeout=2)
                if inc_resp.status_code in [200, 201]:
                    created_incidents += 1
            except Exception:
                pass

    print("=" * 70)
    print("      DATASET INGESTION COMPLETE - LIVE SPECTRA STATUS         ")
    print("=" * 70)
    print(f"  Total Telemetry Metrics Streamed : {ingested_metrics}")
    print(f"  Live Benchmark Incidents Opened  : {created_incidents}")
    print("\nOpen your browser at: http://localhost:5173")
    print(f"Sign in with:  Email: {username}  |  Password: {password}")
    print("You will see the live populated dashboards, sparklines, alerts, and AI RCA cards!")
    print("=" * 70)

if __name__ == "__main__":
    ingest_dataset_to_spectra()
