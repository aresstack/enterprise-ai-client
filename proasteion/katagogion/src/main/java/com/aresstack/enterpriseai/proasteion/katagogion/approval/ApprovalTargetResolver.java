package com.aresstack.enterpriseai.proasteion.katagogion.approval;

import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;

/**
 * Determines, without side effects, what a tool call would act on: action, scope, owner-checked target id and
 * version. Implementations look the target up in trusted data (store, session) instead of believing the model's
 * arguments. An exception means the target is unknown; the gate then does not run the tool.
 */
public interface ApprovalTargetResolver {

    /** Every call is the plain {@link ApprovalTarget#INVOKE} of the tool. */
    ApprovalTargetResolver INVOKE = call -> ApprovalTarget.invoke();

    ApprovalTarget resolve(McpToolCall call);
}
