package com.aresstack.enterpriseai.proasteion.exedra.settings;

import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigLoader;
import com.aresstack.enterpriseai.proasteion.application.config.ChatConfig;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * {@code chat.assistant.displayName}: ohne Eintrag „Merlins Tochter“, der Standard steht nie in der Datei, ein eigener
 * Name bleibt, auch wenn die Stimme wechselt.
 */
public class AssistantDisplayNameSettingsTest {

    @Test
    public void withoutAnEntryTheAssistantIsMerlinsDaughter() throws Exception {
        AppConfig config = AppConfigLoader.fromProperties(SettingsMapperTest.valid());
        assertEquals("Merlins Tochter", ChatConfig.DEFAULT_ASSISTANT_DISPLAY_NAME);
        assertEquals(ChatConfig.DEFAULT_ASSISTANT_DISPLAY_NAME, config.chat().assistantDisplayName());
        assertEquals(ChatConfig.DEFAULT_ASSISTANT_DISPLAY_NAME, ChatConfig.assistantDisplayNameOf(config));
        assertEquals(ChatConfig.DEFAULT_ASSISTANT_DISPLAY_NAME, ChatConfig.assistantDisplayNameOf(null));

        SettingsForm form = SettingsMapper.fromProperties(new Properties());
        assertEquals(ChatConfig.DEFAULT_ASSISTANT_DISPLAY_NAME, form.assistantDisplayName());
        assertEquals(ChatConfig.DEFAULT_ASSISTANT_DISPLAY_NAME, form.defaultAssistantDisplayName());
        assertEquals(ChatConfig.DEFAULT_ASSISTANT_DISPLAY_NAME,
                SettingsMapper.firstStartDefaults().assistantDisplayName());
    }

    @Test
    public void theDefaultNameIsNeverWrittenToTheFile() {
        SettingsForm form = SettingsMapper.fromProperties(SettingsMapperTest.valid());
        assertEquals("", SettingsMapper.changes(form).get(ChatConfig.ASSISTANT_DISPLAY_NAME_KEY));
        Properties merged = SettingsMapper.merge(SettingsMapperTest.valid(), form);
        assertNull(merged.getProperty(ChatConfig.ASSISTANT_DISPLAY_NAME_KEY));
        // leeres Feld heißt Standard
        assertEquals("", SettingsMapper.changes(form.toBuilder().assistantDisplayName("  ").build())
                .get(ChatConfig.ASSISTANT_DISPLAY_NAME_KEY));
    }

    @Test
    public void anOwnNameIsStoredAndReadBack() throws Exception {
        SettingsForm form = SettingsMapper.fromProperties(SettingsMapperTest.valid()).toBuilder()
                .assistantDisplayName("  Morgana ").build();
        Properties merged = SettingsMapper.merge(SettingsMapperTest.valid(), form);
        assertEquals("Morgana", merged.getProperty(ChatConfig.ASSISTANT_DISPLAY_NAME_KEY));
        assertEquals("Morgana", SettingsMapper.fromProperties(merged).assistantDisplayName());
        assertEquals("Morgana", AppConfigLoader.fromProperties(merged).chat().assistantDisplayName());
    }

    @Test
    public void changingTheVoiceKeepsTheName() throws Exception {
        Properties p = SettingsMapperTest.valid();
        p.setProperty(ChatConfig.ASSISTANT_DISPLAY_NAME_KEY, "Morgana");
        SettingsForm form = SettingsMapper.fromProperties(p).toBuilder()
                .modelSelection("tts", "test-tts-voice").build();
        Properties merged = SettingsMapper.merge(p, form);
        assertEquals("test-tts-voice", merged.getProperty("model.tts"));
        assertEquals("Morgana", AppConfigLoader.fromProperties(merged).chat().assistantDisplayName());
        assertFalse(SettingsMapper.changes(form).get(ChatConfig.ASSISTANT_DISPLAY_NAME_KEY).isEmpty());
    }

    @Test
    public void theTemplateDocumentsTheKeyWithoutSettingIt() throws Exception {
        Properties template = SettingsMapperTest.template();
        assertNull(template.getProperty(ChatConfig.ASSISTANT_DISPLAY_NAME_KEY));
        org.junit.Assert.assertTrue(AppConfigLoader.exampleConfiguration().contains("#" + ChatConfig.ASSISTANT_DISPLAY_NAME_KEY + "="));
    }
}
