from typing import Dict, Any, List, Optional


class RecommendationService:
    def __init__(self):
        pass

    def generate_recommendations(
        self,
        incident_context: Dict[str, Any],
        rca_result: Dict[str, Any],
        matched_runbooks: List[Dict[str, Any]],
        prediction_result: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        primary_cause = rca_result.get("primary_root_cause", "Operational Anomaly")
        confidence = rca_result.get("confidence_score", 0.70)
        category = rca_result.get("failure_category", "GENERAL")
        service_name = rca_result.get("service_name", "Monitored Service")

        # Extract top runbook actions
        top_rb = matched_runbooks[0] if matched_runbooks else {}
        immediate_actions = top_rb.get("immediate_actions", [
            "Inspect recent service deployments and configuration changes",
            "Review application logs for unhandled runtime exceptions",
            "Restart failing service pods or instances if health checks fail"
        ])
        long_term_fix = top_rb.get("long_term_fix", "Refactor resource utilization and configure automated scaling policies.")

        # Structure prioritized recommendations
        action_plan = []
        action_plan.append({
            "priority": "P0_IMMEDIATE",
            "title": "Contain Outage & Mitigate Customer Impact",
            "steps": immediate_actions[:2]
        })
        if len(immediate_actions) > 2:
            action_plan.append({
                "priority": "P1_STABILIZATION",
                "title": "Service Stabilization & Capacity Restoration",
                "steps": immediate_actions[2:]
            })
        action_plan.append({
            "priority": "P2_PERMANENT_FIX",
            "title": "Root Cause Remediation & Resiliency Hardening",
            "steps": [long_term_fix]
        })

        rationale = (
            f"Spectra AI identified '{primary_cause}' with {confidence * 100:.1f}% confidence. "
            f"Evidence: {rca_result.get('primary_evidence', 'Correlated telemetry anomalies')}. "
            f"The failure category '{category}' aligns with runbook {top_rb.get('runbook_id', 'RB-DEFAULT')} "
            f"('{top_rb.get('title', 'General Runbook')}')."
        )

        risk_of_inaction = (
            "Failure to execute immediate containment may result in cascading timeouts, "
            "degradation of dependent services, and violation of 99.9% uptime SLA."
        )

        return {
            "service_name": service_name,
            "primary_root_cause": primary_cause,
            "confidence_score": confidence,
            "failure_category": category,
            "explainable_rationale": rationale,
            "action_plan": action_plan,
            "risk_of_inaction": risk_of_inaction,
            "matched_runbook_id": top_rb.get("runbook_id"),
            "matched_runbook_title": top_rb.get("title")
        }
