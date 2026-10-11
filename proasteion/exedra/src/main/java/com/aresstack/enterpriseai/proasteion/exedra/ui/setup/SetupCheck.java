package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

/** Ein Punkt der Abschlussprüfung des Einrichtungsassistenten: was geprüft wurde, ob es geht, und warum nicht. */
public final class SetupCheck {

    private final String label;
    private final boolean ok;
    private final String detail;

    private SetupCheck(String label, boolean ok, String detail) {
        if (label == null || label.trim().isEmpty()) {
            throw new IllegalArgumentException("label must not be empty");
        }
        this.label = label;
        this.ok = ok;
        this.detail = detail == null ? "" : detail;
    }

    public static SetupCheck ok(String label, String detail) {
        return new SetupCheck(label, true, detail);
    }

    public static SetupCheck failed(String label, String detail) {
        return new SetupCheck(label, false, detail);
    }

    public String label() {
        return label;
    }

    public boolean isOk() {
        return ok;
    }

    public String detail() {
        return detail;
    }

    @Override
    public String toString() {
        return (ok ? "OK " : "FEHLER ") + label + (detail.isEmpty() ? "" : ": " + detail);
    }
}
