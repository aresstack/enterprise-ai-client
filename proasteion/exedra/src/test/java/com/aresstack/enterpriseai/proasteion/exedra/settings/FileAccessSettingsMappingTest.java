package com.aresstack.enterpriseai.proasteion.exedra.settings;

import com.aresstack.enterpriseai.proasteion.application.config.FileAccessConfig;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Issue #120: folder grants and the allow-all opt-in persist; no access is the default and stays out. */
public class FileAccessSettingsMappingTest {

    @Test
    public void noAccessIsTheDefaultAndNotWritten() {
        SettingsForm form = SettingsMapper.fromProperties(new Properties());
        assertFalse(form.fileAccessAllowAll());
        assertEquals(Collections.<String>emptyList(), form.fileAccessRoots());
        Map<String, String> changes = SettingsMapper.changes(form);
        assertEquals("", changes.get(FileAccessConfig.ALLOW_ALL_KEY));
        assertEquals("", changes.get(FileAccessConfig.ROOTS_KEY));
    }

    @Test
    public void grantsRoundTripIncludingNetworkPaths() {
        Properties stored = new Properties();
        stored.setProperty(FileAccessConfig.ROOTS_KEY, "C:\\Projekte;P:\\Fachkonzepte;\\\\server\\team\\wissen");
        stored.setProperty(FileAccessConfig.ALLOW_ALL_KEY, "true");
        SettingsForm form = SettingsMapper.fromProperties(stored);
        assertTrue(form.fileAccessAllowAll());
        assertEquals(Arrays.asList("C:\\Projekte", "P:\\Fachkonzepte", "\\\\server\\team\\wissen"),
                form.fileAccessRoots());
        Map<String, String> changes = SettingsMapper.changes(form);
        assertEquals("true", changes.get(FileAccessConfig.ALLOW_ALL_KEY));
        assertEquals("C:\\Projekte;P:\\Fachkonzepte;\\\\server\\team\\wissen", changes.get(FileAccessConfig.ROOTS_KEY));
    }
}
