package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.acropolis.image.ImageAnalysis;
import com.aresstack.enterpriseai.astu.acropolis.image.ImageAnalysisCache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bildauswertungen des Lesebereichs als Dateien, je Cache-Schlüssel (Bild und Version) eine; der Dateiname ist der
 * SHA-256 des Schlüssels. Ein unlesbarer Eintrag zählt als nicht vorhanden.
 */
final class FileImageAnalysisCache implements ImageAnalysisCache {

    private static final Logger LOG = Logger.getLogger(FileImageAnalysisCache.class.getName());

    private final Path directory;

    FileImageAnalysisCache(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("directory must not be null");
        }
        this.directory = directory;
    }

    @Override
    public ImageAnalysis get(String cacheKey) {
        Path file = fileOf(cacheKey);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (IOException e) {
            LOG.log(Level.FINE, "Bildauswertung nicht lesbar: " + file, e);
            return null;
        }
        if (!cacheKey.equals(properties.getProperty("key"))) {
            return null;
        }
        return new ImageAnalysis(properties.getProperty("imageId", ""), properties.getProperty("title", ""),
                properties.getProperty("version", ""), properties.getProperty("ocr", ""),
                properties.getProperty("description", ""));
    }

    @Override
    public void put(String cacheKey, ImageAnalysis analysis) {
        Properties properties = new Properties();
        properties.setProperty("key", cacheKey);
        properties.setProperty("imageId", analysis.imageId() == null ? "" : analysis.imageId());
        properties.setProperty("title", analysis.title());
        properties.setProperty("version", analysis.version());
        properties.setProperty("ocr", analysis.ocrText());
        properties.setProperty("description", analysis.description());
        Path file = fileOf(cacheKey);
        try {
            Files.createDirectories(directory);
            Path temp = Files.createTempFile(directory, "image-", ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                properties.store(out, null);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Bildauswertung nicht speicherbar: " + file, e);
        }
    }

    private Path fileOf(String cacheKey) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(cacheKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (byte b : hash) {
                name.append(String.format("%02x", b & 0xFF));
            }
            return directory.resolve(name.append(".properties").toString());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 fehlt", e);
        }
    }
}
