import re
from typing import Dict, Any


class SentimentAnalyzer:
    """
    Evaluates the emotional tone, sentiment polarity, and urgency of user messages.
    Enables Sanjaya AI to respond with emotional intelligence and empathy.
    """

    # Greeting patterns including typos and common colloquialisms like "hy", "heyy", "wassup"
    GREETING_REGEX = re.compile(
        r"^(h+[ye]+|h+[aeiou]+y*|hello+|greetings|yo+|sup|wassup|wazzup|howdy|good\s*(morning|afternoon|evening|day)|how\s*(are\s*you|r\s*u)|what'?s\s*up)\b",
        re.IGNORECASE
    )

    # Urgent panic / critical outage indicators
    PANIC_KEYWORDS = {
        "down", "crashing", "crashed", "outage", "broken", "failing", "failed",
        "critical", "sev1", "sev 1", "emergency", "disaster", "asap", "urgent",
        "fire", "meltdown", "panic", "production down", "prod down", "everything down"
    }

    # Frustration indicators
    FRUSTRATION_KEYWORDS = {
        "annoying", "annoyed", "frustrated", "hate", "terrible", "horrible",
        "stupid", "worst", "broken again", "not working", "useless", "slow as hell",
        "why is it failing", "why does this keep happening"
    }

    # Gratitude & relief indicators
    GRATITUDE_KEYWORDS = {
        "thanks", "thank you", "thx", "appreciate", "awesome", "great",
        "perfect", "good job", "helpful", "fixed", "resolved", "solved", "lifesaver"
    }

    # Inquiry / Curiosity indicators
    CURIOSITY_KEYWORDS = {
        "what is", "how does", "why does", "explain", "tell me about",
        "can you explain", "how can i", "what causes", "how to debug", "compare"
    }

    def analyze(self, text: str) -> Dict[str, Any]:
        raw = text.strip()
        cleaned = raw.lower()
        words = set(re.findall(r"\b[a-z0-9'-]+\b", cleaned))

        # Check for exclamation marks and capital letters (emotional intensity)
        exclamation_count = raw.count("!")
        uppercase_ratio = sum(1 for c in raw if c.isupper()) / max(len(raw), 1)

        # 1. Check for Casual Greeting (e.g., "hy", "hi", "hey", "hello", "good morning")
        # Matches when the text starts with or is primarily a greeting
        if self.GREETING_REGEX.search(cleaned) or cleaned in {"hy", "hi", "hey", "hello", "yo", "sup"}:
            # Ensure it's not a panic message that just happens to have 'hi' in it
            if not any(k in words for k in self.PANIC_KEYWORDS):
                return {
                    "emotion": "CASUAL_GREETING",
                    "tone_label": "Casual & Friendly",
                    "polarity": 0.5,
                    "urgency": "LOW",
                    "is_greeting": True,
                    "empathetic_guidance": "Respond warmly, casually, and conversationally. Do not treat greeting words as technical keywords to search."
                }

        # 2. Check for Urgent / Panicked Emotion
        panic_hits = [k for k in self.PANIC_KEYWORDS if k in cleaned or k in words]
        if panic_hits or (uppercase_ratio > 0.45 and len(raw) > 8 and exclamation_count >= 1):
            return {
                "emotion": "URGENT_PANIC",
                "tone_label": "Urgent & High Stress",
                "polarity": -0.85,
                "urgency": "HIGH",
                "is_greeting": False,
                "empathetic_guidance": "The user is in high distress over an outage or failure. Acknowledge the urgency immediately, provide calm reassurance, and give clear, prioritized first steps."
            }

        # 3. Check for Frustration Emotion
        frust_hits = [k for k in self.FRUSTRATION_KEYWORDS if k in cleaned or k in words]
        if frust_hits:
            return {
                "emotion": "FRUSTRATION",
                "tone_label": "Frustrated & Dissatisfied",
                "polarity": -0.65,
                "urgency": "MEDIUM",
                "is_greeting": False,
                "empathetic_guidance": "The user is frustrated by repeated issues or system behavior. Empathize with their pain point, validate their concern, and focus on practical troubleshooting without being dismissive."
            }

        # 4. Check for Gratitude & Relief Emotion
        grat_hits = [k for k in self.GRATITUDE_KEYWORDS if k in cleaned or k in words]
        if grat_hits:
            return {
                "emotion": "GRATITUDE_RELIEF",
                "tone_label": "Grateful & Relieved",
                "polarity": 0.85,
                "urgency": "LOW",
                "is_greeting": False,
                "empathetic_guidance": "The user is thankful and satisfied. Respond with gracious appreciation and invite any future checks or wrap-up questions."
            }

        # 5. Check for Curiosity / Educational Inquiry
        curious_hits = [k for k in self.CURIOSITY_KEYWORDS if k in cleaned]
        if curious_hits or "?" in raw:
            return {
                "emotion": "CURIOSITY_INQUIRY",
                "tone_label": "Curious & Inquisitive",
                "polarity": 0.2,
                "urgency": "LOW",
                "is_greeting": False,
                "empathetic_guidance": "The user is seeking deep understanding or troubleshooting knowledge. Provide a clear, educational, structured explanation with real-world examples."
            }

        # 6. Default to Neutral / Analytical
        return {
            "emotion": "NEUTRAL_ANALYTICAL",
            "tone_label": "Objective & Analytical",
            "polarity": 0.0,
            "urgency": "LOW",
            "is_greeting": False,
            "empathetic_guidance": "Maintain a professional, articulate, and helpful SRE consultation tone."
        }
