package org.apache.seatunnel.datasource.classloader;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.nio.file.Path;

public class DatasourceClassLoaderTest {

    @TempDir Path tempDir;

    @Test
    public void testLoadClassDuplicate() throws Exception {
        // 1. Create a dummy class source
        String className = "org.apache.seatunnel.datasource.classloader.DummyClass";
        File sourceFile =
                tempDir.resolve("org/apache/seatunnel/datasource/classloader/DummyClass.java")
                        .toFile();
        sourceFile.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(sourceFile)) {
            writer.write("package org.apache.seatunnel.datasource.classloader;\n");
            writer.write("public class DummyClass {}\n");
        }

        // 2. Compile it
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        compiler.run(null, null, null, sourceFile.getPath());

        // 3. Create loader pointing to tempDir
        URL[] urls = new URL[] {tempDir.toUri().toURL()};
        try (DatasourceClassLoader loader =
                new DatasourceClassLoader(urls, Thread.currentThread().getContextClassLoader())) {
            // 4. Load once
            Class<?> cls1 = loader.loadClass(className);
            Assertions.assertNotNull(cls1);
            Assertions.assertEquals(className, cls1.getName());

            // 5. Load again - this should NOT throw LinkageError
            // Without fix, this will trigger findClass -> defineClass again and throw LinkageError
            Class<?> cls2 = loader.loadClass(className);
            Assertions.assertSame(cls1, cls2);
        }
    }
}
