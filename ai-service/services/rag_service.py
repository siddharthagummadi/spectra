import json
import os
from typing import List, Dict, Any, Optional
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity


class RagService:
    def __init__(self, runbooks_path: Optional[str] = None):
        if runbooks_path is None:
            base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
            runbooks_path = os.path.join(base_dir, "knowledge_base", "runbooks.json")
        self.runbooks_path = runbooks_path
        self.runbooks: List[Dict[str, Any]] = []
        self.vectorizer: Optional[TfidfVectorizer] = None
        self.corpus_matrix = None
        self._load_and_index()

    def _load_and_index(self):
        if not os.path.exists(self.runbooks_path):
            self.runbooks = []
            return

        with open(self.runbooks_path, "r", encoding="utf-8") as f:
            self.runbooks = json.load(f)

        if not self.runbooks:
            return

        corpus = []
        for r in self.runbooks:
            doc = (
                f"{r.get('title', '')} "
                f"{r.get('metric_trigger', '')} "
                f"{r.get('service_type', '')} "
                f"{' '.join(r.get('symptoms', []))} "
                f"{' '.join(r.get('root_causes', []))} "
                f"{' '.join(r.get('immediate_actions', []))} "
                f"{r.get('long_term_fix', '')}"
            )
            corpus.append(doc)

        self.vectorizer = TfidfVectorizer(stop_words="english")
        self.corpus_matrix = self.vectorizer.fit_transform(corpus)

    def query_runbooks(self, query: str, top_k: int = 3, metric_filter: Optional[str] = None) -> List[Dict[str, Any]]:
        if not self.runbooks or self.vectorizer is None or self.corpus_matrix is None:
            return []

        query_vec = self.vectorizer.transform([query])
        scores = cosine_similarity(query_vec, self.corpus_matrix)[0]

        results = []
        for idx, score in enumerate(scores):
            rb = self.runbooks[idx]
            # Optional boost if metric_trigger matches
            adjusted_score = float(score)
            if metric_filter and rb.get("metric_trigger", "").upper() == metric_filter.upper():
                adjusted_score += 0.25

            results.append({
                "score": round(adjusted_score, 4),
                "runbook_id": rb.get("id"),
                "title": rb.get("title"),
                "metric_trigger": rb.get("metric_trigger"),
                "service_type": rb.get("service_type"),
                "symptoms": rb.get("symptoms", []),
                "root_causes": rb.get("root_causes", []),
                "immediate_actions": rb.get("immediate_actions", []),
                "long_term_fix": rb.get("long_term_fix", "")
            })

        results.sort(key=lambda x: x["score"], reverse=True)
        return results[:top_k]
