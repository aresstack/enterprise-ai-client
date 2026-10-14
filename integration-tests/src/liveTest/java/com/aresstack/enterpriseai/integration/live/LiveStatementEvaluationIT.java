package com.aresstack.enterpriseai.integration.live;

import com.aresstack.enterpriseai.astu.acropolis.passage.SentenceSegmenter;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.ChatStatementExtractor;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.ChatStatementVerifier;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.PredicateCheck;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementAnalyzer;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementEvaluation;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementSettings;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatAdapter;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatConfig;
import org.junit.Test;

import java.net.URI;

/**
 * Stufe 8 der Live-Verifikation: die Satzanalyse (#108) mit dem echten Chat-Modell auf den 50 erfundenen Sätzen von
 * {@link StatementEvaluation#builtIn()}. Meldet Präzision und Recall je Ebene, falsche Rollen und verbotene Aussagen;
 * wird nie rot, weil die Zahlen erst den Stand messen. Parameter wie Stufe 1, optional
 * {@code -Dlive.statements.verify=false} (ohne Ja/Nein-Prüfung).
 */
public class LiveStatementEvaluationIT {

    private static final int STAGE = 8;

    @Test
    public void evaluatesTheSentenceAnalysisOnInventedSentences() throws Exception {
        LiveSettings.withoutSecretLeak(() -> {
            final String model = LiveSettings.required("live.chat.model");
            OpenAiCompatibleChatAdapter chat = new OpenAiCompatibleChatAdapter(OpenAiCompatibleChatConfig.builder(
                    URI.create(LiveSettings.required("live.chat.baseUrl")), model)
                    .bearerToken(LiveSettings.chatApiKey())
                    .readTimeoutMillis(180000)
                    .build());
            final boolean verify = !"false".equals(LiveSettings.optional("live.statements.verify"));
            final StatementAnalyzer analyzer = new StatementAnalyzer(SentenceSegmenter.splitter(),
                    new ChatStatementExtractor(chat, () -> model), PredicateCheck.none(),
                    new ChatStatementVerifier(chat, () -> model, () -> verify));
            final StatementSettings settings = new StatementSettings(true, verify, 1,
                    StatementSettings.DEFAULT_MAX_PARAGRAPH_CHARS);
            long started = System.nanoTime();
            StatementEvaluation.Report report = StatementEvaluation.evaluate(StatementEvaluation.builtIn(),
                    gold -> analyzer.analyze(gold.sentence(), settings));
            LiveSettings.report(STAGE, "Prüfung " + (verify ? "an" : "aus") + ", "
                    + (System.nanoTime() - started) / 1000000000L + " s");
            for (String line : report.toString().split("\n")) {
                LiveSettings.report(STAGE, line);
            }
        });
    }
}
