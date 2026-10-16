package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import com.aresstack.enterpriseai.proasteion.exedra.ui.memory.MemoryBrowser;
import com.aresstack.enterpriseai.proasteion.exedra.ui.memory.MemoryManagerPanel;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.util.Arrays;
import java.util.function.BiConsumer;

/**
 * The project view (#143): name, project instructions, shared project files, the project's memories (the memory
 * management view of #144) and an input field that starts a new chat in the project with the typed question.
 */
final class ChatProjectView extends JPanel {

    static final String SEND_LABEL = "Neuen Chat starten";

    private final WorkspaceActions actions;
    private final BiConsumer<String, String> startChat;
    private final JLabel title = new JLabel();
    private final JTextArea instructions = new JTextArea(5, 40);
    private final DefaultListModel<ChatProjectFileItem> files = new DefaultListModel<ChatProjectFileItem>();
    private final JList<ChatProjectFileItem> fileList = new JList<ChatProjectFileItem>(files);
    private final JPanel memories = new JPanel(new BorderLayout());
    private final JTextArea question = new JTextArea(3, 40);
    private final JButton send = new JButton(SEND_LABEL);
    private String projectId;

    /**
     * @param startChat called with project id and question when the user sends from the input field
     */
    ChatProjectView(WorkspaceActions actions, BiConsumer<String, String> startChat, ComicPalette palette) {
        super(new BorderLayout());
        this.actions = actions;
        this.startChat = startChat;
        setBackground(palette.getSurface());
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 6f));
        add(title, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        instructions.setLineWrap(true);
        instructions.setWrapStyleWord(true);
        JButton saveInstructions = new JButton("Anweisungen speichern");
        saveInstructions.addActionListener(event -> {
            if (projectId != null) {
                actions.updateProjectInstructionsRequested(projectId, instructions.getText());
            }
        });
        body.add(section("Projektanweisungen", new JScrollPane(instructions), saveInstructions));

        JButton addFiles = new JButton("Dateien hinzufügen …");
        addFiles.addActionListener(event -> chooseFiles());
        JButton removeFile = new JButton("Entfernen");
        removeFile.addActionListener(event -> {
            ChatProjectFileItem file = fileList.getSelectedValue();
            if (projectId != null && file != null) {
                actions.removeProjectFileRequested(projectId, file.id());
                reload();
            }
        });
        fileList.setVisibleRowCount(5);
        body.add(section("Projektdateien (nur für Chats dieses Projekts)", new JScrollPane(fileList), addFiles,
                removeFile));

        memories.setOpaque(false);
        memories.setPreferredSize(new Dimension(400, 220));
        body.add(section("Projekterinnerungen", memories));

        add(new JScrollPane(body), BorderLayout.CENTER);

        question.setLineWrap(true);
        question.setWrapStyleWord(true);
        send.addActionListener(event -> sendQuestion());
        add(section("Frage an dieses Projekt", new JScrollPane(question), send), BorderLayout.SOUTH);
    }

    /** Shows the project {@code project}. */
    void show(ChatProjectItem project) {
        projectId = project.id();
        title.setText(project.name());
        instructions.setText(project.instructions() == null ? "" : project.instructions());
        question.setText("");
        reload();
    }

    String projectId() {
        return projectId;
    }

    JTextArea questionField() {
        return question;
    }

    JButton sendButton() {
        return send;
    }

    void reload() {
        files.clear();
        memories.removeAll();
        if (projectId == null) {
            return;
        }
        for (ChatProjectFileItem file : actions.projectFiles(projectId)) {
            files.addElement(file);
        }
        // The memory management view of #144: one owner (this project), H2 only, deletes via its own use case.
        MemoryBrowser browser = actions.projectMemories(projectId);
        if (browser == null) {
            memories.add(new JLabel("Kein Erinnerungsspeicher verfügbar."), BorderLayout.NORTH);
        } else {
            memories.add(new MemoryManagerPanel(browser), BorderLayout.CENTER);
        }
        memories.revalidate();
        memories.repaint();
    }

    private void sendQuestion() {
        String text = question.getText().trim();
        if (projectId == null || text.isEmpty()) {
            return;
        }
        question.setText("");
        startChat.accept(projectId, text);
    }

    private void chooseFiles() {
        if (projectId == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        String problem = actions.addProjectFilesRequested(projectId, Arrays.asList(chooser.getSelectedFiles()));
        if (problem != null) {
            JOptionPane.showMessageDialog(this, problem, "Projektdateien", JOptionPane.WARNING_MESSAGE);
        }
        reload();
    }

    private static JPanel section(String heading, Component content, JButton... buttons) {
        JPanel section = new JPanel(new BorderLayout(0, 4));
        section.setOpaque(false);
        section.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        section.add(new JLabel(heading), BorderLayout.NORTH);
        section.add(content, BorderLayout.CENTER);
        if (buttons.length > 0) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            row.setOpaque(false);
            for (JButton button : buttons) {
                row.add(button);
                row.add(Box.createRigidArea(new Dimension(6, 0)));
            }
            section.add(row, BorderLayout.SOUTH);
        }
        section.setAlignmentX(LEFT_ALIGNMENT);
        return section;
    }
}
