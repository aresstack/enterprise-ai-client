package com.aresstack.enterpriseai.architecture;

import com.aresstack.enterpriseai.app.ui.archfixture.tech.SwingInsideApp;
import com.aresstack.enterpriseai.app.ui.archfixture.web.PanelUsingJavaFx;
import com.aresstack.enterpriseai.app.ui.archfixture.web.ServletFrontend;
import com.aresstack.enterpriseai.ui.comic.archfixture.tech.SwingInsideComic;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Nachtrag 3: Swing/Java2D-Comic-UI, kein Web-Frontend, kein JavaFX. */
public class UiTechnologyTest {

    @Test
    public void productionUsesNoWebFrontendOrOtherToolkit() {
        Violations.assertNone("Fremdes UI-Toolkit",
                Violations.of(Collections.singletonList(UiTechnologyRules.noWebFrontendOrOtherUiToolkit()), ProductionClasses.all()));
    }

    @Test
    public void javaFxAndServletsAreDetected() {
        List<String> violations = Violations.of(Collections.singletonList(UiTechnologyRules.noWebFrontendOrOtherUiToolkit()),
                ProductionClasses.of(PanelUsingJavaFx.class, ServletFrontend.class));
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("PanelUsingJavaFx"));
        assertTrue(violations.get(0), violations.get(0).contains("ServletFrontend"));
    }

    @Test
    public void swingInAppAndComicPasses() {
        Violations.assertNone("Swing in app-swing/comic-controls ist erlaubt",
                Violations.of(Collections.singletonList(UiTechnologyRules.noWebFrontendOrOtherUiToolkit()),
                        ProductionClasses.of(SwingInsideApp.class, SwingInsideComic.class)));
    }
}
