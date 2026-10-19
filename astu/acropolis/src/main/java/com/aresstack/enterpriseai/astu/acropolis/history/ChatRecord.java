package com.aresstack.enterpriseai.astu.acropolis.history;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein gespeicherter Chat (aus askai-java8 arch), geschlüsselt über die Kennung seiner Unterhaltung: Titel,
 * Zeitpunkte und die geordneten Nachrichten.
 */
public final class ChatRecord {

    private String id;
    private String title;
    /** Vom Nutzer umbenannt: der Titel folgt dann nicht mehr der ersten Nachricht. */
    private boolean titleEdited;
    private long createdAt;
    private long modifiedAt;
    private List<ChatMessageRecord> messages = new ArrayList<ChatMessageRecord>();
    /** Der zuletzt verwendete Wissenskontext (nur Kennungen); {@code null} ohne oder bei älteren Chats. */
    private KnowledgeContextRecord knowledgeContext;
    /** Composer switch "Browser" (#141); {@code null} in older chats means off. */
    private Boolean browserAllowed;
    /** Composer switch "Wissensquellen" (#141); {@code null} in older chats means on. */
    private Boolean knowledgeAllowed;
    /** Tool rounds of a turn with the browser switch on (#141); {@code null} or 0: the default. */
    private Integer browserBudget;
    /** Parallel tabs of browser_open_many (#141); {@code null} or 0: the default. */
    private Integer browserTabs;
    /** Maximum random delay before a tab in ms (#141); {@code null}: none. */
    private Integer browserTabDelay;
    /** Human Emulation and mouse movements of the chat's browser (#141); {@code null}: off. */
    private Boolean humanEmulation;
    private Boolean humanMouse;
    /** Browser profile Intern of the chat (#141); {@code null}: Extern, the default. */
    private Boolean browserInternal;

    /** Für die Deserialisierung. */
    public ChatRecord() {
    }

    public ChatRecord(String id, long createdAt) {
        this.id = id;
        this.createdAt = createdAt;
        this.modifiedAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isTitleEdited() {
        return titleEdited;
    }

    /** Setzt einen vom Nutzer gewählten Titel. */
    public void rename(String newTitle) {
        this.title = newTitle;
        this.titleEdited = true;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(long modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    public List<ChatMessageRecord> getMessages() {
        if (messages == null) {
            messages = new ArrayList<ChatMessageRecord>();
        }
        return messages;
    }

    public boolean isEmpty() {
        return getMessages().isEmpty();
    }

    /** Wie viele Anhänge alle Nachrichten zusammen haben. */
    public int attachmentCount() {
        int count = 0;
        for (ChatMessageRecord message : getMessages()) {
            count += message.getAttachments().size();
        }
        return count;
    }

    /** Der zuletzt verwendete Wissenskontext; {@code null}, wenn keiner gespeichert ist. */
    public KnowledgeContextRecord getKnowledgeContext() {
        return knowledgeContext;
    }

    public void setKnowledgeContext(KnowledgeContextRecord knowledgeContext) {
        this.knowledgeContext = knowledgeContext;
    }

    /** The chat's browser switch (#141): off unless saved as on. */
    public boolean isBrowserAllowed() {
        return Boolean.TRUE.equals(browserAllowed);
    }

    public void setBrowserAllowed(boolean browserAllowed) {
        this.browserAllowed = browserAllowed;
    }

    /** The chat's knowledge switch (#141): on unless saved as off. */
    public boolean isKnowledgeAllowed() {
        return !Boolean.FALSE.equals(knowledgeAllowed);
    }

    public void setKnowledgeAllowed(boolean knowledgeAllowed) {
        this.knowledgeAllowed = knowledgeAllowed;
    }

    /** The chat's browser budget in tool rounds (#141), 0 when the chat keeps the default. */
    public int getBrowserBudget() {
        return browserBudget == null ? 0 : Math.max(0, browserBudget);
    }

    public void setBrowserBudget(int browserBudget) {
        this.browserBudget = browserBudget <= 0 ? null : browserBudget;
    }

    /** Parallel tabs of browser_open_many (#141), 0 when the chat keeps the default. */
    public int getBrowserTabs() {
        return browserTabs == null ? 0 : Math.max(0, browserTabs);
    }

    public void setBrowserTabs(int browserTabs) {
        this.browserTabs = browserTabs <= 0 ? null : browserTabs;
    }

    /** Maximum random delay before a tab in ms (#141), 0 for none. */
    public int getBrowserTabDelay() {
        return browserTabDelay == null ? 0 : Math.max(0, browserTabDelay);
    }

    public void setBrowserTabDelay(int browserTabDelay) {
        this.browserTabDelay = browserTabDelay <= 0 ? null : browserTabDelay;
    }

    public boolean isHumanEmulation() {
        return Boolean.TRUE.equals(humanEmulation);
    }

    public void setHumanEmulation(boolean humanEmulation) {
        this.humanEmulation = humanEmulation ? Boolean.TRUE : null;
    }

    public boolean isHumanMouse() {
        return Boolean.TRUE.equals(humanMouse);
    }

    public void setHumanMouse(boolean humanMouse) {
        this.humanMouse = humanMouse ? Boolean.TRUE : null;
    }

    /** Whether the chat browses with profile Intern (#141); {@code false}: Extern. */
    public boolean isBrowserInternal() {
        return Boolean.TRUE.equals(browserInternal);
    }

    public void setBrowserInternal(boolean browserInternal) {
        this.browserInternal = browserInternal ? Boolean.TRUE : null;
    }
}
