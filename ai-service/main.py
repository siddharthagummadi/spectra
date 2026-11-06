import os
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from schemas import (
    AnomalyDetectionRequest, AnomalyDetectionResponse,
    RcaRequest, RcaResponse,
    PredictionRequest, PredictionResponse,
    RagQueryRequest, RagQueryResponse,
    RecommendationRequest, RecommendationResponse,
    CopilotChatRequest, CopilotChatResponse
)
from services.anomaly_service import AnomalyService
from services.rca_service import RcaService
from services.prediction_service import PredictionService
from services.rag_service import RagService
from services.recommendation_service import RecommendationService
from services.copilot_service import CopilotService

app = FastAPI(
    title="Spectra AI Operations Intelligence Service",
    description="Microservice providing ML Anomaly Detection, Root Cause Analysis, Predictive Operations, RAG Runbooks, and AI Copilot.",
    version="1.0.0"
)

# Enable CORS for local Spring Boot backend and React frontend
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize singletons
anomaly_svc = AnomalyService()
rca_svc = RcaService()
prediction_svc = PredictionService()
rag_svc = RagService()
recommendation_svc = RecommendationService()
copilot_svc = CopilotService(
    rag_service=rag_svc,
    rca_service=rca_svc,
    prediction_service=prediction_svc
)


@app.get("/health")
def health_check():
    return {
        "status": "UP",
        "service": "spectra-ai-service",
        "version": "1.0.0",
        "capabilities": [
            "anomaly_detection",
            "root_cause_analysis",
            "predictive_forecasting",
            "rag_knowledge_base",
            "explainable_recommendations",
            "copilot_chat"
        ]
    }


@app.post("/api/v1/anomaly/detect", response_model=AnomalyDetectionResponse)
def detect_anomaly(req: AnomalyDetectionRequest):
    try:
        res = anomaly_svc.detect(
            metric_name=req.metric_name,
            current_value=req.current_value,
            unit=req.unit,
            historical_values=req.historical_values
        )
        return res
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Anomaly detection failure: {str(e)}")


@app.post("/api/v1/rca/analyze", response_model=RcaResponse)
def analyze_root_cause(req: RcaRequest):
    try:
        res = rca_svc.analyze_incident(
            service_id=req.service_id,
            service_name=req.service_name,
            incident_title=req.incident_title,
            incident_description=req.incident_description,
            recent_metrics=req.recent_metrics,
            active_alerts=req.active_alerts,
            recent_anomalies=req.recent_anomalies,
            logs=req.logs
        )
        return res
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"RCA evaluation failure: {str(e)}")


@app.post("/api/v1/prediction/forecast", response_model=PredictionResponse)
def forecast_failure(req: PredictionRequest):
    try:
        res = prediction_svc.forecast_trajectory(
            metric_name=req.metric_name,
            unit=req.unit,
            values=req.values,
            timestamps_seconds=req.timestamps_seconds,
            critical_threshold=req.critical_threshold
        )
        return res
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Prediction forecasting failure: {str(e)}")


@app.post("/api/v1/rag/query", response_model=RagQueryResponse)
def query_runbooks(req: RagQueryRequest):
    try:
        matches = rag_svc.query_runbooks(
            query=req.query,
            top_k=req.top_k,
            metric_filter=req.metric_filter
        )
        return {
            "query": req.query,
            "results_count": len(matches),
            "matches": matches
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"RAG retrieval failure: {str(e)}")


@app.post("/api/v1/recommendations/explain", response_model=RecommendationResponse)
def generate_recommendations(req: RecommendationRequest):
    try:
        res = recommendation_svc.generate_recommendations(
            incident_context=req.incident_context,
            rca_result=req.rca_result,
            matched_runbooks=req.matched_runbooks,
            prediction_result=req.prediction_result
        )
        return res
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Recommendation engine failure: {str(e)}")


@app.post("/api/v1/copilot/chat", response_model=CopilotChatResponse)
def chat_copilot(req: CopilotChatRequest):
    try:
        res = copilot_svc.chat(
            user_message=req.message,
            history=req.history,
            operational_context=req.operational_context
        )
        return res
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Copilot engine failure: {str(e)}")


if __name__ == "__main__":
    import uvicorn
    port = int(os.getenv("PORT", 8081))
    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=False)
