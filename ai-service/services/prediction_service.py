import math
from typing import List, Dict, Any, Optional
import numpy as np


class PredictionService:
    def __init__(self):
        pass

    def forecast_trajectory(
        self,
        metric_name: str,
        unit: str,
        values: List[float],
        timestamps_seconds: Optional[List[float]] = None,
        critical_threshold: Optional[float] = None
    ) -> Dict[str, Any]:
        valid_pairs = []
        for i, v in enumerate(values):
            if v is not None and not math.isnan(v) and not math.isinf(v):
                t = timestamps_seconds[i] if timestamps_seconds and i < len(timestamps_seconds) else float(i * 60)
                valid_pairs.append((t, v))

        if len(valid_pairs) < 3:
            return {
                "status": "INSUFFICIENT_DATA",
                "message": "At least 3 valid observations required for trajectory projection",
                "projected_values": [],
                "slope_per_minute": 0.0,
                "time_to_breach_minutes": None,
                "outage_risk_probability": 0.0
            }

        t_arr = np.array([p[0] for p in valid_pairs])
        v_arr = np.array([p[1] for p in valid_pairs])

        # Normalize timestamps to minutes from start
        t_min = (t_arr - t_arr[0]) / 60.0

        # Linear regression fit: v = slope * t_min + intercept
        slope, intercept = np.polyfit(t_min, v_arr, 1)

        current_val = float(v_arr[-1])
        default_threshold = critical_threshold
        if default_threshold is None:
            norm_metric = (metric_name or "").upper()
            if norm_metric in ["CPU_USAGE", "MEMORY_USAGE"]:
                default_threshold = 90.0
            elif norm_metric == "RESPONSE_TIME":
                default_threshold = 3000.0
            elif norm_metric == "ERROR_RATE":
                default_threshold = 5.0
            else:
                default_threshold = current_val * 1.5

        # Estimate time to breach
        time_to_breach = None
        risk_probability = 0.0

        if slope > 0 and current_val < default_threshold:
            time_to_breach = (default_threshold - current_val) / slope
            # Outage risk within next 30 minutes
            if time_to_breach <= 15:
                risk_probability = 0.95
            elif time_to_breach <= 30:
                risk_probability = 0.80
            elif time_to_breach <= 60:
                risk_probability = 0.55
            else:
                risk_probability = 0.25
        elif current_val >= default_threshold:
            time_to_breach = 0.0
            risk_probability = 0.99
        else:
            risk_probability = 0.05

        # Generate future 15, 30, 45, 60 minute projections
        current_time_offset = t_min[-1]
        projections = []
        for future_offset in [5, 10, 15, 30, 60]:
            projected = float(slope * (current_time_offset + future_offset) + intercept)
            # Bound realistic values (e.g. percentage 0-100)
            if unit.upper() == "PERCENT":
                projected = min(100.0, max(0.0, projected))
            projections.append({
                "in_minutes": future_offset,
                "projected_value": round(projected, 2)
            })

        return {
            "status": "SUCCESS",
            "metric_name": metric_name,
            "unit": unit,
            "current_value": round(current_val, 2),
            "critical_threshold": round(default_threshold, 2),
            "slope_per_minute": round(float(slope), 4),
            "trend_direction": "RAPIDLY_INCREASING" if slope > 0.5 else ("INCREASING" if slope > 0 else "STABLE_OR_DECREASING"),
            "time_to_breach_minutes": round(time_to_breach, 1) if time_to_breach is not None else None,
            "outage_risk_probability": round(risk_probability, 2),
            "projected_values": projections,
            "proactive_recommendation": (
                f"Proactive alert: {metric_name} is climbing at {slope:.2f} {unit}/min. "
                f"Projected breach of {default_threshold:.1f} in {time_to_breach:.1f} minutes. "
                "Initiate preemptive scaling or cache eviction."
                if time_to_breach and time_to_breach <= 30 else
                "Metric trajectory is healthy; no breach projected in the near term."
            )
        }
