package com.aresstack.enterpriseai.proasteion.application.config;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Issue #120: grants are read from the configuration; without keys nothing may be read. */
public class FileAccessConfigTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static Properties minimal() {
        Properties p = new Properties();
        p.setProperty("chat.baseUrl", "https://ki.example/v1");
        p.setProperty("chat.model", "m");
        p.setProperty("chat.apiKeyRef", "k");
        p.setProperty("embedding.model", "e");
        p.setProperty("embedding.dimension", "8");
        return p;
    }

    @Test
    public void withoutKeysNothingIsGranted() throws Exception {
        AppConfig config = AppConfigLoader.fromProperties(minimal());
        assertFalse(config.fileAccess().allowAll());
        assertTrue(config.fileAccess().roots().isEmpty());
        assertFalse(config.fileAccess().policy().isAllowed(temp.newFile("a.txt").toPath()));
    }

    @Test
    public void grantedFoldersAndAllowAllAreRead() throws Exception {
        Path granted = temp.newFolder("granted").toPath();
        Path other = temp.newFolder("other").toPath();
        Properties p = minimal();
        p.setProperty(FileAccessConfig.ROOTS_KEY, granted + " ; \\\\server\\team\\wissen");
        AppConfig config = AppConfigLoader.fromProperties(p);
        assertEquals(Arrays.asList(granted.toString(), "\\\\server\\team\\wissen"), config.fileAccess().roots());
        assertTrue(config.fileAccess().policy().isAllowed(granted.resolve("a.txt")));
        assertFalse(config.fileAccess().policy().isAllowed(other.resolve("b.txt")));

        p.setProperty(FileAccessConfig.ALLOW_ALL_KEY, "true");
        assertTrue(AppConfigLoader.fromProperties(p).fileAccess().policy().isAllowed(other.resolve("b.txt")));
    }
}
