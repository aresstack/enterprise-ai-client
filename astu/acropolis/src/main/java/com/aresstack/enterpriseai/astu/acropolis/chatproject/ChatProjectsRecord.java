package com.aresstack.enterpriseai.astu.acropolis.chatproject;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything {@link ChatProjectStore} persists: the project tree and one assignment per chat that was ever moved.
 * An assignment without project means "chat without project"; it is kept because its generation must survive a
 * restart (otherwise a chat moved out of a project would get its old scope back).
 */
public final class ChatProjectsRecord {

    private List<ChatProjectRecord> projects = new ArrayList<ChatProjectRecord>();
    private List<Assignment> assignments = new ArrayList<Assignment>();

    /** For deserialisation. */
    public ChatProjectsRecord() {
    }

    public ChatProjectsRecord(List<ChatProjectRecord> projects, List<Assignment> assignments) {
        this.projects = new ArrayList<ChatProjectRecord>(projects);
        this.assignments = new ArrayList<Assignment>(assignments);
    }

    public List<ChatProjectRecord> getProjects() {
        return projects != null ? projects : new ArrayList<ChatProjectRecord>();
    }

    public List<Assignment> getAssignments() {
        return assignments != null ? assignments : new ArrayList<Assignment>();
    }

    /** The project of one chat ({@code null} = none) and how often the chat was moved. */
    public static final class Assignment {

        private String chatId;
        private String projectId;
        private long generation;

        /** For deserialisation. */
        public Assignment() {
        }

        public Assignment(String chatId, String projectId, long generation) {
            this.chatId = chatId;
            this.projectId = projectId;
            this.generation = generation;
        }

        public String getChatId() {
            return chatId != null ? chatId : "";
        }

        public String getProjectId() {
            return projectId == null || projectId.isEmpty() ? null : projectId;
        }

        public long getGeneration() {
            return generation;
        }
    }
}
