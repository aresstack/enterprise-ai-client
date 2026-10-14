package com.aresstack.enterpriseai.proasteion.exedra.settings;

import com.aresstack.enterpriseai.proasteion.application.config.TextMiningConfig;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;
import org.junit.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Issue #108: the sentence analysis settings round-trip through the settings form; defaults stay out. */
public class StatementSettingsMappingTest {

    @Test
    public void offByDefaultAndDefaultsAreNotWritten() {
        SettingsForm form = SettingsMapper.fromProperties(new Properties());
        assertFalse(form.statementsEnabled());
        assertTrue(form.statementsVerify());
        assertEquals("40", form.statementsMaxParagraphs());
        assertEquals("1500", form.statementsMaxParagraphChars());
        Map<String, String> changes = SettingsMapper.changes(form);
        assertEquals("", changes.get(TextMiningConfig.STATEMENTS_ENABLED_KEY));
        assertEquals("", changes.get(TextMiningConfig.STATEMENTS_VERIFY_KEY));
        assertEquals("", changes.get(TextMiningConfig.STATEMENTS_MAX_PARAGRAPHS_KEY));
        assertEquals("", changes.get(TextMiningConfig.STATEMENTS_MAX_PARAGRAPH_CHARS_KEY));
    }

    @Test
    public void changedValuesRoundTrip() {
        Properties stored = new Properties();
        stored.setProperty(TextMiningConfig.STATEMENTS_ENABLED_KEY, "true");
        stored.setProperty(TextMiningConfig.STATEMENTS_VERIFY_KEY, "false");
        stored.setProperty(TextMiningConfig.STATEMENTS_MAX_PARAGRAPHS_KEY, "12");
        stored.setProperty(TextMiningConfig.STATEMENTS_MAX_PARAGRAPH_CHARS_KEY, "900");
        SettingsForm form = SettingsMapper.fromProperties(stored);
        assertTrue(form.statementsEnabled());
        assertFalse(form.statementsVerify());
        Map<String, String> changes = SettingsMapper.changes(form);
        assertEquals("true", changes.get(TextMiningConfig.STATEMENTS_ENABLED_KEY));
        assertEquals("false", changes.get(TextMiningConfig.STATEMENTS_VERIFY_KEY));
        assertEquals("12", changes.get(TextMiningConfig.STATEMENTS_MAX_PARAGRAPHS_KEY));
        assertEquals("900", changes.get(TextMiningConfig.STATEMENTS_MAX_PARAGRAPH_CHARS_KEY));
    }
}
