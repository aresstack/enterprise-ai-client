/**
 * Persistent memories for the chat (#144): use cases over {@link com.aresstack.enterpriseai.memory.api.MemoryStorePort}
 * behind the chat tools {@code memory_save}, {@code memory_search} and {@code memory_delete} (composition.MemoryTools). The chat id comes from the
 * turn that builds the tools, the project from {@link com.aresstack.enterpriseai.proasteion.application.memory.ChatProjectResolver}
 * at every call, the user from the running profile; never from model arguments.
 */
package com.aresstack.enterpriseai.proasteion.application.memory;
