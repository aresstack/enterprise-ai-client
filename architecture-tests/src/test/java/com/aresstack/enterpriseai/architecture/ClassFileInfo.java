package com.aresstack.enterpriseai.architecture;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Liest aus einer Klassendatei, was ArchUnit nicht liefert: die Bytecode-Version (Java 8 = Major 52) und
 * die String-Konstanten des Konstantenpools (hart codierte URLs, Modellnamen, Schlüssel).
 */
final class ClassFileInfo {

    static final int JAVA_8_MAJOR = 52;

    private final String className;
    private final int majorVersion;
    private final List<String> stringConstants;

    private ClassFileInfo(String className, int majorVersion, List<String> stringConstants) {
        this.className = className;
        this.majorVersion = majorVersion;
        this.stringConstants = Collections.unmodifiableList(new ArrayList<String>(stringConstants));
    }

    String className() {
        return className;
    }

    int majorVersion() {
        return majorVersion;
    }

    /** Nur Konstanten, die als {@code CONSTANT_String} (also als Literal im Code) vorkommen. */
    List<String> stringConstants() {
        return stringConstants;
    }

    static ClassFileInfo read(File classFile) {
        InputStream in = null;
        try {
            in = new FileInputStream(classFile);
            return read(readFully(in));
        } catch (IOException e) {
            throw new IllegalStateException("Klassendatei nicht lesbar: " + classFile, e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // bereits gelesen
                }
            }
        }
    }

    /** Klassendatei einer geladenen Klasse (z. B. eines Fixtures) vom Klassenpfad. */
    static ClassFileInfo read(Class<?> type) {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        InputStream in = type.getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Klassendatei nicht gefunden: " + resource);
        }
        try {
            return read(readFully(in));
        } catch (IOException e) {
            throw new IllegalStateException("Klassendatei nicht lesbar: " + resource, e);
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // bereits gelesen
            }
        }
    }

    static ClassFileInfo read(byte[] bytes) throws IOException {
        DataInputStream data = new DataInputStream(new java.io.ByteArrayInputStream(bytes));
        if (data.readInt() != 0xCAFEBABE) {
            throw new IOException("keine Klassendatei (Magic fehlt)");
        }
        data.readUnsignedShort(); // minor
        int major = data.readUnsignedShort();
        int count = data.readUnsignedShort();
        String[] utf8 = new String[count];
        int[] classNameIndex = new int[count];
        List<Integer> stringIndexes = new ArrayList<Integer>();
        for (int i = 1; i < count; i++) {
            int tag = data.readUnsignedByte();
            switch (tag) {
                case 1: // CONSTANT_Utf8
                    utf8[i] = data.readUTF();
                    break;
                case 3: // Integer
                case 4: // Float
                    data.readInt();
                    break;
                case 5: // Long
                case 6: // Double
                    data.readLong();
                    i++; // belegt zwei Einträge
                    break;
                case 7: // Class
                    classNameIndex[i] = data.readUnsignedShort();
                    break;
                case 8: // String
                    stringIndexes.add(data.readUnsignedShort());
                    break;
                case 16: // MethodType
                case 19: // Module
                case 20: // Package
                    data.readUnsignedShort();
                    break;
                case 9: // Fieldref
                case 10: // Methodref
                case 11: // InterfaceMethodref
                case 12: // NameAndType
                case 17: // Dynamic
                case 18: // InvokeDynamic
                    data.readUnsignedShort();
                    data.readUnsignedShort();
                    break;
                case 15: // MethodHandle
                    data.readUnsignedByte();
                    data.readUnsignedShort();
                    break;
                default:
                    throw new IOException("unbekannter Konstantenpool-Tag " + tag);
            }
        }
        data.readUnsignedShort(); // access flags
        int thisClass = data.readUnsignedShort();
        String className = utf8[classNameIndex[thisClass]].replace('/', '.');
        List<String> strings = new ArrayList<String>();
        for (Integer index : stringIndexes) {
            if (utf8[index] != null) {
                strings.add(utf8[index]);
            }
        }
        return new ClassFileInfo(className, major, strings);
    }

    /** Alle {@code .class}-Dateien unterhalb der Verzeichnisse, ohne {@code module-info}. */
    static List<File> classFilesUnder(List<File> directories) {
        List<File> result = new ArrayList<File>();
        for (File directory : directories) {
            collect(directory, result);
        }
        return result;
    }

    private static void collect(File directory, List<File> result) {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        java.util.Arrays.sort(children);
        for (File child : children) {
            if (child.isDirectory()) {
                collect(child, result);
            } else if (child.getName().endsWith(".class") && !child.getName().equals("module-info.class")) {
                result.add(child);
            }
        }
    }

    private static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
