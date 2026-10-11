package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Angelos Fehler: einen Reiter per Maus in eine vorhandene Gruppe ziehen ging nicht (eine Gruppe mit einem Reiter hat
 * keine Lücke „zwischen zwei Gruppenreitern“). Und der Gruppendialog im Comic-Stil liefert Name und Farbe, Esc bricht ab.
 */
public class ReaderTabGroupsTest {

    private final ReaderLink a = new ReaderLink("conf", "confluence:x/page/1", "Dependencytrack", "", "");
    private final ReaderLink b = new ReaderLink("conf", "confluence:x/page/2", "SonarQube", "", "");
    private final ReaderLink c = new ReaderLink("conf", "confluence:x/page/3", "Vault", "", "");

    @Test
    public void droppingOntoTheTabOfAOneTabGroupJoinsIt() throws Exception {
        KnowledgeReaderFindTest.onEdt(() -> {
            KnowledgeReaderPanel reader = threeTabs();
            ReaderTabGroup group = reader.newGroup(a.key(), "Recherche", ReaderTabGroup.Tint.ORANGE);
            assertEquals(Arrays.asList(a.key(), b.key(), c.key()), reader.tabKeys());

            drag(reader, c.key(), reader.tabChip(a.key()), 0.75);

            assertSame("auf dem Gruppenreiter abgelegt: tritt bei", group, reader.groupOf(c.key()));
            assertEquals(Arrays.asList(a.key(), c.key(), b.key()), reader.tabKeys());
            assertNull(reader.groupOf(b.key()));
            assertTrue(reader.isPinned(c.key()));
        });
    }

    @Test
    public void droppingOntoTheLeftHalfOfAGroupTabJoinsInFrontOfIt() throws Exception {
        KnowledgeReaderFindTest.onEdt(() -> {
            KnowledgeReaderPanel reader = threeTabs();
            ReaderTabGroup group = reader.newGroup(b.key(), "", ReaderTabGroup.Tint.GREEN);

            drag(reader, c.key(), reader.tabChip(b.key()), 0.25);

            assertSame(group, reader.groupOf(c.key()));
            assertEquals(Arrays.asList(a.key(), c.key(), b.key()), reader.tabKeys());
        });
    }

    @Test
    public void droppingOntoTheGroupMarkerJoinsTheGroup() throws Exception {
        KnowledgeReaderFindTest.onEdt(() -> {
            KnowledgeReaderPanel reader = threeTabs();
            ReaderTabGroup group = reader.newGroup(b.key(), "Betrieb", ReaderTabGroup.Tint.BLUE);
            JComponent marker = reader.groupChip(group.id());
            assertNotNull(marker);

            drag(reader, a.key(), marker, 0.5);

            assertSame(group, reader.groupOf(a.key()));
            assertEquals("an den Anfang der Gruppe", Arrays.asList(a.key(), b.key(), c.key()), reader.tabKeys());
            assertSame(group, reader.groupOf(b.key()));
        });
    }

    @Test
    public void droppingBesideAGroupOrOnAFreeTabOnlyMoves() throws Exception {
        KnowledgeReaderFindTest.onEdt(() -> {
            KnowledgeReaderPanel reader = threeTabs();
            ReaderTabGroup group = reader.newGroup(b.key(), "Betrieb", ReaderTabGroup.Tint.BLUE);

            // vor die Gruppenmarke: verschoben, nicht beigetreten
            JComponent marker = reader.groupChip(group.id());
            drag(reader, c.key(), marker, -0.01);
            assertNull(reader.groupOf(c.key()));
            assertEquals(Arrays.asList(a.key(), c.key(), b.key()), reader.tabKeys());

            // auf einen Reiter ohne Gruppe: nur verschoben, wie bisher
            drag(reader, a.key(), reader.tabChip(c.key()), 0.75);
            assertNull(reader.groupOf(a.key()));
            assertEquals(Arrays.asList(c.key(), a.key(), b.key()), reader.tabKeys());

            // ein Gruppenreiter auf einen freien Reiter gezogen verlässt seine Gruppe
            drag(reader, b.key(), reader.tabChip(c.key()), 0.25);
            assertNull(reader.groupOf(b.key()));
            assertEquals(Arrays.asList(b.key(), c.key(), a.key()), reader.tabKeys());
        });
    }

    @Test
    public void groupFormReturnsNameAndChosenColor() throws Exception {
        KnowledgeReaderFindTest.onEdt(() -> {
            List<ReaderTabGroupDialog.Result> answers = new ArrayList<ReaderTabGroupDialog.Result>();
            ReaderTabGroupDialog.Form form = new ReaderTabGroupDialog.Form(ReaderTabGroupDialog.NEW_TITLE, "",
                    ReaderTabGroup.Tint.BLUE, ComicPalette.defaultPalette(), answers::add);
            assertEquals(ReaderTabGroupDialog.NEW_TITLE, form.heading().getText());
            assertEquals(ReaderTabGroup.Tint.BLUE, form.chosen());

            form.nameField().setText("  Recherche  ");
            press(form.swatch(ReaderTabGroup.Tint.GREEN));
            assertEquals(ReaderTabGroup.Tint.GREEN, form.chosen());
            form.okButton().doClick();

            assertEquals(1, answers.size());
            assertEquals("Recherche", answers.get(0).name());
            assertEquals(ReaderTabGroup.Tint.GREEN, answers.get(0).tint());
        });
    }

    @Test
    public void enterConfirmsAndEscapeCancels() throws Exception {
        KnowledgeReaderFindTest.onEdt(() -> {
            List<ReaderTabGroupDialog.Result> answers = new ArrayList<ReaderTabGroupDialog.Result>();
            ReaderTabGroupDialog.Form form = new ReaderTabGroupDialog.Form(ReaderTabGroupDialog.EDIT_TITLE,
                    "Alt", ReaderTabGroup.Tint.RED, ComicPalette.defaultPalette(), answers::add);
            form.nameField().setText("");
            form.nameField().postActionEvent();
            assertEquals(1, answers.size());
            assertEquals("leerer Name erlaubt", "", answers.get(0).name());
            assertEquals(ReaderTabGroup.Tint.RED, answers.get(0).tint());

            List<ReaderTabGroupDialog.Result> cancelled = new ArrayList<ReaderTabGroupDialog.Result>();
            ReaderTabGroupDialog.Form other = new ReaderTabGroupDialog.Form(ReaderTabGroupDialog.NEW_TITLE, "",
                    ReaderTabGroup.Tint.BLUE, ComicPalette.defaultPalette(), cancelled::add);
            other.getActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)).actionPerformed(null);
            assertEquals(1, cancelled.size());
            assertNull("Esc bricht ab", cancelled.get(0));
            other.cancelButton().doClick();
            assertEquals("nur einmal gemeldet", 1, cancelled.size());
            assertFalse(other.okButton().getText().isEmpty());
        });
    }

    private KnowledgeReaderPanel threeTabs() {
        KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette());
        reader.open(a, true);
        reader.open(b, true);
        reader.open(c, true);
        return reader;
    }

    /** Zieht den Reiter {@code key} mit der Maus auf {@code target}, an den Bruchteil seiner Breite. */
    private static void drag(KnowledgeReaderPanel reader, String key, Component target, double fraction) {
        layout(reader);
        JComponent source = reader.tabChip(key);
        Container strip = source.getParent();
        Point onStrip = new Point(target.getX() + (int) Math.floor(target.getWidth() * fraction),
                target.getY() + target.getHeight() / 2);
        Point local = SwingUtilities.convertPoint(strip, onStrip, source);
        source.dispatchEvent(mouse(source, MouseEvent.MOUSE_PRESSED, new Point(4, 4)));
        source.dispatchEvent(mouse(source, MouseEvent.MOUSE_DRAGGED, local));
        source.dispatchEvent(mouse(source, MouseEvent.MOUSE_RELEASED, local));
    }

    private static void layout(KnowledgeReaderPanel reader) {
        Container strip = reader.tabChip(reader.tabKeys().get(0)).getParent();
        strip.setSize(2000, 40);
        strip.doLayout();
    }

    private static void press(Component component) {
        component.dispatchEvent(mouse(component, MouseEvent.MOUSE_PRESSED, new Point(10, 10)));
    }

    private static MouseEvent mouse(Component source, int id, Point point) {
        int modifiers = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
        return new MouseEvent(source, id, System.currentTimeMillis(), modifiers, point.x, point.y, 1, false,
                MouseEvent.BUTTON1);
    }
}
