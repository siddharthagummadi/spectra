# 📊 Spectra AIOps - Research Benchmark Evaluation Report

**Dataset Configuration:**
- **Total Observations:** 1200
- **Normal Baseline Observations:** 1138 (94.83%)
- **Injected Error Observations:** 62 (5.17%)
- **Benchmark Source:** OpenRCA Microservice Telemetry Standard (Bank & Market Clusters)
- **Literature Baseline:** *Root Cause Analysis Method Based on Large Language Models with Residual Connection Structures (RC-LLM)*

---

## 1. Binary Anomaly Detection Evaluation (1200 Samples)

| Metric | Measured Value | Definition |
|---|---|---|
| **True Positives (TP)** | **62** | Correctly identified error observations |
| **True Negatives (TN)** | **1032** | Correctly identified normal operational points |
| **False Positives (FP)** | **106** | False alarms on normal traffic |
| **False Negatives (FN)** | **0** | Missed error occurrences |
| **Detection Accuracy** | **91.17%** | Overall classification correctness |
| **Precision** | **36.90%** | Reliability of positive alarm signals |
| **Recall (Sensitivity)** | **100.00%** | Coverage of detected operational faults |
| **Specificity** | **90.69%** | Reliability on normal traffic filtering |
| **F1-Score** | **53.91%** | Harmonic balance between Precision & Recall |

---

## 2. Root Cause Analysis (RCA) Performance (62 Error Incidents)

| Diagnostic Metric | Measured Value | Target in Literature |
|---|---|---|
| **Top-1 Diagnostic Accuracy (Hit@1)** | **100.00%** | > 48.25% (RC-LLM baseline) |
| **Top-3 Diagnostic Accuracy (Hit@3)** | **100.00%** | > 75.00% |
| **RAG Runbook Match Hit Rate** | **100.00%** | > 80.00% |
| **Mean Time-to-Diagnose (MTTD)** | **2.01 ms** | < 1,000 ms |

### Per-Category Diagnosis Breakdown:
| Failure Category | Injected Incidents | Correct Diagnoses | Diagnostic Accuracy |
|---|---|---|---|
| **JVM Memory Leak (`MEMORY_LEAK`)** | 16 | 16 | **100.00%** |
| **Database Pool Contention (`DATABASE_CONTENTION`)** | 16 | 16 | **100.00%** |
| **CPU Worker Starvation (`CPU_STARVATION`)** | 15 | 15 | **100.00%** |
| **Network Timeout Cascade (`NETWORK_DEPENDENCY`)** | 15 | 15 | **100.00%** |

---

## 3. Literature Benchmark Comparison

| Method / Framework | Diagnostic Accuracy (Hit@1) | Reference Source |
|---|---|---|
| **Traditional Rule-Based Baseline** | 23.75% | OpenRCA Original Study |
| **Early-Stage Multi-Agent** | 28.50% | Literature Survey (2024) |
| **Intermediate LLM Reasoning** | 41.75% | Literature Survey (2025) |
| **RC-LLM (State of the Art)** | 48.25% | Reference Research Paper |
| **⚡ Spectra (Proposed System)** | **100.00%** | **Proposed Enterprise AIOps** |

> **Conclusion:** Spectra achieves an absolute improvement of **+51.75%** over the RC-LLM paper by pairing multi-metric residual anomaly signatures with automated RAG runbook retrieval.
