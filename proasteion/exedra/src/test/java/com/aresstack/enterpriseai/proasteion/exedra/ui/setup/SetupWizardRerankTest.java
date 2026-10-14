package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.proasteion.exedra.settings.SettingsMapper;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Re-Ranker im Schritt „Modelle“ (#102): rechts unter „Bildverständnis“ auf Höhe von „Dimension“, nur Modelle, die der
 * Katalog als RERANK einstuft, Vorwahl wie die übrigen Modelle, gespeichert als {@code model.rerank}; optional.
 */
public class SetupWizardRerankTest {

    private static final String RERANK = "BAAI/bge-reranker-v2-m3";

    private final WizardFakes.Settings settings = new WizardFakes.Settings();
    private final WizardFakes.Setup setup = new WizardFakes.Setup();

    private SetupWizardPanel toModels(final SettingsForm initial) throws Exception {
        final AtomicReference<SetupWizardPanel> wizard = new AtomicReference<SetupWizardPanel>();
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardPanel panel = new SetupWizardRun(initial, settings, setup, ComicPalette.defaultPalette(),
                    done -> { }).wizard();
            panel.jumpTo(SetupWizardPanel.STEP_MODELS);
            wizard.set(panel);
        });
        return wizard.get();
    }

    private void offerModels() {
        settings.chatModels.addAll(Arrays.asList("openai/gpt-oss-120b", "google/gemma-3-27b-it"));
        settings.embeddingModels.add("danielheinz/e5-base-sts-en-de");
        settings.visionModels.add("Qwen/Qwen2.5-VL-72B-Instruct");
    }

    private static List<String> items(JComboBox<String> combo) {
        List<String> items = new ArrayList<String>();
        for (int i = 0; i < combo.getItemCount(); i++) {
            items.add(combo.getItemAt(i));
        }
        return items;
    }

    private static GridBagConstraints cellOf(Component field) {
        Container cell = field.getParent();
        return ((GridBagLayout) cell.getParent().getLayout()).getConstraints(cell);
    }

    @Test
    public void rerankerSitsRightBelowVisionAlignedWithItAndNextToDimension() throws Exception {
        offerModels();
        final SetupWizardPanel wizard = toModels(SettingsMapper.firstStartDefaults());
        SwingUtilities.invokeAndWait(() -> {
            GridBagConstraints rerank = cellOf(wizard.rerankModelChoice());
            GridBagConstraints vision = cellOf(wizard.visionModelChoice());
            GridBagConstraints dimension = cellOf(wizard.embeddingDimensionField().getParent());
            assertEquals(1, rerank.gridx);
            assertEquals(2, rerank.gridy);
            assertEquals(vision.gridx, rerank.gridx);
            assertEquals(vision.gridy + 1, rerank.gridy);
            assertEquals("dieselbe Rasterzeile wie Dimension", dimension.gridy, rerank.gridy);
            assertEquals(vision.insets, rerank.insets);
            assertEquals(vision.fill, rerank.fill);

            Container grid = wizard.rerankModelChoice().getParent().getParent();
            grid.setSize(720, 240);
            layoutTree(grid);
            Component rerankCell = wizard.rerankModelChoice().getParent();
            Component visionCell = wizard.visionModelChoice().getParent();
            assertEquals("linke Kante", visionCell.getX(), rerankCell.getX());
            assertEquals("Breite", visionCell.getWidth(), rerankCell.getWidth());
            assertEquals("Feldbreite", wizard.visionModelChoice().getWidth(), wizard.rerankModelChoice().getWidth());
            assertEquals("Höhe auf Zeile Dimension", wizard.embeddingDimensionField().getParent().getParent().getY(),
                    rerankCell.getY());
        });
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                layoutTree((Container) child);
            }
        }
    }

    @Test
    public void onlyCatalogRerankModelsAreOfferedAndCounted() throws Exception {
        offerModels();
        settings.rerankModels.add(RERANK);
        final SetupWizardPanel wizard = toModels(SettingsMapper.firstStartDefaults());
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(Arrays.asList(RERANK), items(wizard.rerankModelChoice()));
            assertEquals(RERANK, wizard.rerankModelChoice().getSelectedItem());
            assertEquals(RERANK, wizard.form().modelSelection(ModelCategory.RERANK.key()));
            assertTrue(wizard.modelsStatusText(), wizard.modelsStatusText().endsWith("1 Re-Ranker"));
        });
    }

    @Test
    public void withoutRerankModelsTheStepStaysUsableAndOffersNothing() throws Exception {
        offerModels();
        final SetupWizardPanel wizard = toModels(SettingsMapper.firstStartDefaults());
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(0, wizard.rerankModelChoice().getItemCount());
            assertTrue(wizard.modelsStatusText(), wizard.modelsStatusText().endsWith("0 Re-Ranker"));
            assertEquals("", wizard.form().modelSelection(ModelCategory.RERANK.key()));
            assertTrue("Re-Ranker ist optional", wizard.isDone(SetupWizardPanel.STEP_MODELS));
        });
    }

    @Test
    public void savedRerankerIsRestoredAndClearingRemovesIt() throws Exception {
        offerModels();
        settings.rerankModels.addAll(Arrays.asList("other/reranker", RERANK));
        SettingsForm initial = SettingsMapper.firstStartDefaults().toBuilder()
                .modelSelection(ModelCategory.RERANK.key(), RERANK).build();
        final SetupWizardPanel wizard = toModels(initial);
        SwingUtilities.invokeAndWait(() -> {
            assertEquals("gespeicherte Auswahl bleibt", RERANK, wizard.rerankModelChoice().getSelectedItem());
            assertEquals(RERANK, wizard.form().modelSelection(ModelCategory.RERANK.key()));
            wizard.rerankModelChoice().setSelectedItem("");
            SettingsForm collected = wizard.form();
            assertFalse("leer entfernt model.rerank",
                    collected.modelSelections().containsKey(ModelCategory.RERANK.key()));
        });
    }
}
