package com.aresstack.enterpriseai.proasteion.exedra.settings;

import com.aresstack.enterpriseai.proasteion.application.config.TextMiningConfig;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;
import org.junit.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Issue #107: the acronym switches and thresholds round-trip through the settings form; defaults stay out. */
public class AcronymSettingsMappingTest {

    @Test
    public void defaultsAreNotWritten() {
        SettingsForm form = SettingsMapper.fromProperties(new Properties());
        assertTrue(form.acronymsEnabled());
        assertTrue(form.acronymsHighlight());
        assertEquals("10", form.acronymMaxLetters());
        Map<String, String> changes = SettingsMapper.changes(form);
        assertEquals("", changes.get(TextMiningConfig.ACRONYMS_ENABLED_KEY));
        assertEquals("", changes.get(TextMiningConfig.READER_HIGHLIGHT_KEY));
        assertEquals("", changes.get(TextMiningConfig.MAX_LETTERS_KEY));
        assertEquals("", changes.get(TextMiningConfig.SHOUTING_PERCENT_KEY));
        assertEquals("", changes.get(TextMiningConfig.SHOUTING_MIN_WORDS_KEY));
    }

    @Test
    public void changedValuesRoundTrip() {
        Properties stored = new Properties();
        stored.setProperty(TextMiningConfig.ACRONYMS_ENABLED_KEY, "false");
        stored.setProperty(TextMiningConfig.READER_HIGHLIGHT_KEY, "false");
        stored.setProperty(TextMiningConfig.MAX_LETTERS_KEY, "6");
        stored.setProperty(TextMiningConfig.SHOUTING_PERCENT_KEY, "75");
        stored.setProperty(TextMiningConfig.SHOUTING_MIN_WORDS_KEY, "4");
        SettingsForm form = SettingsMapper.fromProperties(stored);
        assertFalse(form.acronymsEnabled());
        assertFalse(form.acronymsHighlight());
        Map<String, String> changes = SettingsMapper.changes(form);
        assertEquals("false", changes.get(TextMiningConfig.ACRONYMS_ENABLED_KEY));
        assertEquals("false", changes.get(TextMiningConfig.READER_HIGHLIGHT_KEY));
        assertEquals("6", changes.get(TextMiningConfig.MAX_LETTERS_KEY));
        assertEquals("75", changes.get(TextMiningConfig.SHOUTING_PERCENT_KEY));
        assertEquals("4", changes.get(TextMiningConfig.SHOUTING_MIN_WORDS_KEY));
    }
}
