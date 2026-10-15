/**
 * Organisational chat projects (#143): nested folders in the sidebar that group saved chats and carry their own
 * context (project instructions, project memories). Not to be confused with research projects of the research agent.
 *
 * <p>The project tree and the {@code chatId → projectId} assignment are persisted through the port
 * {@link com.aresstack.enterpriseai.astu.acropolis.chat.project.ChatProjectStore}, separately from the chat messages.
 * {@link com.aresstack.enterpriseai.astu.acropolis.chat.project.ChatProjects} resolves the
 * {@link com.aresstack.enterpriseai.astu.acropolis.chat.project.EffectiveProjectContext} for every new turn from the
 * current assignment; nothing about a project is bound to a chat when it starts. Every move of a chat changes its
 * {@linkplain EffectiveProjectContext#scope() scope}, so knowledge context cached or saved under an earlier assignment
 * can no longer be reused.
 */
package com.aresstack.enterpriseai.astu.acropolis.chat.project;
