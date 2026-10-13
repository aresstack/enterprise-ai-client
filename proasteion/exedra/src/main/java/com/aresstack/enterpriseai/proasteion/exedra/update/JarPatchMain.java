package com.aresstack.enterpriseai.proasteion.exedra.update;

import com.aresstack.enterpriseai.proasteion.application.update.JarPatch;
import com.aresstack.enterpriseai.proasteion.application.update.JarPatchBuilder;
import com.aresstack.enterpriseai.proasteion.application.update.JarPatchException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Logger;

/**
 * Command line for the build (Gradle task {@code fatJarPatch}):
 * {@code JarPatchMain <base.jar> <target.jar> <patch.zip> [changes.txt]}. Exits non-zero when no valid patch can be
 * created.
 */
public final class JarPatchMain {

    private static final Logger LOG = Logger.getLogger(JarPatchMain.class.getName());

    private JarPatchMain() {
    }

    public static void main(String[] args) throws IOException, JarPatchException {
        if (args.length < 3 || args.length > 4) {
            throw new IllegalArgumentException("Aufruf: <basis.jar> <ziel.jar> <patch.zip> [changes.txt]");
        }
        String changes = args.length == 4
                ? new String(Files.readAllBytes(new File(args[3]).toPath()), StandardCharsets.UTF_8) : "";
        File out = new File(args[2]);
        JarPatch patch = new JarPatchBuilder().build(new File(args[0]), new File(args[1]), changes, out);
        LOG.info("Update-Paket " + out.getName() + ": " + patch.baseVersion() + " -> " + patch.targetVersion()
                + ", " + patch.changedEntries().size() + " geändert, " + patch.removedEntries().size()
                + " entfernt, " + out.length() + " Bytes");
    }
}
