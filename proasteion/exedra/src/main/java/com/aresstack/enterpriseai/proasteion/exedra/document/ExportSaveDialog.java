package com.aresstack.enterpriseai.proasteion.exedra.document;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The one save dialog of every export (#132): the formats as file type filters, the first (PDF) preselected, the
 * suggested name without extension. The chosen filter decides format and extension; an existing file is replaced only
 * after confirmation, asked for the final name with its extension. On the EDT.
 */
public final class ExportSaveDialog {

    /** The user's choice: the final file and the format to write it in. */
    public static final class Choice<T> {
        private final File file;
        private final T format;

        public Choice(File file, T format) {
            this.file = file;
            this.format = format;
        }

        public File file() {
            return file;
        }

        public T format() {
            return format;
        }
    }

    private ExportSaveDialog() {
    }

    /** Show the dialog; {@code null} if the user cancelled or did not want to replace an existing file. */
    public static <T> Choice<T> show(Component parent, String title, String suggestedName, ExportFileTypes<T> types) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setAcceptAllFileFilterUsed(false);
        Map<FileFilter, ExportFileTypes.Type<T>> filters = new LinkedHashMap<FileFilter, ExportFileTypes.Type<T>>();
        FileFilter preselected = null;
        for (ExportFileTypes.Type<T> type : types.types()) {
            FileFilter filter = new FileNameExtensionFilter(type.label(), type.extension());
            filters.put(filter, type);
            chooser.addChoosableFileFilter(filter);
            if (type == types.preselected()) {
                preselected = filter;
            }
        }
        chooser.setFileFilter(preselected);
        // Without extension: the chosen file type adds it when saving.
        chooser.setSelectedFile(new File(types.withoutExtension(suggestedName)));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return null;
        }
        ExportFileTypes.Type<T> type = filters.get(chooser.getFileFilter());
        if (type == null) {
            type = types.preselected();
        }
        return confirmed(parent, title, types.target(chooser.getSelectedFile(), type), type.format());
    }

    /** The choice if {@code file} is new or the user agrees to replace it; {@code null} otherwise. */
    static <T> Choice<T> confirmed(Component parent, String title, File file, T format) {
        if (file.exists() && JOptionPane.showConfirmDialog(parent, file.getName() + " gibt es schon. Ersetzen?", title,
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return null;
        }
        return new Choice<T>(file, format);
    }
}
