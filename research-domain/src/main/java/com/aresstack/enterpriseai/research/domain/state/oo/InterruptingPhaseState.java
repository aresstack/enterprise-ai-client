package com.aresstack.enterpriseai.research.domain.state.oo;

/** A {@link PhaseState} that interrupts a run (paused/blocked/failed) and remembers where to continue. */
public interface InterruptingPhaseState extends PhaseState {

    PhaseState getContinuationState();
}
