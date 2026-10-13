package com.aresstack.enterpriseai.proasteion.exedra.ui.sidebar;

import com.aresstack.enterpriseai.ui.comic.control.ResearchPillButton;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiMetrics;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

/**
 * Die eine Erzeugung der Aktionsknöpfe links im Drawer-Fuß („+ Neuer Chat“, „+ Quelle“): gleiche Höhe,
 * Rundung, Schrift und Innenabstand, und dieselbe Breite — gemessen am längsten Label, damit der Fuß beim
 * Reiterwechsel nicht springt. Die Aktion hängt der Aufrufer an; hier entsteht nur die Gestalt.
 */
public final class SidebarFooterPill {

    /** Das längste Label im Fuß; alle Fußknöpfe werden so breit wie dieses. */
    public static final String WIDTH_REFERENCE_LABEL = "+ Neuer Chat";
    /** Die Schriftgröße der Fußknöpfe in Punkt. */
    public static final float FONT_SIZE = 13f;

    private SidebarFooterPill() {
    }

    /** Ein Fußknopf mit diesem Label und Tooltip, in der gemeinsamen Gestalt. */
    public static ResearchPillButton create(String label, String tooltip) {
        ResearchPillButton pill = new ResearchPillButton(label, ResearchUiMetrics.FOOTER_CONTROL_HEIGHT,
                ResearchUiMetrics.RADIUS_CONTROL, ResearchUiMetrics.FOOTER_PILL_PADDING_H);
        pill.setFont(ResearchUiTypography.semiBold(FONT_SIZE));
        pill.setWidthReference(WIDTH_REFERENCE_LABEL);
        pill.setToolTipText(tooltip);
        return pill;
    }
}
