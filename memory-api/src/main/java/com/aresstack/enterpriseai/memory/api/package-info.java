/**
 * Port for persistent memories the user explicitly asked to keep (#144). A memory belongs to exactly one
 * {@link com.aresstack.enterpriseai.memory.api.MemoryOwner} (chat, project or global user scope). The owner is always
 * derived from the trusted runtime context by the caller, never from model arguments.
 */
package com.aresstack.enterpriseai.memory.api;
