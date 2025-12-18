package org.nhnacademy.book2onandonbookservice.service.enrichment.rate;

public interface ApiRateLimiter {
    /// Groq 호출 가능 여부 (초당 제한)
    boolean tryAcquireGroq();

    /// Gemini 호출 가능 여부 (초당 제한)
    boolean tryAcquireGemini();
}
