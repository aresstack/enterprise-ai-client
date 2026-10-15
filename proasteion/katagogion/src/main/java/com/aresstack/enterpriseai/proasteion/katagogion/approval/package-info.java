/**
 * Generic, framework-free tool approvals: which tool call may run without asking ({@link com.aresstack.enterpriseai.proasteion.katagogion.approval.ApprovalPolicy#ALLOW}),
 * only after the user agreed ({@link com.aresstack.enterpriseai.proasteion.katagogion.approval.ApprovalPolicy#ASK}) or never ({@link com.aresstack.enterpriseai.proasteion.katagogion.approval.ApprovalPolicy#DENY}).
 *
 * <p>{@link com.aresstack.enterpriseai.proasteion.katagogion.approval.ToolApprovalGate} is the single check point; {@link com.aresstack.enterpriseai.proasteion.katagogion.approval.ApprovingMcpToolHandler} applies it to a
 * tool handler. The question to the user goes through {@link com.aresstack.enterpriseai.proasteion.katagogion.approval.ApprovalPromptPort}, implemented by the UI.
 */
package com.aresstack.enterpriseai.proasteion.katagogion.approval;
