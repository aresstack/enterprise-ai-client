package com.aresstack.enterpriseai.proasteion.help;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Help chapters bundled in the jar under {@code help/<language>/}, listed in {@code contents.txt} (written by the
 * build from {@code docs/help}). Reads through a class loader, so the help always matches the installed version.
 */
public final class ClasspathHelpDocuments implements HelpDocuments {

    /** Language of the bundled help; translations would add further folders next to it. */
    public static final String GERMAN = "de";
    static final String ROOT = "help/";
    static final String CONTENTS = "contents.txt";
    private static final int BUFFER = 8192;

    private final ClassLoader loader;
    private final String root;

    public ClasspathHelpDocuments(ClassLoader loader, String language) {
        if (loader == null || language == null || language.trim().isEmpty()) {
            throw new IllegalArgumentException("loader and language are required");
        }
        this.loader = loader;
        this.root = ROOT + language.trim() + "/";
    }

    /** The German help bundled with this module. */
    public static ClasspathHelpDocuments bundled() {
        return new ClasspathHelpDocuments(ClasspathHelpDocuments.class.getClassLoader(), GERMAN);
    }

    @Override
    public List<String> names() throws IOException {
        List<String> names = new ArrayList<String>();
        for (String line : load(CONTENTS).split("\n")) {
            if (!line.trim().isEmpty()) {
                names.add(line.trim());
            }
        }
        return Collections.unmodifiableList(names);
    }

    @Override
    public String read(String name) throws IOException {
        return load(name);
    }

    private String load(String name) throws IOException {
        InputStream in = loader.getResourceAsStream(root + name);
        if (in == null) {
            throw new FileNotFoundException(root + name);
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER];
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                bytes.write(buffer, 0, read);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }
}
