package com.aresstack.enterpriseai.astu.acropolis.projectfile;

import com.aresstack.enterpriseai.astu.acropolis.attachment.Attachment;
import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentException;
import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentTextExtractor;
import com.aresstack.enterpriseai.astu.acropolis.chat.project.ChatProjectStore;
import com.aresstack.enterpriseai.astu.acropolis.chat.project.ChatProjects;
import com.aresstack.enterpriseai.astu.acropolis.chat.project.ChatProjectsRecord;
import com.aresstack.enterpriseai.astu.acropolis.chat.project.EffectiveProjectContext;
import com.aresstack.enterpriseai.astu.acropolis.tool.AiTool;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProjectFileToolsTest {

    private static final ChatConversationId CHAT = new ChatConversationId("chat-1");

    private ChatProjects projects;
    private Files files;
    private String alpha;
    private String beta;

    @Before
    public void setUp() {
        projects = new ChatProjects(new ChatProjectStore() {
            private ChatProjectsRecord record;

            @Override
            public ChatProjectsRecord load() {
                return record;
            }

            @Override
            public void save(ChatProjectsRecord saved) {
                record = saved;
            }
        });
        alpha = projects.create("Alpha", null);
        beta = projects.create("Beta", null);
        files = new Files();
        files.put(alpha, "att-0000000a", "plan.txt", "Der Zielwert ist 42.\n\nAnderes.");
        files.put(beta, "att-0000000b", "geheim.txt", "Beta-Geheimnis");
    }

    @Test
    public void chatInProjectReadsAndSearchesItsProjectFiles() {
        projects.assign(CHAT.value(), alpha);
        assertTrue(tool("list_project_files").execute("{}").contains("plan.txt"));
        assertTrue(tool("read_project_file").execute("{\"file_id\":\"att-0000000a\"}").contains("Zielwert ist 42"));
        assertTrue(tool("search_project_file").execute("{\"query\":\"zielwert\"}").contains("Absatz 1"));
    }

    @Test
    public void otherProjectsFilesAreUnknownEvenByExactId() {
        projects.assign(CHAT.value(), alpha);
        refuses("read_project_file", "{\"file_id\":\"att-0000000b\"}");
    }

    @Test
    public void chatWithoutProjectIsRefused() {
        refuses("list_project_files", "{}");
    }

    @Test
    public void movingTheChatWithdrawsTheOldProjectsFilesFromToolsBuiltBefore() {
        projects.assign(CHAT.value(), alpha);
        List<AiTool> before = tools();
        projects.assign(CHAT.value(), beta);
        String listed = find(before, "list_project_files").execute("{}");
        assertFalse(listed.contains("plan.txt"));
        assertTrue(listed.contains("geheim.txt"));
        projects.assign(CHAT.value(), null);
        try {
            find(before, "read_project_file").execute("{\"file_id\":\"att-0000000a\"}");
            fail("expected refusal");
        } catch (AttachmentException expected) {
            // fail closed
        }
    }

    @Test
    public void unresolvedAssignmentFailsClosed() {
        List<AiTool> tools = new ProjectFileTools(files, extractor(), id -> EffectiveProjectContext.unresolved(),
                CHAT).tools();
        try {
            find(tools, "list_project_files").execute("{}");
            fail("expected refusal");
        } catch (AttachmentException expected) {
            assertTrue(expected.getMessage().contains("verweigert"));
        }
        List<AiTool> throwing = new ProjectFileTools(files, extractor(), id -> {
            throw new IllegalStateException("broken");
        }, CHAT).tools();
        try {
            find(throwing, "list_project_files").execute("{}");
            fail("expected refusal");
        } catch (AttachmentException expected) {
            // fail closed
        }
    }

    @Test
    public void definitionsNameTheThreeTools() {
        assertEquals(3, ProjectFileTools.definitions().size());
    }

    private void refuses(String name, String arguments) {
        try {
            tool(name).execute(arguments);
            fail("expected refusal");
        } catch (AttachmentException expected) {
            // fail closed
        }
    }

    private AiTool tool(String name) {
        return find(tools(), name);
    }

    private List<AiTool> tools() {
        return new ProjectFileTools(files, extractor(), projects, CHAT).tools();
    }

    private static AiTool find(List<AiTool> tools, String name) {
        for (AiTool tool : tools) {
            if (tool.name().equals(name)) {
                return tool;
            }
        }
        throw new AssertionError(name);
    }

    private static AttachmentTextExtractor extractor() {
        return (fileName, content) -> new String(content, StandardCharsets.UTF_8);
    }

    private static final class Files implements ProjectFileStore {
        private final Map<String, List<Attachment>> lists = new HashMap<String, List<Attachment>>();
        private final Map<String, byte[]> contents = new HashMap<String, byte[]>();

        void put(String projectId, String id, String name, String text) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            List<Attachment> list = lists.get(projectId);
            if (list == null) {
                list = new ArrayList<Attachment>();
                lists.put(projectId, list);
            }
            list.add(new Attachment(id, name, bytes.length));
            contents.put(projectId + "/" + id, bytes);
        }

        @Override
        public Attachment add(String projectId, Path source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Attachment> list(String projectId) {
            List<Attachment> list = lists.get(projectId);
            return list == null ? new ArrayList<Attachment>() : list;
        }

        @Override
        public byte[] read(String projectId, String fileId) {
            byte[] bytes = contents.get(projectId + "/" + fileId);
            if (bytes == null) {
                throw new AttachmentException("Unbekannt " + fileId);
            }
            return bytes;
        }

        @Override
        public void remove(String projectId, String fileId) {
        }

        @Override
        public void deleteProject(String projectId) {
        }
    }
}
