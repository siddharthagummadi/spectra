import os
from typing import List, Dict, Any, Optional
import google.generativeai as genai


class CopilotService:
    def __init__(self, rag_service=None, rca_service=None, prediction_service=None):
        self.rag_service = rag_service
        self.rca_service = rca_service
        self.prediction_service = prediction_service
        self.api_key = os.getenv("GEMINI_API_KEY") or os.getenv("GOOGLE_API_KEY")
        self.model = None

        if self.api_key:
            try:
                genai.configure(api_key=self.api_key)
                self.model = genai.GenerativeModel("gemini-1.5-flash")
            except Exception as e:
                self.model = None

    def chat(
        self,
        user_message: str,
        history: Optional[List[Dict[str, str]]] = None,
        operational_context: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        history = history or []
        operational_context = operational_context or {}

        # 1. First, search knowledge base via RAG to enrich response
        runbooks = []
        if self.rag_service:
            runbooks = self.rag_service.query_runbooks(user_message, top_k=2)

        # 2. If Gemini is available, construct enriched system prompt and invoke
        if self.model:
            try:
                context_str = self._format_context(operational_context, runbooks)
                prompt = (
                    f"You are Spectra AI Copilot, an enterprise site reliability and operations intelligence assistant.\n"
                    f"Context Information:\n{context_str}\n\n"
                    f"Operator Query: {user_message}\n\n"
                    "Provide a concise, professional, and actionable operations response."
                )
                response = self.model.generate_content(prompt)
                return {
                    "reply": response.text,
                    "engine_used": "GEMINI_LLM",
                    "referenced_runbooks": [rb["runbook_id"] for rb in runbooks],
                    "status": "SUCCESS"
                }
            except Exception as ex:
                pass  # Fallback to deterministic engine on any API error

        # 3. Deterministic Operational Intent Resolution
        reply = self._deterministic_reply(user_message, operational_context, runbooks)
        return {
            "reply": reply,
            "engine_used": "SPECTRA_DETERMINISTIC_COPILOT",
            "referenced_runbooks": [rb["runbook_id"] for rb in runbooks],
            "status": "SUCCESS"
        }

    def _format_context(self, context: Dict[str, Any], runbooks: List[Dict[str, Any]]) -> str:
        lines = []
        if "services" in context:
            lines.append(f"Monitored Services: {len(context['services'])} services registered.")
        if "alerts" in context:
            lines.append(f"Active Alerts: {len(context['alerts'])} alerts triggered.")
        if "incidents" in context:
            lines.append(f"Open Incidents: {len(context['incidents'])} incidents pending resolution.")
        if runbooks:
            lines.append("Relevant Runbooks:")
            for rb in runbooks:
                lines.append(f"- [{rb['runbook_id']}] {rb['title']}: {'; '.join(rb['immediate_actions'][:2])}")
        return "\n".join(lines) if lines else "No live telemetry passed."

    def _deterministic_reply(self, message: str, context: Dict[str, Any], runbooks: List[Dict[str, Any]]) -> str:
        msg = message.lower()

        # Health/Status intent
        if "health" in msg or "status" in msg or "overview" in msg:
            services = context.get("services", [])
            alerts = context.get("alerts", [])
            incidents = context.get("incidents", [])
            return (
                f"### Spectra Platform Health Summary\n"
                f"- **Services Monitored:** {len(services)} total\n"
                f"- **Active Alerts:** {len(alerts)} currently triggered\n"
                f"- **Active Incidents:** {len(incidents)} open for investigation\n\n"
                f"Operational State: {'DEGRADED' if len(alerts) > 0 or len(incidents) > 0 else 'HEALTHY'}."
            )

        # Anomaly/Spike intent
        if "anomaly" in msg or "spike" in msg or "drop" in msg:
            return (
                "### Statistical Anomaly Detection Engine\n"
                "Spectra continuously evaluates incoming telemetry against a rolling 30-observation historical baseline "
                "using sample standard deviation (Bessel's correction) and unit-specific variance floors. "
                "Any metric deviating by |Z| >= 3.0 is flagged as an anomaly event and indexed for root cause investigation."
            )

        # Runbook/Remediation intent
        if "runbook" in msg or "how to fix" in msg or "mitigate" in msg or "remediation" in msg or runbooks:
            if runbooks:
                top = runbooks[0]
                actions = "\n".join([f"  1. {act}" for act in top.get("immediate_actions", [])])
                return (
                    f"### Recommended Playbook: {top['title']} ({top['runbook_id']})\n"
                    f"**Trigger Metric:** {top['metric_trigger']}\n"
                    f"**Immediate Containment Steps:**\n{actions}\n\n"
                    f"**Permanent Fix:** {top['long_term_fix']}"
                )

        # Default help
        return (
            "### Spectra AI Copilot\n"
            "I can assist you with:\n"
            "- **Root Cause Analysis:** Ask me to analyze an active incident or service failure.\n"
            "- **Telemetry Forecasting:** Inquire about metric trends and estimated time-to-breach.\n"
            "- **Operational Playbooks:** Ask for mitigation runbooks for high CPU, memory leaks, or DB latency.\n"
            "- **Platform Status:** Check current service health and triggered alerts."
        )
