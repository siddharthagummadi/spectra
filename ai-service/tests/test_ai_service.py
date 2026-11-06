import unittest
from fastapi.testclient import TestClient
import os
import sys

# Ensure ai-service is in sys.path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from main import app
from services.anomaly_service import AnomalyService
from services.rca_service import RcaService
from services.prediction_service import PredictionService
from services.rag_service import RagService
from services.recommendation_service import RecommendationService
from services.copilot_service import CopilotService


class TestAiService(unittest.TestCase):

    def setUp(self):
        self.client = TestClient(app)
        self.anomaly_svc = AnomalyService()
        self.rca_svc = RcaService()
        self.prediction_svc = PredictionService()
        self.rag_svc = RagService()
        self.recommendation_svc = RecommendationService()
        self.copilot_svc = CopilotService(rag_service=self.rag_svc)

    def test_health_endpoint(self):
        response = self.client.get("/health")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["status"], "UP")
        self.assertIn("anomaly_detection", data["capabilities"])

    # --- Anomaly Service Tests ---
    def test_anomaly_detection_normal(self):
        history = [50.0] * 15
        res = self.anomaly_svc.detect("CPU_USAGE", 51.0, "PERCENT", history)
        self.assertFalse(res["is_anomaly"])
        self.assertEqual(res["baseline_mean"], 50.0)

    def test_anomaly_detection_critical_spike(self):
        history = [50.0] * 15
        res = self.anomaly_svc.detect("CPU_USAGE", 95.0, "PERCENT", history)
        self.assertTrue(res["is_anomaly"])
        self.assertEqual(res["direction"], "POSITIVE_SPIKE")
        self.assertIn(res["severity"], ["HIGH", "CRITICAL"])
        self.assertGreaterEqual(res["anomaly_score"], 3.0)

    def test_anomaly_detection_insufficient_history(self):
        history = [50.0] * 5
        res = self.anomaly_svc.detect("CPU_USAGE", 95.0, "PERCENT", history)
        self.assertFalse(res["is_anomaly"])
        self.assertEqual(res["detector_used"], "SKIPPED")

    # --- RCA Service Tests ---
    def test_rca_service_memory_leak(self):
        metrics = [{"metricName": "MEMORY_USAGE", "metricValue": 92.0}]
        logs = ["java.lang.OutOfMemoryError: Java heap space", "GC overhead limit exceeded"]
        res = self.rca_svc.analyze_incident(
            service_id=1,
            service_name="OrderService",
            incident_title="Memory exhaustion",
            recent_metrics=metrics,
            logs=logs
        )
        self.assertEqual(res["failure_category"], "MEMORY_LEAK")
        self.assertGreaterEqual(res["confidence_score"], 0.85)

    def test_rca_service_db_starvation(self):
        metrics = [{"metricName": "RESPONSE_TIME", "metricValue": 3500.0}]
        logs = ["HikariCP - Connection is not available, request timed out after 30000ms"]
        res = self.rca_svc.analyze_incident(
            service_id=2,
            service_name="BillingService",
            incident_title="Database timeouts",
            recent_metrics=metrics,
            logs=logs
        )
        self.assertEqual(res["failure_category"], "DATABASE_CONTENTION")
        self.assertGreaterEqual(res["confidence_score"], 0.80)

    # --- Prediction Service Tests ---
    def test_prediction_forecasting_rapid_climb(self):
        values = [50.0, 55.0, 60.0, 65.0, 70.0, 75.0]
        res = self.prediction_svc.forecast_trajectory(
            metric_name="CPU_USAGE",
            unit="PERCENT",
            values=values,
            critical_threshold=90.0
        )
        self.assertEqual(res["status"], "SUCCESS")
        self.assertIsNotNone(res["time_to_breach_minutes"])
        self.assertGreater(res["slope_per_minute"], 0.0)
        self.assertGreaterEqual(res["outage_risk_probability"], 0.5)

    def test_prediction_forecasting_stable(self):
        values = [50.0, 49.8, 50.2, 50.0, 49.9]
        res = self.prediction_svc.forecast_trajectory(
            metric_name="CPU_USAGE",
            unit="PERCENT",
            values=values,
            critical_threshold=90.0
        )
        self.assertEqual(res["status"], "SUCCESS")
        self.assertEqual(res["outage_risk_probability"], 0.05)

    # --- RAG Service Tests ---
    def test_rag_query_retrieval(self):
        matches = self.rag_svc.query_runbooks("High CPU thread saturation and runaway loop", top_k=2)
        self.assertGreater(len(matches), 0)
        top = matches[0]
        self.assertEqual(top["runbook_id"], "RB-001")
        self.assertIn("jstack", " ".join(top["immediate_actions"]))

    def test_rag_query_db_contention(self):
        matches = self.rag_svc.query_runbooks("Hikari pool exhaustion slow database query timeout", top_k=2)
        self.assertGreater(len(matches), 0)
        top = matches[0]
        self.assertEqual(top["runbook_id"], "RB-003")

    # --- Recommendation Service Tests ---
    def test_recommendations_generation(self):
        rca = {
            "primary_root_cause": "JVM Heap Exhaustion / Memory Leak",
            "confidence_score": 0.90,
            "failure_category": "MEMORY_LEAK",
            "primary_evidence": "Heap dump indicated unbounded static cache growth",
            "service_name": "AuthService"
        }
        runbooks = [{
            "runbook_id": "RB-002",
            "title": "Memory Leak Mitigation",
            "immediate_actions": ["Trigger JVM heap dump", "Restart instance", "Divert 50% traffic"],
            "long_term_fix": "Analyze heap dump with Eclipse MAT"
        }]
        res = self.recommendation_svc.generate_recommendations({}, rca, runbooks)
        self.assertEqual(res["primary_root_cause"], "JVM Heap Exhaustion / Memory Leak")
        self.assertEqual(len(res["action_plan"]), 3)
        self.assertEqual(res["action_plan"][0]["priority"], "P0_IMMEDIATE")

    # --- Copilot Service Tests ---
    def test_copilot_deterministic_status_reply(self):
        context = {
            "services": [{"id": 1, "name": "Payment"}],
            "alerts": [{"id": 10, "status": "TRIGGERED"}],
            "incidents": []
        }
        res = self.copilot_svc.chat("What is the current health status?", operational_context=context)
        self.assertEqual(res["status"], "SUCCESS")
        self.assertIn("Spectra Platform Health Summary", res["reply"])
        self.assertIn("DEGRADED", res["reply"])

    # --- FastAPI Endpoint Integration Tests ---
    def test_api_anomaly_detect_endpoint(self):
        payload = {
            "metric_name": "CPU_USAGE",
            "current_value": 96.0,
            "unit": "PERCENT",
            "historical_values": [50.0] * 12
        }
        res = self.client.post("/api/v1/anomaly/detect", json=payload)
        self.assertEqual(res.status_code, 200)
        self.assertTrue(res.json()["is_anomaly"])

    def test_api_rag_query_endpoint(self):
        payload = {
            "query": "Java heap space OutOfMemoryError",
            "top_k": 2
        }
        res = self.client.post("/api/v1/rag/query", json=payload)
        self.assertEqual(res.status_code, 200)
        data = res.json()
        self.assertGreaterEqual(data["results_count"], 1)

    def test_api_rca_endpoint(self):
        payload = {
            "service_id": 1,
            "service_name": "SearchService",
            "incident_title": "Slow queries and thread exhaustion",
            "recent_metrics": [{"metricName": "RESPONSE_TIME", "metricValue": 4000.0}],
            "logs": ["HikariCP connection timeout"]
        }
        res = self.client.post("/api/v1/rca/analyze", json=payload)
        self.assertEqual(res.status_code, 200)
        self.assertEqual(res.json()["failure_category"], "DATABASE_CONTENTION")

    def test_api_prediction_endpoint(self):
        payload = {
            "metric_name": "MEMORY_USAGE",
            "unit": "PERCENT",
            "values": [60.0, 65.0, 70.0, 75.0, 80.0]
        }
        res = self.client.post("/api/v1/prediction/forecast", json=payload)
        self.assertEqual(res.status_code, 200)
        self.assertEqual(res.json()["status"], "SUCCESS")

    def test_api_copilot_endpoint(self):
        payload = {
            "message": "How do I mitigate high CPU usage?"
        }
        res = self.client.post("/api/v1/copilot/chat", json=payload)
        self.assertEqual(res.status_code, 200)
        self.assertEqual(res.json()["status"], "SUCCESS")
        self.assertIn("RB-001", res.json()["referenced_runbooks"])


if __name__ == "__main__":
    unittest.main()
