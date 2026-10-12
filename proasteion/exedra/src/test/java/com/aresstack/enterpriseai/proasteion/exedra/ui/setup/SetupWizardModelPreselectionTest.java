package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.proasteion.exedra.settings.SettingsMapper;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Step "Modelle" selects chat and embedding model by itself and measures the dimension right away, so the usual
 * first start needs no click there; a missing preferred model silently falls back to the first offered one.
 */
public class SetupWizardModelPreselectionTest {

    private static final String CHAT = "openai/gpt-oss-120b";
    private static final String EMBEDDING = "danielheinz/e5-base-sts-en-de";
    private static final String TTS = "Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice";

    private final WizardFakes.Settings settings = new WizardFakes.Settings();
    private final WizardFakes.Setup setup = new WizardFakes.Setup();

    private SetupWizardPanel toModels() throws Exception {
        final AtomicReference<SetupWizardPanel> wizard = new AtomicReference<SetupWizardPanel>();
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardRun run = new SetupWizardRun(SettingsMapper.firstStartDefaults(), settings, setup,
                    ComicPalette.defaultPalette(), done -> { });
            SetupWizardPanel panel = run.wizard();
            panel.pairButton().doClick();
            panel.nextButton().doClick();
            panel.nextButton().doClick();
            assertEquals(SetupWizardPanel.STEP_MODELS, panel.step());
            wizard.set(panel);
        });
        return wizard.get();
    }

    private void offerScreenshotModels() {
        settings.chatModels.addAll(Arrays.asList("mistralai/Mistral-Small-3.2-24B-Instruct-2506", "openai/gpt-oss-20b",
                CHAT, "google/gemma-3-27b-it", "Qwen/Qwen3-32B"));
        settings.embeddingModels.addAll(Arrays.asList("BAAI/bge-m3", EMBEDDING, "intfloat/multilingual-e5-large",
                "Qwen/Qwen3-Embedding-8B"));
        settings.speechModels.addAll(Arrays.asList("canopylabs/orpheus-tts-3b-de", "Qwen/Qwen3-TTS-12Hz-0.6B-Base",
                "Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice"));
        settings.preferred.put(ModelCategory.CHAT, CHAT);
        settings.preferred.put(ModelCategory.EMBEDDING, EMBEDDING);
        settings.preferred.put(ModelCategory.TTS, TTS);
        settings.preferredMatch.put(ModelCategory.TTS, Arrays.asList("qwen3-tts", "customvoice"));
    }

    @Test
    public void preferredModelsAreSelectedAndTheDimensionIsMeasuredWithoutAClick() throws Exception {
        offerScreenshotModels();
        final SetupWizardPanel wizard = toModels();
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(CHAT, wizard.chatModelChoice().getSelectedItem());
            assertEquals(EMBEDDING, wizard.embeddingModelChoice().getSelectedItem());
            assertEquals("chat speech output, not Merlin's voice", TTS,
                    wizard.speechOutputModelChoice().getSelectedItem());
            assertEquals(TTS, wizard.form().modelSelection(ModelCategory.TTS.key()));
            assertEquals("768", wizard.embeddingDimensionField().getText());
            assertTrue("step done without manual choice", wizard.isDone(SetupWizardPanel.STEP_MODELS));
            assertNull(wizard.errorOf(SetupWizardPanel.STEP_MODELS));
        });
    }

    @Test
    public void missingPreferredModelFallsBackToTheFirstOfferedWithoutError() throws Exception {
        settings.chatModels.addAll(Arrays.asList("google/gemma-3-27b-it", "Qwen/Qwen3-32B"));
        settings.embeddingModels.addAll(Arrays.asList("BAAI/bge-m3", "intfloat/multilingual-e5-large"));
        settings.preferred.put(ModelCategory.CHAT, CHAT);
        settings.preferred.put(ModelCategory.EMBEDDING, EMBEDDING);
        final SetupWizardPanel wizard = toModels();
        SwingUtilities.invokeAndWait(() -> {
            assertEquals("google/gemma-3-27b-it", wizard.chatModelChoice().getSelectedItem());
            assertEquals("BAAI/bge-m3", wizard.embeddingModelChoice().getSelectedItem());
            assertEquals("768", wizard.embeddingDimensionField().getText());
            assertTrue(wizard.isDone(SetupWizardPanel.STEP_MODELS));
            assertNull(wizard.errorOf(SetupWizardPanel.STEP_MODELS));
        });
    }

    @Test
    public void selectionRulesKeepAnOfferedChoiceAndMatchAUniqueFragment() {
        assertEquals("kept", "google/gemma-3-27b-it", ModelPreselection.choose(
                Arrays.asList("x", "google/gemma-3-27b-it", CHAT), "google/gemma-3-27b-it", CHAT));
        assertEquals("exact with catalog prefix", "default:" + CHAT, ModelPreselection.choose(
                Arrays.asList("default:x", "default:" + CHAT), "", CHAT));
        assertEquals("unique fragment", "gpt-oss-120b-fp8", ModelPreselection.choose(
                Arrays.asList("a", "gpt-oss-120b-fp8"), "", CHAT));
        assertEquals("ambiguous fragment falls back to the first", "a", ModelPreselection.choose(
                Arrays.asList("a", "gpt-oss-120b-fp8", "x/gpt-oss-120b-int4"), "", CHAT));
        assertEquals("stale choice is replaced", CHAT, ModelPreselection.choose(
                Arrays.asList("a", CHAT), "gone", CHAT));
        assertEquals("nothing offered keeps the typed name", "typed", ModelPreselection.choose(
                Collections.<String>emptyList(), "typed", CHAT));
        assertEquals("no preference picks the first", "a", ModelPreselection.choose(
                Arrays.asList("a", "b"), "", ""));
        assertEquals("match words find another CustomVoice size", "Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice",
                ModelPreselection.choose(Arrays.asList("canopylabs/orpheus-tts-3b-de", "Qwen/Qwen3-TTS-12Hz-0.6B-Base",
                        "Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice"), "", TTS, Arrays.asList("qwen3-tts", "customvoice")));
    }
}
