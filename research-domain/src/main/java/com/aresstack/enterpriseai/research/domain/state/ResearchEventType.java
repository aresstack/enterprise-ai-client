package com.aresstack.enterpriseai.research.domain.state;

/** Domain event kinds emitted by the state machine (UI-free). */
public enum ResearchEventType {
    SessionStateChanged,
    OutlineProposed,
    OutlineApproved,
    SectionAdded,
    SectionRenamed,
    SectionReordered,
    SourceLinked,
    FindingLinked,
    ApprovalRequested,
    RevisionRequested,
    ResearchCompleted,
    ResearchBlocked,
    ResearchFailed
}
