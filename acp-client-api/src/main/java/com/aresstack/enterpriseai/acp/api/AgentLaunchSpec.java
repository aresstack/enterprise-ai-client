package com.aresstack.enterpriseai.acp.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How to spawn the external agent process (command, args, env). Immutable.
 *
 * <p>Environment values may carry credentials (e.g. an MCP endpoint token), so {@link #toString()} lists
 * only the variable names, never their values.</p>
 */
public final class AgentLaunchSpec {

    private final String command;
    private final List<String> args;
    private final Map<String, String> env;

    public AgentLaunchSpec(String command, List<String> args, Map<String, String> env) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        this.command = command;
        this.args = Collections.unmodifiableList(new ArrayList<String>(
                args == null ? Collections.<String>emptyList() : args));
        this.env = Collections.unmodifiableMap(new LinkedHashMap<String, String>(
                env == null ? Collections.<String, String>emptyMap() : env));
    }

    public String getCommand() { return command; }
    public List<String> getArgs() { return args; }
    public Map<String, String> getEnv() { return env; }

    @Override
    public String toString() {
        return "AgentLaunchSpec{command=" + command + ", args=" + args + ", envKeys=" + env.keySet() + "}";
    }
}
