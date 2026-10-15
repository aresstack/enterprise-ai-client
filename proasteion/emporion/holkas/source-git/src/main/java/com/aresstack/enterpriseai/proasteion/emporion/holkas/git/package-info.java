/**
 * Adapter „Git-Repository“ (#137): genau ein Ref eines Git-Remotes (Azure Repos, GitHub, GitLab, lokal) als
 * Knowledge Source. JGit bleibt adapterintern, liest aus einem anwendungseigenen Bare-Cache und führt nie
 * Repository-Code, Hooks, Push oder Checkout aus.
 */
package com.aresstack.enterpriseai.proasteion.emporion.holkas.git;
