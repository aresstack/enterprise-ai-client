package com.aresstack.enterpriseai.proasteion.exedra.document;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

/** #132: one dialog logic for every export; the chosen file type alone decides format and extension. */
public class ExportFileTypesTest {

    private final ExportFileTypes<ExportFormat> types = ExportFileTypes.pages();
    private final File folder = new File("export");

    private File target(String name, ExportFormat format) {
        return types.target(new File(folder, name), types.of(format));
    }

    @Test
    public void pdfIsPreselectedThenWordAndMarkdown() {
        assertEquals(ExportFormat.PDF, types.preselected().format());
        assertEquals(3, types.types().size());
        assertEquals("pdf", types.types().get(0).extension());
    }

    @Test
    public void switchingTheTypeNeverStacksExtensions() {
        File pdf = target("Konzept", ExportFormat.PDF);
        assertEquals(new File(folder, "Konzept.pdf"), pdf);
        File docx = target(pdf.getName(), ExportFormat.DOCX);
        assertEquals(new File(folder, "Konzept.docx"), docx);
        assertEquals(new File(folder, "Konzept.md"), target(docx.getName(), ExportFormat.MARKDOWN));
        assertEquals(new File(folder, "Konzept.pdf"), target("Konzept.MD", ExportFormat.PDF));
        assertEquals(new File(folder, "Konzept v1.2.pdf"), target("Konzept v1.2", ExportFormat.PDF));
        assertEquals(new File(folder, "notiz.txt.docx"), target("notiz.txt", ExportFormat.DOCX));
    }

    @Test
    public void theSuggestedNameHasNoExtension() {
        assertEquals("Konzept", types.withoutExtension("Konzept.pdf"));
        assertEquals("Konzept", types.withoutExtension("Konzept"));
    }
}
