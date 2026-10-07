package com.aresstack.enterpriseai.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Importiert die kompilierten Produktionsklassen aller Module einmal je Testlauf (AP24-Regelklassen teilen
 * sich den Import; die älteren Regelklassen importieren weiterhin selbst).
 */
final class ProductionClasses {

    private static JavaClasses all;

    private ProductionClasses() {
    }

    static synchronized JavaClasses all() {
        if (all == null) {
            BuildModel model = BuildModel.load();
            List<File> directories = new ArrayList<File>();
            for (String module : model.scannedModules()) {
                directories.addAll(model.existingClassDirectoriesOf(module));
            }
            all = new ClassFileImporter().importPaths(BuildModelTest.toPaths(directories));
        }
        return all;
    }

    static JavaClasses of(Class<?>... classes) {
        return new ClassFileImporter().importClasses(classes);
    }
}
