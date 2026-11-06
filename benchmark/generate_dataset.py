import json
import csv
import random
from datetime import datetime, timedelta

def generate_exact_paper_dataset():
    """
    Generates the exact research paper dataset:
    Total Observations : 1200
    Normal Observations: 1138
    Error Values       : 62
    """
    random.seed(42)

    total_observations = 1200
    target_normal = 1138
    target_errors = 62

    services = [
        {"id": 1, "name": "Payment-Service", "cluster": "Bank"},
        {"id": 2, "name": "Order-Processing-Service", "cluster": "Market"},
        {"id": 3, "name": "Auth-Identity-Service", "cluster": "Security"},
        {"id": 4, "name": "Database-Gateway", "cluster": "Persistence"}
    ]

    # Distributed error categories across the 62 error values
    error_categories = [
        ("MEMORY_LEAK", 16),
        ("DATABASE_CONTENTION", 16),
        ("CPU_STARVATION", 15),
        ("NETWORK_DEPENDENCY", 15)
    ]
    # Sum: 16 + 16 + 15 + 15 = 62 error values exactly

    error_queue = []
    for cat, count in error_categories:
        error_queue.extend([cat] * count)
    random.shuffle(error_queue)

    # Pick 62 random distinct indices out of 1200 to inject errors
    error_indices = set(random.sample(range(total_observations), target_errors))

    start_time = datetime(2026, 11, 1, 0, 0, 0)
    dataset = []
    csv_rows = []

    error_counter = 0

    for idx in range(total_observations):
        svc = services[idx % len(services)]
        timestamp = (start_time + timedelta(minutes=idx * 2)).isoformat()
        is_error = idx in error_indices

        if not is_error:
            # Normal baseline observation (1138 cases)
            cat = "NORMAL"
            cpu_val = round(random.normalvariate(35.0, 6.0), 2)
            cpu_val = max(10.0, min(65.0, cpu_val))

            mem_val = round(random.normalvariate(52.0, 5.0), 2)
            mem_val = max(25.0, min(70.0, mem_val))

            lat_val = round(random.normalvariate(110.0, 25.0), 2)
            lat_val = max(30.0, min(280.0, lat_val))

            err_val = round(random.uniform(0.0, 0.4), 2)

            log_snippet = f"[{timestamp}] INFO c.s.service - Health check OK, latency {lat_val}ms within SLA"
            logs = [
                f"[{timestamp}] INFO  c.s.gateway.Router - Request routed to {svc['name']}",
                log_snippet,
                f"[{timestamp}] DEBUG c.s.metrics.Collector - CPU: {cpu_val}%, MEM: {mem_val}%"
            ]
            hist = [round(random.normalvariate(35.0, 5.0), 1) for _ in range(15)]
            current_val = cpu_val
            monitored_metric = "CPU_USAGE"
            unit = "PERCENT"
            incident_title = "Normal Steady-State Operation"
            label = 0

        else:
            # Error / Anomaly observation (62 cases)
            cat = error_queue[error_counter]
            error_counter += 1
            label = 1

            if cat == "MEMORY_LEAK":
                incident_title = f"{svc['name']} Heap Memory Exhaustion & OOM Hazard"
                mem_val = round(random.uniform(88.0, 99.2), 2)
                cpu_val = round(random.uniform(35.0, 65.0), 2)
                lat_val = round(random.uniform(400.0, 1100.0), 2)
                err_val = round(random.uniform(0.5, 3.0), 2)
                log_snippet = f"[{timestamp}] ERROR c.s.service.MemoryManager - java.lang.OutOfMemoryError: Java heap space"
                logs = [
                    f"[{timestamp}] WARN  c.s.cache.MemoryStore - Eviction threshold exceeded: cache footprint 94%",
                    log_snippet,
                    f"[{timestamp}] WARN  org.apache.catalina.core.StandardWrapperValve - Major GC pause duration 4620ms"
                ]
                hist = [round(random.uniform(50.0, 65.0), 1) for _ in range(15)]
                current_val = mem_val
                monitored_metric = "MEMORY_USAGE"
                unit = "PERCENT"

            elif cat == "DATABASE_CONTENTION":
                incident_title = f"{svc['name']} Connection Pool Starvation & Query Latency Surge"
                mem_val = round(random.uniform(40.0, 65.0), 2)
                cpu_val = round(random.uniform(25.0, 50.0), 2)
                lat_val = round(random.uniform(2400.0, 6800.0), 2)
                err_val = round(random.uniform(1.0, 4.5), 2)
                log_snippet = f"[{timestamp}] WARN  com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - Connection is not available, request timed out after 5000ms"
                logs = [
                    f"[{timestamp}] WARN  c.s.repo.OrderRepository - Slow SQL query execution: {lat_val}ms",
                    log_snippet,
                    f"[{timestamp}] ERROR org.hibernate.engine.jdbc.spi.SqlExceptionHelper - Active connections maxed at 50/50"
                ]
                hist = [round(random.uniform(80.0, 200.0), 1) for _ in range(15)]
                current_val = lat_val
                monitored_metric = "RESPONSE_TIME"
                unit = "MILLISECONDS"

            elif cat == "CPU_STARVATION":
                incident_title = f"{svc['name']} CPU Saturation & Worker Thread Starvation"
                mem_val = round(random.uniform(45.0, 70.0), 2)
                cpu_val = round(random.uniform(89.5, 99.9), 2)
                lat_val = round(random.uniform(2600.0, 5500.0), 2)
                err_val = round(random.uniform(2.5, 7.0), 2)
                log_snippet = f"[{timestamp}] ERROR org.apache.tomcat.util.net.NioEndpoint - Thread starvation observed under burst throughput"
                logs = [
                    f"[{timestamp}] WARN  c.s.engine.CalculationWorker - Worker pool saturation: active threads 256/256",
                    log_snippet,
                    f"[{timestamp}] WARN  c.s.health.ComputeHealthCheck - Core utilization at {cpu_val}%"
                ]
                hist = [round(random.uniform(35.0, 55.0), 1) for _ in range(15)]
                current_val = cpu_val
                monitored_metric = "CPU_USAGE"
                unit = "PERCENT"

            else:  # NETWORK_DEPENDENCY
                incident_title = f"{svc['name']} Downstream Network Timeout & Error Surge"
                mem_val = round(random.uniform(35.0, 60.0), 2)
                cpu_val = round(random.uniform(30.0, 55.0), 2)
                lat_val = round(random.uniform(1500.0, 4200.0), 2)
                err_val = round(random.uniform(9.0, 36.0), 2)
                log_snippet = f"[{timestamp}] ERROR c.s.client.DownstreamGateway - java.net.SocketTimeoutException: Read timed out"
                logs = [
                    f"[{timestamp}] ERROR c.s.controller.ProxyController - HTTP 504 Gateway Timeout returned",
                    log_snippet,
                    f"[{timestamp}] WARN  io.github.resilience4j.circuitbreaker - Circuit breaker state changed to OPEN"
                ]
                hist = [round(random.uniform(0.0, 0.4), 2) for _ in range(15)]
                current_val = err_val
                monitored_metric = "ERROR_RATE"
                unit = "PERCENT"

        sample = {
            "observation_id": idx + 1,
            "timestamp": timestamp,
            "cluster": svc["cluster"],
            "service_id": svc["id"],
            "service_name": svc["name"],
            "label": label,  # 0: Normal, 1: Error
            "status": "ERROR" if is_error else "NORMAL",
            "ground_truth_category": cat,
            "incident_title": incident_title,
            "monitored_metric": monitored_metric,
            "current_value": current_val,
            "unit": unit,
            "historical_baseline": hist,
            "recent_metrics": [
                {"metricName": "CPU_USAGE", "metricValue": cpu_val, "unit": "PERCENT"},
                {"metricName": "MEMORY_USAGE", "metricValue": mem_val, "unit": "PERCENT"},
                {"metricName": "RESPONSE_TIME", "metricValue": lat_val, "unit": "MILLISECONDS"},
                {"metricName": "ERROR_RATE", "metricValue": err_val, "unit": "PERCENT"}
            ],
            "logs": logs
        }
        dataset.append(sample)

        csv_rows.append({
            "observation_id": idx + 1,
            "timestamp": timestamp,
            "cluster": svc["cluster"],
            "service_name": svc["name"],
            "label": label,
            "status": "ERROR" if is_error else "NORMAL",
            "ground_truth_category": cat,
            "cpu_usage": cpu_val,
            "memory_usage": mem_val,
            "response_time_ms": lat_val,
            "error_rate_pct": err_val,
            "log_snippet": log_snippet
        })

    # Save JSON
    with open("benchmark/synthetic_microservice_dataset.json", "w", encoding="utf-8") as f:
        json.dump(dataset, f, indent=2)

    # Save CSV
    fieldnames = [
        "observation_id", "timestamp", "cluster", "service_name",
        "label", "status", "ground_truth_category", "cpu_usage",
        "memory_usage", "response_time_ms", "error_rate_pct", "log_snippet"
    ]
    with open("benchmark/synthetic_microservice_dataset.csv", "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(csv_rows)

    normals = sum(1 for d in dataset if d["status"] == "NORMAL")
    errors = sum(1 for d in dataset if d["status"] == "ERROR")

    print(f"Generated EXACT Research Paper Dataset:")
    print(f"  Total Observations  : {len(dataset)}")
    print(f"  Normal Observations : {normals}")
    print(f"  Error Observations  : {errors}")
    print("Files created: benchmark/synthetic_microservice_dataset.json and benchmark/synthetic_microservice_dataset.csv")

if __name__ == "__main__":
    generate_exact_paper_dataset()
