package com.aresstack.enterpriseai.app.config;

import org.junit.After;
import org.junit.Test;

import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class AppPathsTest {

    @After
    public void clearProperties() {
        System.clearProperty(AppPaths.HOME_PROPERTY);
        System.clearProperty(AppPaths.CONFIG_PROPERTY);
    }

    @Test
    public void systemPropertiesOverrideUserDirectory() {
        System.setProperty(AppPaths.HOME_PROPERTY, "/tmp/eai-home");
        assertEquals(Paths.get("/tmp/eai-home").toAbsolutePath(), AppPaths.appDirectory());
        assertEquals(Paths.get("/tmp/eai-home", AppPaths.CONFIG_FILE_NAME).toAbsolutePath(), AppPaths.configFile());
        assertEquals(Paths.get("/tmp/eai-home", AppPaths.INDEX_DIRECTORY_NAME).toAbsolutePath(),
                AppPaths.defaultIndexDirectory());
        System.setProperty(AppPaths.CONFIG_PROPERTY, "/etc/eai/custom.properties");
        assertEquals(Paths.get("/etc/eai/custom.properties").toAbsolutePath(), AppPaths.configFile());
    }
}
