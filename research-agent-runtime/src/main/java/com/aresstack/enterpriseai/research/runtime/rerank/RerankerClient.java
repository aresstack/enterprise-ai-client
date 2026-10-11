package com.aresstack.enterpriseai.research.runtime.rerank;

import com.aresstack.enterpriseai.research.runtime.support.CancellationSignal;

import java.util.List;

/**
 * Bewertet Dokumente gegen eine Anfrage (Cross-Encoder). askai arch sprach dafür einen HTTP-Reranker direkt
 * an; hier liefert der Host das Modell (Kategorie RERANK), der Agentenprozess kennt weder URL noch Zugang.
 */
public interface RerankerClient {

    RerankResponse rerank(String query, List<String> documents, CancellationSignal cancellation)
            throws RerankerClientException;
}
