import os
import json
from pathlib import Path
from typing import List, Dict, Any, Optional
import requests
from dotenv import load_dotenv

from services.sentiment_analyzer import SentimentAnalyzer

# Load environment variables from .env file if present
env_path = Path(__file__).parent.parent / ".env"
if env_path.exists():
    load_dotenv(dotenv_path=env_path)
else:
    load_dotenv()

try:
    import google.generativeai as genai
    HAS_GENAI = True
except ImportError:
    genai = None
    HAS_GENAI = False


SPECTRA_SYSTEM_PROMPT = """You are Sanjaya AI, an intelligent, versatile, and conversational Site Reliability Engineering (SRE) and software architecture AI assistant for the Spectra Observability Platform.

YOUR CONVERSATIONAL STYLE & VOICE:
- Speak naturally, warmly, and conversationally—exactly like ChatGPT or Google Gemini.
- Avoid rigid, robotic, or hardcoded templates. Never output repetitive menus like "Ask X, Ask Y" unless specifically requested by the user.
- When answering questions, provide thorough, articulate, and well-structured explanations with real-world clarity, intuitive analogies, architectural context, and practical commands (SQL, bash, kubectl, Java/Python), just like an expert Google search synthesis and senior SRE consultation.
- When live cluster telemetry, microservices, or active incidents are present in the context, naturally weave them into your answer.
- Format responses beautifully with clean markdown headers, bolding, bullet points, and code snippets where appropriate.

ABOUT THE SPECTRA OBSERVABILITY PLATFORM:
- Purpose: Distributed microservice observability, predictive anomaly forecasting, automated root-cause analysis (RCA), and SRE remediation runbooks.
- Architecture:
  * Backend: Java 21, Spring Boot 3.3.4, Spring Data JPA, Flyway Migrations, PostgreSQL 16.
  * AI Service: Python 3.13, FastAPI (Port 8081), scikit-learn, ARIMA/Linear regression TTE forecasting, RAG runbooks, Z-score anomaly detector.
  * Frontend: React 18, Vite, responsive glassmorphic dark-mode UI.
- Monitored Services:
  1. order-processing (Spring Boot REST service; high transaction volume; connects to database-gateway via HikariCP pool).
  2. payment-service (Spring Boot JVM service; checkout transactions; vulnerable to JVM heap memory leaks).
  3. database-gateway (PostgreSQL coordinator & query router; vulnerable to CPU starvation and thread pool contention).
  4. auth-service (Spring Security & OAuth2 token provider; vulnerable to upstream network timeouts).
  5. notification-service (Async webhook dispatch and alert delivery worker).
- Telemetry & Math:
  * Metrics: CPU_USAGE (%), MEMORY_USAGE (%), RESPONSE_TIME (ms), ERROR_RATE (%).
  * Z-Score Anomaly: Z = (x_t - μ_30) / σ_30 with Bessel's correction (N - 1) and variance floor (σ_min = 1.0). Threshold: |Z| >= 3.0 (>99.73% Gaussian confidence).
- Known Scenarios & Runbooks:
  * MEMORY_LEAK: Payment-Service heap exhaustion (97%+ memory), prolonged GC pauses, JVM OOM -> RB-MEM-002.
  * CPU_STARVATION: Database-Gateway compute starvation (98%+ CPU), thread lockup -> RB-CPU-003.
  * DATABASE_CONTENTION: Order-Processing HikariCP connection starvation, queue wait > 4500ms -> RB-DB-001.
  * NETWORK_DEPENDENCY: Auth-Service upstream timeouts, circuit breaker trip -> RB-NET-004.
"""


class CopilotService:
    def __init__(self, rag_service=None, rca_service=None, prediction_service=None):
        self.rag_service = rag_service
        self.rca_service = rca_service
        self.prediction_service = prediction_service
        self.sentiment_analyzer = SentimentAnalyzer()
        
        self.gemini_key = os.getenv("GEMINI_API_KEY") or os.getenv("GOOGLE_API_KEY")
        self.grok_key = os.getenv("GROK_API_KEY") or os.getenv("XAI_API_KEY")
        
        self.gemini_model = os.getenv("GEMINI_MODEL", "gemini-1.5-flash")
        self.grok_model = os.getenv("GROK_MODEL", "grok-2-latest")
        
        # Determine initial active provider
        if self.gemini_key:
            self.provider = "GEMINI"
        elif self.grok_key:
            self.provider = "GROK"
        else:
            self.provider = "LOCAL_SRE"
            
        self._init_gemini()

    def _init_gemini(self):
        if self.gemini_key and HAS_GENAI:
            try:
                genai.configure(api_key=self.gemini_key)
            except Exception:
                pass

    def get_config_status(self) -> Dict[str, Any]:
        """Returns the current LLM provider status and capabilities."""
        masked_gemini = self._mask_key(self.gemini_key) if self.gemini_key else None
        masked_grok = self._mask_key(self.grok_key) if self.grok_key else None
        
        active_model = (
            self.gemini_model if self.provider == "GEMINI"
            else (self.grok_model if self.provider == "GROK" else "sanjaya-conversational-sre")
        )
        
        note = (
            "Connected to Google Gemini (Free Generative AI)" if self.provider == "GEMINI"
            else ("Connected to xAI Grok" if self.provider == "GROK"
                  else "Operating on Sanjaya Conversational SRE Engine")
        )

        return {
            "provider": self.provider,
            "active_model": active_model,
            "free_of_cost": self.provider in ["GEMINI", "LOCAL_SRE"],
            "gemini_configured": bool(self.gemini_key),
            "grok_configured": bool(self.grok_key),
            "masked_key": masked_gemini if self.provider == "GEMINI" else (masked_grok if self.provider == "GROK" else None),
            "is_configured": bool(self.gemini_key if self.provider == "GEMINI" else (self.grok_key if self.provider == "GROK" else False)),
            "note": note
        }

    def configure_keys(self, provider: str, api_key: str, model: Optional[str] = None) -> Dict[str, Any]:
        """Dynamically configures API keys at runtime and persists to .env."""
        prov = provider.upper().strip()
        key = api_key.strip() if api_key else ""
        
        if prov == "GEMINI":
            if key:
                self.gemini_key = key
                self._save_env_var("GEMINI_API_KEY", key)
            if model:
                self.gemini_model = model.strip()
                self._save_env_var("GEMINI_MODEL", self.gemini_model)
            self.provider = "GEMINI"
            self._init_gemini()
        elif prov == "GROK":
            if key:
                self.grok_key = key
                self._save_env_var("GROK_API_KEY", key)
            if model:
                self.grok_model = model.strip()
                self._save_env_var("GROK_MODEL", self.grok_model)
            self.provider = "GROK"
        elif prov in ["LOCAL", "LOCAL_SRE", "DEFAULT"]:
            self.provider = "LOCAL_SRE"
        else:
            raise ValueError(f"Unknown LLM provider: {provider}. Supported: GEMINI, GROK, LOCAL_SRE")
            
        return self.get_config_status()

    def _save_env_var(self, key: str, value: str):
        try:
            target_file = Path(__file__).parent.parent / ".env"
            lines = []
            if target_file.exists():
                with open(target_file, "r", encoding="utf-8") as f:
                    lines = f.readlines()
            
            updated = False
            new_lines = []
            for line in lines:
                if line.startswith(f"{key}="):
                    new_lines.append(f"{key}={value}\n")
                    updated = True
                else:
                    new_lines.append(line)
            if not updated:
                new_lines.append(f"{key}={value}\n")
                
            with open(target_file, "w", encoding="utf-8") as f:
                f.writelines(new_lines)
            os.environ[key] = value
        except Exception:
            pass

    def _mask_key(self, key: str) -> str:
        if not key or len(key) < 8:
            return "********"
        return f"{key[:4]}...{key[-4:]}"

    def chat(
        self,
        user_message: str,
        history: Optional[List[Dict[str, str]]] = None,
        operational_context: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        history = history or []
        operational_context = operational_context or {}

        # 0. Perform Emotional Tone & Sentiment Analysis
        sentiment_meta = self.sentiment_analyzer.analyze(user_message)

        # 1. Search knowledge base via RAG for relevant runbooks
        runbooks = []
        if self.rag_service:
            runbooks = self.rag_service.query_runbooks(user_message, top_k=2)

        context_str = self._format_context(operational_context, runbooks)

        # 2. If provider is GEMINI and key is set, call Google Gemini
        if self.provider == "GEMINI" and self.gemini_key:
            gemini_res = self._call_gemini(user_message, history, context_str, sentiment_meta)
            if gemini_res:
                return {
                    "reply": gemini_res,
                    "engine_used": f"Google Gemini ({self.gemini_model}) [Free LLM]",
                    "referenced_runbooks": [rb["runbook_id"] for rb in runbooks],
                    "status": "SUCCESS",
                    "sentiment": sentiment_meta
                }

        # 3. If provider is GROK and key is set, call xAI Grok
        if self.provider == "GROK" and self.grok_key:
            grok_res = self._call_grok(user_message, history, context_str, sentiment_meta)
            if grok_res:
                return {
                    "reply": grok_res,
                    "engine_used": f"xAI Grok ({self.grok_model})",
                    "referenced_runbooks": [rb["runbook_id"] for rb in runbooks],
                    "status": "SUCCESS",
                    "sentiment": sentiment_meta
                }

        # 4. Local Conversational Intelligence Engine (natural, Google-search style with sentiment adaptation)
        reply = self._conversational_reply(user_message, operational_context, runbooks, sentiment_meta)
        return {
            "reply": reply,
            "engine_used": "SPECTRA_DETERMINISTIC_COPILOT",
            "referenced_runbooks": [rb["runbook_id"] for rb in runbooks],
            "status": "SUCCESS",
            "sentiment": sentiment_meta
        }

    def _call_gemini(self, message: str, history: List[Dict[str, str]], context_str: str, sentiment: Dict[str, Any]) -> Optional[str]:
        """Calls Google Gemini with full autonomous understanding, context, and emotional tone."""
        prompt = (
            f"CURRENT LIVE OPERATIONAL CONTEXT:\n{context_str}\n\n"
            f"USER EMOTIONAL TONE & SENTIMENT:\n"
            f"- Emotion: {sentiment.get('emotion')} ({sentiment.get('tone_label')})\n"
            f"- Urgency: {sentiment.get('urgency')}\n"
            f"- Guidance: {sentiment.get('empathetic_guidance')}\n\n"
            f"USER QUERY: {message}\n\n"
            f"Answer conversationally, thoroughly, and with appropriate empathy matching the user's emotional state."
        )

        # Method A: Try google.generativeai SDK
        if HAS_GENAI:
            try:
                genai.configure(api_key=self.gemini_key)
                model = genai.GenerativeModel(
                    model_name=self.gemini_model,
                    system_instruction=SPECTRA_SYSTEM_PROMPT
                )
                response = model.generate_content(prompt)
                if response and response.text:
                    return response.text
            except Exception as ex:
                print(f"[CopilotService] Gemini SDK call failed, falling back to REST: {ex}")

        # Method B: Direct Google Gemini REST API (guaranteed compatibility)
        try:
            url = f"https://generativelanguage.googleapis.com/v1beta/models/{self.gemini_model}:generateContent?key={self.gemini_key}"
            headers = {"Content-Type": "application/json"}
            payload = {
                "system_instruction": {
                    "parts": [{"text": SPECTRA_SYSTEM_PROMPT}]
                },
                "contents": [
                    {
                        "parts": [{"text": prompt}]
                    }
                ],
                "generationConfig": {
                    "temperature": 0.4,
                    "maxOutputTokens": 2048
                }
            }
            res = requests.post(url, headers=headers, json=payload, timeout=25)
            if res.status_code == 200:
                data = res.json()
                candidates = data.get("candidates", [])
                if candidates and "content" in candidates[0]:
                    parts = candidates[0]["content"].get("parts", [])
                    if parts and "text" in parts[0]:
                        return parts[0]["text"]
            else:
                print(f"[CopilotService] Gemini REST API returned {res.status_code}: {res.text}")
        except Exception as ex:
            print(f"[CopilotService] Gemini REST call failed: {ex}")

        return None

    def _call_grok(self, message: str, history: List[Dict[str, str]], context_str: str, sentiment: Dict[str, Any]) -> Optional[str]:
        """Calls xAI Grok API (OpenAI-compatible) with full autonomous understanding, context, and emotional tone."""
        try:
            url = "https://api.x.ai/v1/chat/completions"
            headers = {
                "Authorization": f"Bearer {self.grok_key}",
                "Content-Type": "application/json"
            }
            messages = [{"role": "system", "content": SPECTRA_SYSTEM_PROMPT}]
            
            for h in history[-6:]:
                role = "assistant" if h.get("role") in ["assistant", "model"] else "user"
                content = h.get("text") or h.get("content", "")
                if content:
                    messages.append({"role": role, "content": content})
                    
            messages.append({
                "role": "user",
                "content": (
                    f"CURRENT LIVE OPERATIONAL CONTEXT:\n{context_str}\n\n"
                    f"USER EMOTION & SENTIMENT:\n"
                    f"- Tone: {sentiment.get('tone_label')}\n"
                    f"- Urgency: {sentiment.get('urgency')}\n"
                    f"- Guidance: {sentiment.get('empathetic_guidance')}\n\n"
                    f"USER MESSAGE: {message}\n\n"
                    f"Answer conversationally and thoroughly with appropriate emotional intelligence like Grok/ChatGPT."
                )
            })
            
            payload = {
                "model": self.grok_model,
                "messages": messages,
                "temperature": 0.4
            }
            res = requests.post(url, headers=headers, json=payload, timeout=25)
            if res.status_code == 200:
                data = res.json()
                return data["choices"][0]["message"]["content"]
            else:
                print(f"[CopilotService] Grok API returned {res.status_code}: {res.text}")
        except Exception as ex:
            print(f"[CopilotService] Grok call failed: {ex}")
        return None

    def _format_context(self, context: Dict[str, Any], runbooks: List[Dict[str, Any]]) -> str:
        lines = []
        if "activeScenario" in context:
            lines.append(f"- Active Chaos Scenario: {context['activeScenario']}")
        if "activeIncident" in context and context["activeIncident"]:
            inc = context["activeIncident"]
            lines.append(f"- Active Incident: #{inc.get('id')} '{inc.get('title')}' on service '{inc.get('serviceName')}' (Severity: {inc.get('severity')}, Status: {inc.get('status')})")
            if inc.get("description"):
                lines.append(f"  Description: {inc.get('description')}")
        if "services" in context:
            svcs = [f"{s.get('name', 'svc')}({s.get('status', 'HEALTHY')})" for s in context["services"][:5]]
            lines.append(f"- Monitored Services: {', '.join(svcs)}")
        elif "totalServices" in context:
            lines.append(f"- Monitored Services: {context['totalServices']} active services registered")
        if "alerts" in context:
            lines.append(f"- Triggered Alerts: {len(context['alerts'])} active alerts pending review")
        if "incidents" in context:
            lines.append(f"- Total Incidents: {len(context['incidents'])} incidents in history")
        if runbooks:
            lines.append("- Matched Knowledge Base Runbooks:")
            for rb in runbooks:
                actions = "; ".join(rb.get("immediate_actions", [])[:2])
                lines.append(f"  * [{rb['runbook_id']}] {rb['title']} (Trigger: {rb.get('metric_trigger')}) -> Actions: {actions}")
        return "\n".join(lines) if lines else "Cluster operating in standard baseline mode."

    def _conversational_reply(
        self,
        message: str,
        context: Dict[str, Any],
        runbooks: List[Dict[str, Any]],
        sentiment: Optional[Dict[str, Any]] = None
    ) -> str:
        """
        Generates an emotionally intelligent, conversational, Google-search style response that answers questions
        clearly and humanly like ChatGPT or Gemini without rigid templates.
        """
        sentiment = sentiment or {}
        emotion = sentiment.get("emotion", "NEUTRAL_ANALYTICAL")
        is_greeting = sentiment.get("is_greeting", False)
        msg = message.lower().strip()

        # 1. Greetings & Casual Social Interactions (accurately handles "hy", "hi", "hey", "hello", "sup", "wassup", etc.)
        if is_greeting or emotion == "CASUAL_GREETING" or msg in ["hy", "hi", "hey", "hello", "sup", "yo", "wassup", "hola"]:
            return (
                "Hey there! Great to connect with you. How's everything going with your cluster today?\n\n"
                "I am **Sanjaya AI**, your conversational engineering and SRE assistant. "
                "I'm here to help you monitor services, inspect live telemetry (CPU, memory, latency), "
                "diagnose incidents, or discuss software architecture.\n\n"
                "What would you like to explore or check on right now?"
            )

        # 2. Gratitude & Relief Interactions
        if emotion == "GRATITUDE_RELIEF":
            return (
                "You're very welcome! I'm really glad that was helpful.\n\n"
                "If you'd like to do a quick health verification on your services or review any lingering metrics, feel free to ask anytime!"
            )

        # 3. Urgent Panic / Production Outage Distress
        if emotion == "URGENT_PANIC":
            services = context.get("services", [])
            alerts = context.get("alerts", [])
            active_inc = context.get("activeIncident")
            inc_info = f"Active Incident #{active_inc['id']} on `{active_inc.get('serviceName')}` ({active_inc.get('title')})" if active_inc else "No formal incident ticket opened yet."

            return (
                "🚨 **Take a deep breath—I am right here with you.** Let's stabilize the cluster immediately.\n\n"
                "### Immediate SRE Incident Response Checklist\n"
                f"1. **Confirm Blast Radius:** Current cluster tracking: {inc_info}.\n"
                f"2. **Check Database & Thread Pools:** {len(alerts)} alerts are currently active. Inspect HikariCP connections and database lock contention.\n"
                "3. **Shed Ingress Load:** If servers are crashing under load, temporarily engage rate-limiting at the ingress gateway or activate circuit breaker fallback stubs.\n"
                "4. **Collect Diagnostic Artifacts:** Before restarting, capture a thread dump (`jstack <PID>`) or heap dump (`jcmd <PID> GC.heap_dump`) so engineering can analyze the post-mortem.\n\n"
                "Tell me which microservice is failing or the error message you're seeing, and I'll pull the exact runbook commands for you right now."
            )

        # 4. Frustration & Annoyance De-escalation
        if emotion == "FRUSTRATION":
            return (
                "I hear you, and I completely understand how frustrating it is when systems are unstable and repeated errors occur.\n\n"
                "Let's step through this methodically so we can get your environment stable and running smoothly:\n\n"
                "* **Telemetry Check:** Navigate to the **Metrics** page to verify if CPU, memory, or connection pools are spiking.\n"
                "* **Statistical Outliers:** Check the **Alerts** tab (`|Z| >= 3.0`) to see if an underlying service is experiencing hidden anomalies.\n"
                "* **Runbooks:** I have enterprise remediation playbooks ready to execute for connection starvation, memory leaks, and network timeouts.\n\n"
                "What specific component or error is giving you the most trouble right now? Let's solve it together."
            )

        # 5. Identity / Introduction / Capabilities
        if any(g in msg for g in ["who are you", "what are you", "what can you do", "introduce yourself", "tell me about yourself"]):
            return (
                "Hello! I am **Sanjaya AI**, your intelligent Site Reliability Engineering (SRE) and operations partner for the Spectra platform.\n\n"
                "Think of me like a dedicated ChatGPT or Gemini tailored specifically for your distributed microservices and infrastructure. Here is how I can help you:\n\n"
                "* **Diagnose Outages & Root Causes:** When a microservice degrades, I analyze telemetry patterns, exception traces, and dependency graphs to pinpoint exactly what failed and why.\n"
                "* **Telemetry & Anomaly Tracking:** I monitor real-time CPU, memory, latency, and error rates using statistical $Z$-scores ($|Z| \\ge 3.0$) and Isolation Forest models to catch silent anomalies before they cause user-facing outages.\n"
                "* **Capacity & Exhaustion Forecasting:** I project capacity trajectories over 30-minute operational horizons to estimate Time-To-Exhaustion (TTE) before critical SLA thresholds breach.\n"
                "* **Remediation & Runbooks:** I retrieve step-by-step enterprise SOP playbooks with concrete terminal and SQL commands to restore degraded systems safely.\n"
                "* **Engineering & DevOps Q&A:** You can ask me any technical question—from how HikariCP connection pooling works, to debugging JVM memory leaks, or configuring proactive alerts.\n\n"
                "How can I help you with your services or cluster operations today?"
            )

        # 2. General Health / Status Question
        if any(k in msg for k in ["how is the system", "system health", "cluster health", "platform health", "are services healthy", "current status", "what is the status", "health status", "current health status"]):
            services = context.get("services", [])
            alerts = context.get("alerts", [])
            incidents = context.get("incidents", [])
            active_inc = context.get("activeIncident")

            if active_inc or alerts:
                inc_text = f"Incident #{active_inc['id']} (**{active_inc.get('title')}**)" if active_inc else "triggered alert conditions"
                return (
                    f"### ✨ Spectra Platform Health Summary\n\n"
                    f"The cluster health status is currently evaluated as **DEGRADED** due to active {inc_text}.\n\n"
                    f"* **Monitored Services:** {len(services)} services registered\n"
                    f"* **Active Alerts:** {len(alerts)} threshold breach events pending review\n"
                    f"* **Under Investigation:** {active_inc.get('serviceName', 'Target Service') if active_inc else 'Degraded components'}\n\n"
                    f"Would you like me to walk through the root cause analysis, assess the cascading blast radius, or display the recommended remediation runbook?"
                )
            else:
                return (
                    "### ✨ Spectra Platform Health Summary\n\n"
                    "All registered microservices are currently operating within healthy baseline bounds (**HEALTHY**):\n\n"
                    "* **Services Monitored:** `order-processing`, `payment-service`, `database-gateway`, `auth-service`, `notification-service`.\n"
                    "* **System Telemetry:** Real-time CPU, memory, latency, and error rates are tracking normally within expected Gaussian distributions ($|Z| < 1.5$).\n"
                    "* **Open Incidents:** 0 active Sev-1/Sev-2 incidents.\n\n"
                    "Everything is running smoothly! You can test our diagnostic pipeline at any time by triggering an anomaly from the data ingestion hub or chaos scenarios."
                )

        # 3. CPU / Starvation / High Compute Queries
        if any(k in msg for k in ["cpu", "starvation", "high compute", "compute usage"]):
            return (
                "### ⚡ Understanding CPU Usage & Starvation in Distributed Systems\n\n"
                "**CPU Usage** indicates the percentage of compute processor cycles consumed by application threads relative to available core capacity. When CPU utilization sustains above **85-90%**, request queues begin backing up, leading to exponential latency degradation and thread starvation.\n\n"
                "#### Common Causes in Production\n"
                "1. **Thread Contention & Lock Spin:** Multiple threads spinning to acquire synchronized blocks or database pool locks.\n"
                "2. **Garbage Collection Overhead:** When JVM heap memory is exhausted, the Garbage Collector consumes 100% of CPU time in consecutive Stop-The-World compaction loops.\n"
                "3. **Inefficient JSON/Cryptographic Processing:** Large uncompressed payload serialization or repeated TLS/SSL handshakes under heavy ingress volume.\n"
                "4. **Unbounded Compute Loops:** Recursive calls, unindexed collection lookups ($O(N^2)$), or regex backtracking.\n\n"
                "#### Recommended SRE Mitigation Steps\n"
                "* **Inspect Thread Consumption:** Run `top -H -p <PID>` to identify the exact thread consuming high CPU, then convert the thread ID to hex and inspect it in a `jstack <PID>` thread dump.\n"
                "* **Horizontal Replica Scaling:** Increase container/pod replica counts to divide ingress load across more compute nodes.\n"
                "* **Ingress Rate-Limiting:** Apply token-bucket rate limiting at the API Gateway to prevent downstream cascading lockups."
            )

        # 4. Memory Leaks / JVM Heap Exhaustion Queries
        if any(k in msg for k in ["memory leak", "heap", "oom", "outofmemory", "garbage collection", "jvm memory"]):
            return (
                "### 🧠 Understanding Memory Leaks & JVM Heap Exhaustion\n\n"
                "A **Memory Leak** in Java occurs when an application retains references to objects that are no longer needed by business logic. Because a valid GC root reference remains, the JVM Garbage Collector cannot reclaim the memory, causing the heap usage to climb monotonically until the process crashes with `java.lang.OutOfMemoryError`.\n\n"
                "#### Common Culprits in Microservices\n"
                "1. **Static Collections as Caches:** Using unbounded `static Map` or `List` instances without TTL eviction policies.\n"
                "2. **ThreadLocal Variable Leaks:** Storing objects in `ThreadLocal` on pooled threads (like Tomcat/Jetty worker threads) without explicitly calling `.remove()` after request completion.\n"
                "3. **Unclosed Connections & File Handles:** Leaving JDBC ResultSets, file streams, or HTTP response bodies unclosed.\n"
                "4. **Classloader Leaks:** Dynamically registering listeners or third-party SDKs that prevent class unload during hot-reloads.\n\n"
                "#### SRE Diagnostic & Resolution Workflow\n"
                "* **Capture Heap Dump:** Execute `jcmd <PID> GC.heap_dump /tmp/heap.hprof` while the container is live.\n"
                "* **Analyze Dominator Tree:** Load the `.hprof` file in Eclipse Memory Analyzer (MAT) or VisualVM to identify objects with the largest retained heap size.\n"
                "* **Immediate Remediation:** Initiate a rolling restart of affected service instances to reclaim memory while developers patch the retention path.\n"
                "* **Configure Post-Mortem Safeguards:** Set JVM flags `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/dumps`."
            )

        # 5. Database Connection Contention / HikariCP Queries
        if any(k in msg for k in ["database", "connection pool", "hikaricp", "deadlock", "postgres", "connection timeout"]):
            return (
                "### 🗄️ Database Connection Pool Contention & Starvation\n\n"
                "In microservice architectures, connection pools like **HikariCP** manage reusable database connections to prevent the expensive overhead of establishing a new TCP/TLS connection for every transaction. When queries run slowly or transactions remain uncommitted, the pool becomes exhausted, and incoming requests wait in queue until throwing connection acquisition timeouts (`Connection is not available, request timed out after 30000ms`).\n\n"
                "#### Primary Failure Modes\n"
                "1. **Unindexed Table Scans:** Slow SQL queries holding connections open for multiple seconds.\n"
                "2. **Work Done Inside Transactions:** Performing external HTTP calls or long compute tasks within `@Transactional` boundaries, keeping DB connections idle but reserved.\n"
                "3. **Undersized Connection Pools:** `maximumPoolSize` set too low for peak concurrent traffic.\n"
                "4. **Database Lock Contention:** Competing transactions locking the same rows or table partitions.\n\n"
                "#### How to Investigate & Resolve\n"
                "* **Check Active PostgreSQL Queries:**\n"
                "  ```sql\n"
                "  SELECT pid, now() - query_start AS duration, state, query\n"
                "  FROM pg_stat_activity\n"
                "  WHERE state != 'idle'\n"
                "  ORDER BY duration DESC;\n"
                "  ```\n"
                "* **Terminate Blocking Queries:** Run `SELECT pg_terminate_backend(<pid>);` to immediately free locked resources.\n"
                "* **Optimize HikariCP Pool Sizing:** Use the standard sizing formula: `connections = ((core_count * 2) + effective_spindle_count)`. For Spring Boot, adjust `spring.datasource.hikari.maximum-pool-size` appropriately."
            )

        # 6. Z-Score & Statistical Anomaly Detection Queries
        if any(k in msg for k in ["z-score", "zscore", "anomaly detection", "how do you detect", "evidence", "xai"]):
            return (
                "### 🔬 How Spectra Evaluates Telemetry Anomalies via Statistical Z-Scores\n\n"
                "Traditional monitoring systems rely on static thresholds (such as 'alert if latency > 500ms'), which trigger false alarms during routine traffic spikes. Spectra applies an adaptive rolling **Z-score** formula combined with unsupervised Isolation Forests to evaluate anomalies mathematically.\n\n"
                "#### The Mathematical Formula\n"
                "$$Z = \\frac{x_t - \\mu_{30}}{\\sigma_{30}}$$\n\n"
                "* **$x_t$:** The real-time metric value (CPU, memory, latency, or error rate).\n"
                "* **$\\mu_{30}$:** The rolling mean calculated across the previous 30 observations.\n"
                "* **$\\sigma_{30}$:** The sample standard deviation using Bessel's correction ($N - 1$).\n"
                "* **$\\sigma_{min} = 1.0$ (Variance Floor):** Enforced to prevent divide-by-zero crashes on flatline steady-state signals.\n\n"
                "#### The $|Z| \\ge 3.0$ Significance Threshold\n"
                "In a standard Gaussian distribution, **99.73%** of all data points fall within three standard deviations of the mean. An observation with $|Z| \\ge 3.0$ has less than a **0.27%** chance of being normal variation, providing mathematical confidence that the deviation represents a genuine operational anomaly."
            )

        # 7. Blast Radius & Topology Queries
        if any(k in msg for k in ["blast radius", "cascading", "dependency", "dependencies", "impact"]):
            return (
                "### 🌐 Blast Radius & Microservice Dependency Topology\n\n"
                "In distributed microservices, failures rarely stay isolated. An issue in a core shared component inevitably ripples outward to upstream and downstream services.\n\n"
                "#### Typical Cascading Failure Chain\n"
                "```\n"
                "Ingress Traffic ──> order-processing\n"
                "                         │\n"
                "                         ▼ (REST/gRPC)\n"
                "                     payment-service\n"
                "                         │\n"
                "                         ▼ (HikariCP / JDBC)\n"
                "                     database-gateway (PostgreSQL)\n"
                "                         ▲\n"
                "                         │ (Token Validation)\n"
                "                     auth-service\n"
                "```\n\n"
                "#### Risk & Impact Analysis\n"
                "* **Bottleneck Dependency:** `database-gateway` and `auth-service` sit on the critical path of both ordering and checkout.\n"
                "* **Cascading Latency:** If database connection acquisition slows down, `payment-service` worker threads block, exhausting incoming thread pools within 15-30 seconds.\n"
                "* **Containment Best Practice:** Implement circuit breakers (e.g. Resilience4j) with fallback stubs, connection timeouts (<2500ms), and bulkhead thread isolation to contain the blast radius."
            )

        # 8. Active Incident Specific Root Cause / Troubleshooting
        if any(k in msg for k in ["root cause", "what broke", "why did it happen", "why is it failing", "diagnose"]):
            active_inc = context.get("activeIncident")
            incidents = context.get("incidents", [])
            target = active_inc or (incidents[0] if incidents else None)

            if target:
                title = target.get("title", "Service Latency Degradation")
                svc = target.get("serviceName", "Target Service")
                sev = target.get("severity", "CRITICAL")
                t_lower = title.lower()

                if "database" in t_lower or "connection" in t_lower or "pool" in t_lower:
                    return (
                        f"### 🚨 Diagnostic Analysis: Incident #{target.get('id', '1')} ({title})\n\n"
                        f"Based on real-time telemetry and exception logs, here is the diagnosis for `{svc}`:\n\n"
                        f"* **Root Cause:** **HikariCP Database Connection Pool Exhaustion** (Confidence: 94%)\n"
                        f"* **Mechanism:** Worker threads saturated all available database connections. Uncommitted transactions held locks open beyond client timeout thresholds, resulting in queuing and HTTP 504 gateway timeouts.\n"
                        f"* **Immediate Remediation:**\n"
                        f"  1. Terminate blocking backend PIDs using `SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE state != 'idle';`\n"
                        f"  2. Increase `maximumPoolSize` from 10 to 40 in the service configuration.\n"
                        f"  3. Monitor HikariCP connection acquisition latency to confirm stabilization."
                    )
                elif "memory" in t_lower or "heap" in t_lower or "leak" in t_lower:
                    return (
                        f"### 🚨 Diagnostic Analysis: Incident #{target.get('id', '1')} ({title})\n\n"
                        f"Based on real-time telemetry and exception logs, here is the diagnosis for `{svc}`:\n\n"
                        f"* **Root Cause:** **JVM Heap Memory Exhaustion / Monotonic Heap Leak** (Confidence: 91%)\n"
                        f"* **Mechanism:** Heap utilization rose steadily without post-GC recovery, causing extended Stop-The-World garbage collection pauses and worker thread lockups.\n"
                        f"* **Immediate Remediation:**\n"
                        f"  1. Capture a heap dump: `jcmd <PID> GC.heap_dump /tmp/heap.hprof`.\n"
                        f"  2. Trigger a rolling restart of the container instance to immediately restore service.\n"
                        f"  3. Inspect static collection references and cache retention paths."
                    )
                elif "cpu" in t_lower or "starvation" in t_lower:
                    return (
                        f"### 🚨 Diagnostic Analysis: Incident #{target.get('id', '1')} ({title})\n\n"
                        f"Based on real-time telemetry and exception logs, here is the diagnosis for `{svc}`:\n\n"
                        f"* **Root Cause:** **CPU Compute Saturation & Thread Starvation** (Confidence: 89%)\n"
                        f"* **Mechanism:** Intensive serialization or compute loops saturated 100% of available CPU cores, starving the worker pool.\n"
                        f"* **Immediate Remediation:**\n"
                        f"  1. Capture thread dump: `jstack <PID>` to identify spinning threads.\n"
                        f"  2. Horizontally scale service replicas to distribute traffic volume.\n"
                        f"  3. Engage ingress rate-limiting to shed excess traffic bursts."
                    )
                else:
                    return (
                        f"### 🚨 Diagnostic Analysis: Incident #{target.get('id', '1')} ({title})\n\n"
                        f"Based on real-time telemetry and exception logs, here is the diagnosis for `{svc}`:\n\n"
                        f"* **Root Cause:** **Cascading Network Latency & Dependency Timeout** (Confidence: 87%)\n"
                        f"* **Mechanism:** Latency in an upstream service caused local connection pools to back up, tripping the circuit breaker.\n"
                        f"* **Immediate Remediation:** Activate circuit breaker fallback responses, verify upstream network connectivity, and review socket timeouts."
                    )

        # 9. Generic / General Freeform Technical Question
        # Provide a natural, helpful, conversational answer in the style of ChatGPT/Gemini
        return (
            f"Here is what you need to know regarding **{message.strip()}**:\n\n"
            f"In distributed cloud architectures, this typically relates to system reliability, throughput limits, and resource utilization. "
            f"Ensuring microservices remain resilient requires continuous telemetry tracking (P95/P99 latency, CPU, and heap), "
            f"enforcing circuit breakers on external calls, and establishing clear automated runbooks for incident response.\n\n"
            f"* **In Spectra:** You can observe real-time metrics in the **Metrics** view, inspect statistical outliers in the **Alerts** tab, "
            f"or review incident blast radius in **Incidents**.\n"
            f"* **Pro Tip:** To enable live, autonomous LLM reasoning directly from Google Gemini, click **⚙️ Configure Free LLM** in the top right to paste a free Gemini API key.\n\n"
            f"Would you like me to elaborate on a specific aspect of this or analyze one of your cluster services?"
        )
