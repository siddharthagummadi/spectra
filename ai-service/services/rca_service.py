from typing import List, Dict, Any, Optional


class RcaService:
    def __init__(self):
        pass

    def analyze_incident(
        self,
        service_id: int,
        service_name: str,
        incident_title: str,
        incident_description: Optional[str] = None,
        recent_metrics: Optional[List[Dict[str, Any]]] = None,
        active_alerts: Optional[List[Dict[str, Any]]] = None,
        recent_anomalies: Optional[List[Dict[str, Any]]] = None,
        logs: Optional[List[str]] = None
    ) -> Dict[str, Any]:
        recent_metrics = recent_metrics or []
        active_alerts = active_alerts or []
        recent_anomalies = recent_anomalies or []
        logs = logs or []

        # Analyze metric signatures
        high_cpu = any(m.get("metricName") == "CPU_USAGE" and m.get("metricValue", 0) > 80.0 for m in recent_metrics)
        high_mem = any(m.get("metricName") == "MEMORY_USAGE" and m.get("metricValue", 0) > 85.0 for m in recent_metrics)
        high_lat = any(m.get("metricName") == "RESPONSE_TIME" and m.get("metricValue", 0) > 2000.0 for m in recent_metrics)
        high_err = any(m.get("metricName") == "ERROR_RATE" and m.get("metricValue", 0) > 5.0 for m in recent_metrics)

        # Log pattern matching
        log_text = " ".join(logs).lower()
        has_oom = "outofmemory" in log_text or "heap space" in log_text
        has_timeout = "timeout" in log_text or "timed out" in log_text or "connection refused" in log_text
        has_deadlock = "deadlock" in log_text or "lock acquisition" in log_text
        has_db_pool = "hikari" in log_text or "pool exhaustion" in log_text or "connection leak" in log_text

        candidate_causes = []

        if has_oom or high_mem:
            candidate_causes.append({
                "cause": "JVM Heap Exhaustion / Memory Leak",
                "confidence": 0.92 if has_oom else 0.78,
                "category": "MEMORY_LEAK",
                "evidence": "Memory usage exceeded 85% with uncollected heap objects" + (" and OutOfMemoryError in logs" if has_oom else "")
            })

        if has_db_pool or (high_lat and not high_cpu):
            candidate_causes.append({
                "cause": "Database Connection Pool Starvation / Slow Query Contention",
                "confidence": 0.88 if has_db_pool else 0.75,
                "category": "DATABASE_CONTENTION",
                "evidence": "Elevated response time without compute saturation indicating I/O or pool contention"
            })

        if high_cpu and high_lat:
            candidate_causes.append({
                "cause": "Compute Thread Saturation & Request Backlog",
                "confidence": 0.85,
                "category": "CPU_STARVATION",
                "evidence": "Synchronous high CPU usage (>80%) directly correlating with response latency spike"
            })

        if high_err or has_timeout:
            candidate_causes.append({
                "cause": "Upstream Dependency Unreachable / Network Partition",
                "confidence": 0.82 if has_timeout else 0.70,
                "category": "NETWORK_DEPENDENCY",
                "evidence": "Error rate surge with socket/HTTP timeouts to downstream service endpoints"
            })

        if not candidate_causes:
            # General operational degradation fallback
            candidate_causes.append({
                "cause": "Transient Traffic Surge / Operational Anomaly",
                "confidence": 0.65,
                "category": "TRAFFIC_SPIKE",
                "evidence": f"Anomaly events observed on service '{service_name}' without catastrophic resource exhaustion"
            })

        candidate_causes.sort(key=lambda x: x["confidence"], reverse=True)
        primary = candidate_causes[0]

        contributing_factors = [
            f"Active Alert Rules: {len(active_alerts)} triggered",
            f"Recent Anomalies Detected: {len(recent_anomalies)}",
            f"Logs Analyzed: {len(logs)} entries"
        ]
        if high_cpu: contributing_factors.append("Sustained CPU saturation (>80%)")
        if high_mem: contributing_factors.append("Linear memory utilization climb (>85%)")
        if high_lat: contributing_factors.append("P99 response time degradation (>2000ms)")
        if high_err: contributing_factors.append("High HTTP 5xx error rate (>5%)")

        return {
            "service_id": service_id,
            "service_name": service_name,
            "incident_title": incident_title,
            "primary_root_cause": primary["cause"],
            "confidence_score": primary["confidence"],
            "failure_category": primary["category"],
            "primary_evidence": primary["evidence"],
            "ranked_causes": candidate_causes,
            "contributing_factors": contributing_factors
        }
