package com.aresstack.enterpriseai.model.sidecar;

import com.aresstack.enterpriseai.domain.localruntime.JavaRuntimeInstallation;
import com.aresstack.enterpriseai.model.api.JavaRuntimeProbe;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prüft eine Java-Installation mit {@code java -XshowSettings:properties -version} (wie {@code java -version} im
 * PowerShell-Startskript, dazu Hersteller und Version aus den System-Properties). Die Ausgabe geht in eine
 * Temporärdatei, damit ein hängender Prozess nach dem Timeout beendet werden kann, statt beim Lesen zu blockieren.
 */
public final class ProcessJavaRuntimeProbe implements JavaRuntimeProbe {

    private static final Logger LOG = Logger.getLogger(ProcessJavaRuntimeProbe.class.getName());
    private static final Pattern VERSION_PROPERTY = Pattern.compile("(?m)^\\s*java\\.version\\s*=\\s*(\\S+)\\s*$");
    private static final Pattern VENDOR_PROPERTY = Pattern.compile("(?m)^\\s*java\\.vendor\\s*=\\s*(.+?)\\s*$");
    private static final Pattern QUOTED_VERSION = Pattern.compile("\"([0-9][0-9._\\-a-zA-Z+]*)\"");

    private final long timeoutMillis;

    public ProcessJavaRuntimeProbe(long timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis must be positive");
        }
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public JavaRuntimeInstallation inspect(Path javaExecutable) {
        Path executable = resolveExecutable(javaExecutable);
        if (executable == null) {
            return null;
        }
        File output = null;
        Process process = null;
        try {
            output = File.createTempFile("java-probe", ".txt");
            process = new ProcessBuilder(Arrays.asList(executable.toString(), "-XshowSettings:properties",
                    "-version")).redirectErrorStream(true).redirectOutput(output).start();
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                LOG.info("Java-Prüfung abgebrochen (Timeout): " + executable);
                return null;
            }
            if (process.exitValue() != 0) {
                return null;
            }
            return parse(executable, new String(Files.readAllBytes(output.toPath()), Charset.defaultCharset()));
        } catch (IOException e) {
            LOG.log(Level.FINE, "Java nicht ausführbar: " + executable, e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (output != null && !output.delete()) {
                output.deleteOnExit();
            }
        }
    }

    /** {@code java}/{@code java.exe} selbst oder das Installationsverzeichnis mit {@code bin/java(.exe)}. */
    static Path resolveExecutable(Path path) {
        if (path == null) {
            return null;
        }
        if (Files.isRegularFile(path)) {
            return path.toAbsolutePath().normalize();
        }
        if (Files.isDirectory(path)) {
            for (String name : new String[]{"java.exe", "java"}) {
                Path candidate = path.resolve("bin").resolve(name);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath().normalize();
                }
            }
        }
        return null;
    }

    static JavaRuntimeInstallation parse(Path executable, String output) {
        String version = null;
        Matcher property = VERSION_PROPERTY.matcher(output);
        if (property.find()) {
            version = property.group(1);
        } else {
            Matcher quoted = QUOTED_VERSION.matcher(output);
            if (quoted.find()) {
                version = quoted.group(1);
            }
        }
        if (version == null) {
            return null;
        }
        Matcher vendor = VENDOR_PROPERTY.matcher(output);
        return new JavaRuntimeInstallation(executable, version, JavaRuntimeInstallation.majorOf(version),
                vendor.find() ? vendor.group(1) : "");
    }
}
