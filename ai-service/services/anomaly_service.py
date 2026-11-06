import math
from typing import List, Dict, Any, Optional
import numpy as np
from sklearn.ensemble import IsolationForest


class AnomalyService:
    def __init__(self, contamination: float = 0.05):
        self.contamination = contamination

    def detect(
        self,
        metric_name: str,
        current_value: float,
        unit: str,
        historical_values: List[float]
    ) -> Dict[str, Any]:
        if current_value is None or math.isnan(current_value) or math.isinf(current_value):
            raise ValueError("Current metric value must be a valid finite number")

        valid_history = [v for v in historical_values if v is not None and not math.isnan(v) and not math.isinf(v)]
        if len(valid_history) < 10:
            return {
                "is_anomaly": False,
                "anomaly_score": 0.0,
                "baseline_mean": 0.0,
                "baseline_std_dev": 0.0,
                "severity": None,
                "direction": None,
                "detector_used": "SKIPPED",
                "message": f"Insufficient historical observations: {len(valid_history)} found, minimum 10 required"
            }

        # Bounded historical window (up to 30 most recent)
        window = valid_history[-30:]
        n = len(window)
        mean = float(np.mean(window))
        sample_std = float(np.std(window, ddof=1)) if n > 1 else 0.0

        # Variance floor policy
        floor = self._resolve_variance_floor(metric_name, unit)
        effective_std = max(sample_std, floor)

        # 1. Parametric Statistical Z-Score
        z_score = (current_value - mean) / effective_std
        abs_z = abs(z_score)

        # 2. Unsupervised ML: Isolation Forest
        X_train = np.array(window).reshape(-1, 1)
        # Use a deterministic random state for reproducibility
        iso_forest = IsolationForest(contamination=self.contamination, random_state=42)
        iso_forest.fit(X_train)
        
        # decision_function gives anomaly score (lower means more anomalous)
        iso_score = float(iso_forest.decision_function([[current_value]])[0])
        iso_prediction = int(iso_forest.predict([[current_value]])[0]) # -1 for anomaly, 1 for normal

        is_anomaly = (abs_z >= 3.0) or (iso_prediction == -1 and abs_z >= 2.0)

        if not is_anomaly:
            return {
                "is_anomaly": False,
                "anomaly_score": round(abs_z, 2),
                "baseline_mean": round(mean, 2),
                "baseline_std_dev": round(effective_std, 2),
                "z_score": round(z_score, 2),
                "isolation_forest_score": round(iso_score, 4),
                "severity": None,
                "direction": None,
                "detector_used": "HYBRID_STATISTICAL_ISOLATION_FOREST",
                "message": "Normal observation within expected operational baseline"
            }

        # Severity resolution
        if abs_z >= 5.0 or iso_score < -0.15:
            severity = "CRITICAL"
        elif abs_z >= 4.0:
            severity = "HIGH"
        else:
            severity = "MEDIUM"

        direction = "POSITIVE_SPIKE" if z_score >= 0 else "NEGATIVE_DROP"
        message = (
            f"Hybrid anomaly detected: observed {current_value:.2f} {unit} deviates from "
            f"baseline mean {mean:.2f} (|Z|={abs_z:.2f}, IsoForestScore={iso_score:.3f}, "
            f"severity={severity}, direction={direction})"
        )

        return {
            "is_anomaly": True,
            "anomaly_score": round(abs_z, 2),
            "baseline_mean": round(mean, 2),
            "baseline_std_dev": round(effective_std, 2),
            "z_score": round(z_score, 2),
            "isolation_forest_score": round(iso_score, 4),
            "severity": severity,
            "direction": direction,
            "detector_used": "HYBRID_STATISTICAL_ISOLATION_FOREST",
            "message": message
        }

    def _resolve_variance_floor(self, metric_name: str, unit: str) -> float:
        norm_metric = (metric_name or "").strip().upper()
        norm_unit = (unit or "").strip().upper()

        if norm_metric == "ERROR_RATE" and norm_unit == "PERCENT":
            return 0.1

        match norm_unit:
            case "PERCENT":
                return 1.0
            case "MILLISECONDS":
                return 5.0
            case "MEGABYTES":
                return 10.0
            case _:
                return 1.0
