from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any


# Anomaly Schemas
class AnomalyDetectionRequest(BaseModel):
    metric_name: str = Field(..., example="CPU_USAGE")
    current_value: float = Field(..., example=95.0)
    unit: str = Field(..., example="PERCENT")
    historical_values: List[float] = Field(..., example=[50.0, 52.0, 49.0, 51.0, 50.5, 49.8, 51.2, 50.1, 49.9, 50.2])


class AnomalyDetectionResponse(BaseModel):
    is_anomaly: bool
    anomaly_score: float
    baseline_mean: float
    baseline_std_dev: float
    z_score: Optional[float] = None
    isolation_forest_score: Optional[float] = None
    severity: Optional[str] = None
    direction: Optional[str] = None
    detector_used: str
    message: str


# RCA Schemas
class RcaRequest(BaseModel):
    service_id: int = Field(..., example=1)
    service_name: str = Field(..., example="Payment Gateway")
    incident_title: str = Field(..., example="High latency and timeout surge")
    incident_description: Optional[str] = None
    recent_metrics: Optional[List[Dict[str, Any]]] = None
    active_alerts: Optional[List[Dict[str, Any]]] = None
    recent_anomalies: Optional[List[Dict[str, Any]]] = None
    logs: Optional[List[str]] = None


class RcaResponse(BaseModel):
    service_id: int
    service_name: str
    incident_title: str
    primary_root_cause: str
    confidence_score: float
    failure_category: str
    primary_evidence: str
    ranked_causes: List[Dict[str, Any]]
    contributing_factors: List[str]


# Prediction Schemas
class PredictionRequest(BaseModel):
    metric_name: str = Field(..., example="MEMORY_USAGE")
    unit: str = Field(..., example="PERCENT")
    values: List[float] = Field(..., example=[65.0, 68.0, 71.0, 74.0, 78.0, 82.0])
    timestamps_seconds: Optional[List[float]] = None
    critical_threshold: Optional[float] = Field(None, example=90.0)


class ProjectedPoint(BaseModel):
    in_minutes: int
    projected_value: float


class PredictionResponse(BaseModel):
    status: str
    metric_name: Optional[str] = None
    unit: Optional[str] = None
    current_value: Optional[float] = None
    critical_threshold: Optional[float] = None
    slope_per_minute: Optional[float] = None
    trend_direction: Optional[str] = None
    time_to_breach_minutes: Optional[float] = None
    outage_risk_probability: Optional[float] = None
    projected_values: Optional[List[ProjectedPoint]] = None
    proactive_recommendation: Optional[str] = None
    message: Optional[str] = None


# RAG Schemas
class RagQueryRequest(BaseModel):
    query: str = Field(..., example="How do I mitigate Java OutOfMemoryError in production?")
    top_k: int = Field(3, example=3)
    metric_filter: Optional[str] = None


class RunbookMatch(BaseModel):
    score: float
    runbook_id: str
    title: str
    metric_trigger: str
    service_type: str
    symptoms: List[str]
    root_causes: List[str]
    immediate_actions: List[str]
    long_term_fix: str


class RagQueryResponse(BaseModel):
    query: str
    results_count: int
    matches: List[RunbookMatch]


# Recommendations Schemas
class RecommendationRequest(BaseModel):
    incident_context: Dict[str, Any]
    rca_result: Dict[str, Any]
    matched_runbooks: List[Dict[str, Any]]
    prediction_result: Optional[Dict[str, Any]] = None


class RecommendationResponse(BaseModel):
    service_name: str
    primary_root_cause: str
    confidence_score: float
    failure_category: str
    explainable_rationale: str
    action_plan: List[Dict[str, Any]]
    risk_of_inaction: str
    matched_runbook_id: Optional[str] = None
    matched_runbook_title: Optional[str] = None


# Copilot Schemas
class CopilotChatRequest(BaseModel):
    message: str = Field(..., example="What is the operational health of our payment service?")
    history: Optional[List[Dict[str, str]]] = None
    operational_context: Optional[Dict[str, Any]] = None


class CopilotChatResponse(BaseModel):
    reply: str
    engine_used: str
    referenced_runbooks: List[str]
    status: str
