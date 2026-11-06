import json
import csv
import random
from datetime import datetime, timedelta

def generate_synthetic_benchmark():
    random.seed(42)
    categories = [
        "MEMORY_LEAK",
        "DATABASE_CONTENTION",
        "CPU_STARVATION",
        "NETWORK_DEPENDENCY",
        "TRAFFIC_SPIKE"
    ]
    
    services = [
        {"id": 1, "name": "Payment-Service", "cluster": "Bank"},
        {"id": 2, "name": "Order-Processing-Service", "cluster": "Market"},
        {"id": 3, "name": "Auth-Identity-Service", "cluster": "Security"},
        {"id": 4, "name": "Database-Gateway", "cluster": "Persistence"}
    ]

    dataset = []
    csv_rows = []
    
    start_time = datetime(2026, 11, 1, 10, 0, 0)
    
    case_id = 1
    for cat in categories:
        for i in range(20):
            svc = random.choice(services)
            t = start_time + timedelta(minutes=case_id * 15)
            t_str = t.isoformat()
            
            if cat == "MEMORY_LEAK":
                title = f"{svc['name']} Heap Memory Exhaustion & OOM Hazard"
                mem_val = round(random.uniform(86.0, 98.5), 2)
                cpu_val = round(random.uniform(35.0, 65.0), 2)
                lat_val = round(random.uniform(350.0, 900.0), 2)
                err_val = round(random.uniform(0.5, 2.5), 2)
                logs = [
                    f"[{t_str}] WARN  c.s.cache.MemoryStore - Eviction threshold exceeded: cache footprint 92%",
                    f"[{t_str}] ERROR c.s.service.TransactionManager - java.lang.OutOfMemoryError: Java heap space",
                    f"[{t_str}] WARN  org.apache.catalina.core.StandardWrapperValve - Major GC pause duration 4120ms"
                ]
                hist = [round(random.uniform(55.0, 68.0), 1) for _ in range(15)]
                curr = mem_val
                metric_name = "MEMORY_USAGE"
                unit = "PERCENT"
                
            elif cat == "DATABASE_CONTENTION":
                title = f"{svc['name']} Connection Pool Starvation & Query Latency Surge"
                mem_val = round(random.uniform(40.0, 65.0), 2)
                cpu_val = round(random.uniform(25.0, 55.0), 2)
                lat_val = round(random.uniform(2200.0, 6500.0), 2)
                err_val = round(random.uniform(1.0, 4.0), 2)
                logs = [
                    f"[{t_str}] WARN  com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - Connection is not available, request timed out after 5000ms",
                    f"[{t_str}] ERROR org.hibernate.engine.jdbc.spi.SqlExceptionHelper - Connection leak suspected: active connections maxed at 50/50",
                    f"[{t_str}] WARN  c.s.repo.OrderRepository - Slow SQL query execution time: {lat_val}ms"
                ]
                hist = [round(random.uniform(80.0, 220.0), 1) for _ in range(15)]
                curr = lat_val
                metric_name = "RESPONSE_TIME"
                unit = "MILLISECONDS"
                
            elif cat == "CPU_STARVATION":
                title = f"{svc['name']} CPU Saturation & Worker Thread Starvation"
                mem_val = round(random.uniform(45.0, 70.0), 2)
                cpu_val = round(random.uniform(88.0, 99.8), 2)
                lat_val = round(random.uniform(2500.0, 5200.0), 2)
                err_val = round(random.uniform(2.0, 6.0), 2)
                logs = [
                    f"[{t_str}] WARN  c.s.engine.CalculationWorker - Worker pool saturation: active threads 256/256",
                    f"[{t_str}] ERROR org.apache.tomcat.util.net.NioEndpoint - Thread starvation observed under sudden burst throughput",
                    f"[{t_str}] WARN  c.s.health.ComputeHealthCheck - Sustained core utilization at {cpu_val}%"
                ]
                hist = [round(random.uniform(40.0, 60.0), 1) for _ in range(15)]
                curr = cpu_val
                metric_name = "CPU_USAGE"
                unit = "PERCENT"
                
            elif cat == "NETWORK_DEPENDENCY":
                title = f"{svc['name']} Downstream Network Timeout & Error Surge"
                mem_val = round(random.uniform(35.0, 60.0), 2)
                cpu_val = round(random.uniform(30.0, 55.0), 2)
                lat_val = round(random.uniform(1200.0, 3500.0), 2)
                err_val = round(random.uniform(8.5, 32.0), 2)
                logs = [
                    f"[{t_str}] ERROR c.s.client.DownstreamGateway - java.net.SocketTimeoutException: Read timed out to remote gateway",
                    f"[{t_str}] WARN  io.github.resilience4j.circuitbreaker - Circuit breaker state changed from CLOSED to OPEN",
                    f"[{t_str}] ERROR c.s.controller.ProxyController - HTTP 504 Gateway Timeout returned to upstream caller"
                ]
                hist = [round(random.uniform(0.1, 1.2), 2) for _ in range(15)]
                curr = err_val
                metric_name = "ERROR_RATE"
                unit = "PERCENT"
                
            else: # TRAFFIC_SPIKE
                title = f"{svc['name']} Benign Traffic Influx / Transient Load"
                mem_val = round(random.uniform(50.0, 75.0), 2)
                cpu_val = round(random.uniform(55.0, 78.0), 2)
                lat_val = round(random.uniform(400.0, 950.0), 2)
                err_val = round(random.uniform(0.1, 1.5), 2)
                logs = [
                    f"[{t_str}] INFO  c.s.ingress.TrafficRouter - Inbound request rate elevated: 1200 req/s",
                    f"[{t_str}] INFO  c.s.service.RateLimiter - Dynamic throttling active, queue processing normally",
                    f"[{t_str}] INFO  c.s.health.ClusterHealth - Transient load managed within SLA limits"
                ]
                hist = [round(random.uniform(45.0, 65.0), 1) for _ in range(15)]
                curr = cpu_val
                metric_name = "CPU_USAGE"
                unit = "PERCENT"
                
            metrics_payload = [
                {"metricName": "CPU_USAGE", "metricValue": cpu_val, "unit": "PERCENT"},
                {"metricName": "MEMORY_USAGE", "metricValue": mem_val, "unit": "PERCENT"},
                {"metricName": "RESPONSE_TIME", "metricValue": lat_val, "unit": "MILLISECONDS"},
                {"metricName": "ERROR_RATE", "metricValue": err_val, "unit": "PERCENT"}
            ]
            
            sample = {
                "case_id": case_id,
                "timestamp": t_str,
                "cluster": svc["cluster"],
                "service_id": svc["id"],
                "service_name": svc["name"],
                "ground_truth_category": cat,
                "incident_title": title,
                "monitored_metric": metric_name,
                "current_value": curr,
                "unit": unit,
                "historical_baseline": hist,
                "recent_metrics": metrics_payload,
                "logs": logs
            }
            dataset.append(sample)
            
            csv_rows.append({
                "case_id": case_id,
                "timestamp": t_str,
                "cluster": svc["cluster"],
                "service_name": svc["name"],
                "ground_truth_category": cat,
                "incident_title": title,
                "cpu_usage": cpu_val,
                "memory_usage": mem_val,
                "response_time_ms": lat_val,
                "error_rate_pct": err_val,
                "log_snippet": logs[1]
            })
            case_id += 1

    # Save JSON dataset
    with open("benchmark/synthetic_microservice_dataset.json", "w", encoding="utf-8") as f:
        json.dump(dataset, f, indent=2)

    # Save CSV dataset
    fieldnames = [
        "case_id", "timestamp", "cluster", "service_name",
        "ground_truth_category", "incident_title", "cpu_usage",
        "memory_usage", "response_time_ms", "error_rate_pct", "log_snippet"
    ]
    with open("benchmark/synthetic_microservice_dataset.csv", "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(csv_rows)
        
    print(f"Generated {len(dataset)} synthetic benchmark cases across 5 failure categories.")
    print("Files created: benchmark/synthetic_microservice_dataset.json and benchmark/synthetic_microservice_dataset.csv")

if __name__ == "__main__":
    generate_synthetic_benchmark()
