package com.aresstack.enterpriseai.architecture;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Gegenbeispiele für Regelklassen, deren Regeln in den Testmethoden selbst stehen (ChatBoundaryTest,
 * EmbeddingBoundaryTest, KnowledgeBoundaryTest, RagBoundaryTest, SourceBoundaryTest, ModuleRegistryTest,
 * BuildModelTest): Die Regelmethode wird unverändert ausgeführt, nur ihre Eingabe (die importierten Klassen,
 * die Registry oder das Build-Modell) wird für die Dauer des Aufrufs durch das Gegenbeispiel ersetzt. So
 * bleibt jede Strang-Regelklasse unangetastet und wird trotzdem bewiesen.
 */
final class ExistingRuleProbe {

    private ExistingRuleProbe() {
    }

    /**
     * Führt {@code ruleMethod} von {@code testClass} mit {@code substitute} als Eingabe aus und erwartet einen
     * {@link AssertionError}, dessen Meldung jeden der erwarteten Textbausteine enthält.
     */
    static void assertRuleFails(Class<?> testClass, String ruleMethod, Object substitute, String... expectedInMessage) {
        Field input = inputField(testClass, substitute.getClass());
        Object instance = instantiate(testClass);
        Object target = Modifier.isStatic(input.getModifiers()) ? null : instance;
        Object previous;
        try {
            input.setAccessible(true);
            previous = input.get(target);
            input.set(target, substitute);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(testClass.getSimpleName() + "." + input.getName() + " nicht ersetzbar", e);
        }
        try {
            Method method = testClass.getMethod(ruleMethod);
            try {
                method.invoke(instance);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof AssertionError) {
                    String message = String.valueOf(e.getCause().getMessage());
                    for (String expected : expectedInMessage) {
                        assertTrue(testClass.getSimpleName() + "." + ruleMethod + " meldet nicht '" + expected + "': "
                                + message, message.contains(expected));
                    }
                    return;
                }
                throw new IllegalStateException(testClass.getSimpleName() + "." + ruleMethod + " brach ab", e.getCause());
            }
            fail(testClass.getSimpleName() + "." + ruleMethod + " hat das Gegenbeispiel nicht erkannt");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(testClass.getSimpleName() + "." + ruleMethod + " nicht aufrufbar", e);
        } finally {
            try {
                input.set(target, previous);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** Das Eingabefeld der Regelklasse: das einzige Feld, dessen Typ zum Ersatzobjekt passt. */
    private static Field inputField(Class<?> testClass, Class<?> substituteType) {
        Field found = null;
        for (Field field : testClass.getDeclaredFields()) {
            if (field.getType().isAssignableFrom(substituteType) && !field.getType().equals(Object.class)) {
                if (found != null) {
                    throw new IllegalStateException(testClass.getSimpleName() + " hat mehrere Felder vom Typ "
                            + substituteType.getSimpleName());
                }
                found = field;
            }
        }
        if (found == null) {
            throw new IllegalStateException(testClass.getSimpleName() + " hat kein Feld vom Typ "
                    + substituteType.getSimpleName() + "; Probe anpassen");
        }
        return found;
    }

    private static Object instantiate(Class<?> testClass) {
        try {
            return testClass.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(testClass.getSimpleName() + " nicht instanziierbar", e);
        }
    }
}
