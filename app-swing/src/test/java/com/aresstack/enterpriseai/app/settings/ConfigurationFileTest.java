package com.aresstack.enterpriseai.app.settings;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Der Dateieditor ersetzt Zeilen an Ort und Stelle und lässt Kommentare, Reihenfolge und fremde Schlüssel stehen. */
public class ConfigurationFileTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static Map<String, String> set(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static final Map<String, String> NOTHING = Collections.emptyMap();

    @Test
    public void replacesTheActiveLineInPlace() {
        String text = "# Chat\nchat.model=alt\nchat.baseUrl=http://127.0.0.1:9/v1\n";
        assertEquals("# Chat\nchat.model=neu\nchat.baseUrl=http://127.0.0.1:9/v1\n",
                ConfigurationFile.apply(text, set("chat.model", "neu"), Collections.<String>emptySet()));
    }

    @Test
    public void activatesACommentedLineInsteadOfAppending() {
        String text = "# Optional:\n#embedding.baseUrl=http://127.0.0.1:1/alt\n# nur bei Bedarf\nx=1\n";
        String updated = ConfigurationFile.apply(text, set("embedding.baseUrl", "http://127.0.0.1:9/v1"),
                Collections.<String>emptySet());
        assertEquals("# Optional:\nembedding.baseUrl=http://127.0.0.1:9/v1\n# nur bei Bedarf\nx=1\n", updated);
    }

    @Test
    public void appendsUnknownKeysUnderOneHeader() {
        String once = ConfigurationFile.apply("a=1\n", set("neu.key", "v"), Collections.<String>emptySet());
        assertEquals("a=1\n\n" + ConfigurationFile.APPENDED_HEADER + "\nneu.key=v\n", once);
        String twice = ConfigurationFile.apply(once, set("neu.zwei", "w"), Collections.<String>emptySet());
        assertEquals(once + "neu.zwei=w\n", twice);
        assertEquals(1, twice.split(java.util.regex.Pattern.quote(ConfigurationFile.APPENDED_HEADER), -1).length - 1);
    }

    @Test
    public void removedKeysAreCommentedOutNotDeleted() {
        String updated = ConfigurationFile.apply("chat.systemPrompt=Sei knapp.\nb=2\n", NOTHING,
                Collections.singleton("chat.systemPrompt"));
        assertTrue(updated, updated.startsWith("#chat.systemPrompt=Sei knapp.\n"));
        assertTrue(updated.endsWith("b=2\n"));
    }

    @Test
    public void continuationLinesAreReplacedAsOneLogicalLine() {
        String text = "a=eins \\\n    zwei\nb=2\n";
        assertEquals("a=drei\nb=2\n", ConfigurationFile.apply(text, set("a", "drei"), Collections.<String>emptySet()));
    }

    @Test
    public void duplicatesAreMadeUnique() {
        String updated = ConfigurationFile.apply("a=1\nb=0\na=2\n", set("a", "3"), Collections.<String>emptySet());
        assertEquals("#a=1\nb=0\na=3\n", updated);
    }

    @Test
    public void keepsWindowsLineEndings() {
        assertEquals("a=2\r\nb=2\r\n",
                ConfigurationFile.apply("a=1\r\nb=2\r\n", set("a", "2"), Collections.<String>emptySet()));
    }

    @Test
    public void valuesSurviveTheRoundTripThroughPropertiesLoad() throws Exception {
        Path path = tmp.getRoot().toPath().resolve("sub").resolve("app.properties");
        ConfigurationFile file = new ConfigurationFile(path);
        assertFalse(file.exists());
        Map<String, String> values = set(
                "pfad", "C:\\Daten\\index",
                "mehrzeilig", "erste Zeile\nzweite Zeile",
                "umlaute", "Größe: 5 = fünf",
                "fuehrend", "  mit Leerzeichen davor",
                "tab", "a\tb",
                "schluessel mit leer", "x");
        file.update(values, Collections.<String>emptySet(), "# Vorlage\n");
        Properties read = file.read();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            assertEquals(entry.getKey(), entry.getValue(), read.getProperty(entry.getKey()));
        }
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        assertTrue(text, text.startsWith("# Vorlage\n"));
    }

    @Test
    public void createsTheTemplateOnlyOnce() throws Exception {
        Path path = tmp.getRoot().toPath().resolve("app.properties");
        ConfigurationFile file = new ConfigurationFile(path);
        assertTrue(file.createIfMissing("#a=1\n"));
        assertFalse(file.createIfMissing("#b=2\n"));
        assertEquals("#a=1\n", new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
        assertNull(file.read().getProperty("a"));
    }

    @Test
    public void missingFileStartsFromTheTemplate() throws Exception {
        Path path = tmp.getRoot().toPath().resolve("app.properties");
        ConfigurationFile file = new ConfigurationFile(path);
        file.update(set("chat.model", "m"), Collections.<String>emptySet(), "# Kopf\n#chat.model=beispiel\n");
        assertEquals("# Kopf\nchat.model=m\n", new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
        assertEquals("m", file.read().getProperty("chat.model"));
    }

    @Test
    public void setWinsOverRemoveForTheSameKey() {
        String updated = ConfigurationFile.apply("a=1\n", set("a", "2"), Collections.singleton("a"));
        assertEquals("a=2\n", updated);
    }
}
