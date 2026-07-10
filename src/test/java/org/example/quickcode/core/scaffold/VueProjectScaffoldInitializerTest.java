package org.example.quickcode.core.scaffold;

import org.example.quickcode.constant.AppConstant;
import org.example.quickcode.core.CodegenOutputPaths;
import org.example.quickcode.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VueProjectScaffoldInitializerTest {

    private static final long TEST_APP_ID = 999_999_001L;

    @AfterEach
    void cleanup() {
        File outputDir = CodegenOutputPaths.outputDir(TEST_APP_ID, CodeGenTypeEnum.VUE_PROJECT);
        if (outputDir.exists()) {
            deleteRecursively(outputDir);
        }
    }

    @Test
    void initIfAbsent_copiesScaffoldFiles() {
        VueProjectScaffoldInitializer initializer = new VueProjectScaffoldInitializer();

        assertTrue(initializer.initIfAbsent(TEST_APP_ID));
        assertFalse(initializer.initIfAbsent(TEST_APP_ID));

        File outputDir = CodegenOutputPaths.outputDir(TEST_APP_ID, CodeGenTypeEnum.VUE_PROJECT);
        assertTrue(new File(outputDir, "package.json").isFile());
        assertTrue(new File(outputDir, "vite.config.js").isFile());
        assertTrue(new File(outputDir, "index.html").isFile());
        assertTrue(new File(outputDir, "src/main.js").isFile());
        assertTrue(new File(outputDir, "src/App.vue").isFile());
        assertTrue(new File(outputDir, "src/router/index.js").isFile());
        assertTrue(new File(outputDir, "src/pages/HomePage.vue").isFile());
        assertTrue(new File(outputDir, "src/components/NavBar.vue").isFile());
        assertTrue(new File(outputDir, "src/styles/global.css").isFile());
    }

    @Test
    void scaffoldPackageJsonContainsVueDependencies() throws Exception {
        VueProjectScaffoldInitializer initializer = new VueProjectScaffoldInitializer();
        initializer.initIfAbsent(TEST_APP_ID);

        File packageJson = new File(
                AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_" + TEST_APP_ID,
                "package.json");
        String content = Files.readString(packageJson.toPath());
        assertTrue(content.contains("\"vue\""));
        assertTrue(content.contains("\"vue-router\""));
        assertTrue(content.contains("\"vite\""));
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
