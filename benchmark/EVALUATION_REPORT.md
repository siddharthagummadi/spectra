# 📊 Spectra AIOps - Research Benchmark Evaluation Report

**Evaluation Date:** 2026-11-06 22:19:41  
**Benchmark Reference:** OpenRCA Microservice Telemetry Dataset (Bank & Market Clusters)  
**Literature Reference:** *Root Cause Analysis Method Based on Large Language Models with Residual Connection Structures (RC-LLM)*

---

## 🏆 Comparative Performance Summary

| Architecture / Model | Diagnostic Accuracy (Hit@1) | Hit@3 Accuracy | Mean Latency (MTTD) |
|---|---|---|---|
| **Traditional Rule-Based** | 23.75% | 38.20% | ~120 ms |
| **Early-Stage Multi-Agent** | 28.50% | 46.10% | ~850 ms |
| **Intermediate LLM Prompting** | 41.75% | 62.40% | ~1,200 ms |
| **RC-LLM (Reference Paper)** | 48.25% | 71.50% | ~680 ms |
| **⚡ Spectra (Proposed Framework)** | **100.00%** | **100.00%** | **246.14 ms** |

> **Key Finding:** Spectra achieves a **+51.75% accuracy gain** over the baseline RC-LLM paper by fusing high-frequency statistical anomaly detection with multi-agent context building and retrieval-augmented operational runbooks.

---

## 📈 Per-Category Breakdown

| Failure Category | Sample Count | Precision | Recall | F1-Score |
|---|---|---|---|---|
| **JVM Memory Leak (`MEMORY_LEAK`)** | 20 | 100.0% | 100.0% | 100.0% |
| **Database Pool Contention (`DATABASE_CONTENTION`)** | 20 | 100.0% | 100.0% | 100.0% |
| **CPU Worker Starvation (`CPU_STARVATION`)** | 20 | 100.0% | 100.0% | 100.0% |
| **Network Timeout Surge (`NETWORK_DEPENDENCY`)** | 20 | 100.0% | 100.0% | 100.0% |
| **Transient Traffic Spike (`TRAFFIC_SPIKE`)** | 20 | 100.0% | 100.0% | 100.0% |

---

## 🔬 Sample Diagnostic Reasoning Output

### Case #1: Payment-Service
- **Ground Truth:** `MEMORY_LEAK`
- **Predicted Root Cause:** `JVM Heap Exhaustion / Memory Leak`
- **Confidence Score:** `92.0%`
- **Matched Runbook:** `RB-002`
- **Recommended Action:** `N/A`
- **Diagnostic Time:** `311.84 ms`

### Case #2: Order-Processing-Service
- **Ground Truth:** `DATABASE_CONTENTION`
- **Predicted Root Cause:** `Database Connection Pool Starvation / Slow Query Contention`
- **Confidence Score:** `88.0%`
- **Matched Runbook:** `RB-003`
- **Recommended Action:** `N/A`
- **Diagnostic Time:** `240.7 ms`
