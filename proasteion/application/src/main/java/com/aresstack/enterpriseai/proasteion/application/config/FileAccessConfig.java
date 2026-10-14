package com.aresstack.enterpriseai.proasteion.application.config;

import com.aresstack.enterpriseai.source.api.files.LocalFileAccessPolicy;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Folders the user granted for reading local files and network drives (issue #120): {@value #ROOTS_KEY} lists them
 * separated by {@value #ROOT_SEPARATOR} (mapped drives and UNC paths alike), {@value #ALLOW_ALL_KEY} opts into reading
 * everything the user account can read. Both default to no access; granting never starts indexing. A configured local
 * files source always grants its own folder. Applies without restart.
 */
public final class FileAccessConfig {

    public static final String ALLOW_ALL_KEY = "files.access.allowAll";
    public static final String ROOTS_KEY = "files.access.roots";
    public static final String ROOT_SEPARATOR = ";";

    private final boolean allowAll;
    private final List<String> roots;
    private final LocalFileAccessPolicy policy;

    public FileAccessConfig(boolean allowAll, List<String> roots) {
        List<String> cleaned = new ArrayList<String>();
        List<Path> paths = new ArrayList<Path>();
        if (roots != null) {
            for (String root : roots) {
                String trimmed = root == null ? "" : root.trim();
                if (trimmed.isEmpty() || cleaned.contains(trimmed)) {
                    continue;
                }
                cleaned.add(trimmed);
                try {
                    paths.add(Paths.get(trimmed));
                } catch (InvalidPathException ignored) {
                    // An unusable entry grants nothing.
                }
            }
        }
        this.allowAll = allowAll;
        this.roots = Collections.unmodifiableList(cleaned);
        this.policy = LocalFileAccessPolicy.of(allowAll, paths);
    }

    public static FileAccessConfig defaults() {
        return new FileAccessConfig(false, Collections.<String>emptyList());
    }

    /** Split the stored {@value #ROOTS_KEY} value into folders. */
    public static List<String> splitRoots(String value) {
        List<String> roots = new ArrayList<String>();
        if (value != null) {
            for (String part : value.split(ROOT_SEPARATOR)) {
                if (!part.trim().isEmpty()) {
                    roots.add(part.trim());
                }
            }
        }
        return roots;
    }

    /** Join folders into the stored {@value #ROOTS_KEY} value. */
    public static String joinRoots(List<String> roots) {
        StringBuilder value = new StringBuilder();
        for (String root : roots) {
            if (root != null && !root.trim().isEmpty()) {
                if (value.length() > 0) {
                    value.append(ROOT_SEPARATOR);
                }
                value.append(root.trim());
            }
        }
        return value.toString();
    }

    /** Whether the user opted into reading every path the user account can read. */
    public boolean allowAll() {
        return allowAll;
    }

    /** The granted folders as entered. */
    public List<String> roots() {
        return roots;
    }

    /** The policy the file adapters check every access against. */
    public LocalFileAccessPolicy policy() {
        return policy;
    }

    @Override
    public String toString() {
        return "FileAccessConfig[allowAll=" + allowAll + ", roots=" + roots + "]";
    }
}
