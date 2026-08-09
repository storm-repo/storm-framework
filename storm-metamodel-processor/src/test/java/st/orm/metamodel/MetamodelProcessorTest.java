/*
 * Copyright 2024 - 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package st.orm.metamodel;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compiles fixture sources with the processor attached and asserts on the generated files.
 *
 * <p>The fixtures compile against the actual storm-foundation sources via {@code -sourcepath}, so the
 * {@code st.orm.GenerateMetamodel} annotation the processor matches by name is the real one and the generated
 * code compiles against the real metamodel base classes. A jar dependency on storm-foundation is not an option
 * here: storm-foundation's own build runs this processor through {@code annotationProcessorPaths}, an edge the
 * reactor sorter cannot see, so a visible dependency in the other direction would order storm-foundation first
 * and break builds that start from an empty repository.</p>
 */
class MetamodelProcessorTest {

    @TempDir
    private Path tempDir;

    private record Compilation(boolean success, String errors, Path generatedSources, Path classes) {

        boolean generated(String relativePath) {
            return Files.exists(generatedSources.resolve(relativePath));
        }
    }

    @Test
    void generatesMetamodelForAnnotatedPlainRecord() throws Exception {
        Compilation compilation = compile("CityStats.java", """
                import st.orm.GenerateMetamodel;

                @GenerateMetamodel
                public record CityStats(String name, int inhabitants) {}
                """);
        assertTrue(compilation.success(), compilation.errors());
        assertTrue(compilation.generated("CityStatsMetamodel.java"),
                "expected a metamodel for the @GenerateMetamodel record");
        assertTrue(compilation.generated("CityStatsInstantiator.java"),
                "expected an instantiator for the @GenerateMetamodel record");
        assertTrue(Files.exists(compilation.classes().resolve("CityStatsMetamodel.class")),
                "expected the generated metamodel to compile");
        assertFalse(compilation.generated("CityStats_.java"),
                "the root metamodel interface is reserved for Data records");
        Path services = compilation.classes().resolve("META-INF/services/st.orm.mapping.Instantiator");
        assertTrue(Files.exists(services), "expected an instantiator service registration");
        assertTrue(Files.readString(services).contains("CityStatsInstantiator"));
    }

    @Test
    void ignoresPlainRecordWithoutAnnotation() throws Exception {
        Compilation compilation = compile("CityStats.java", """
                public record CityStats(String name, int inhabitants) {}
                """);
        assertTrue(compilation.success(), compilation.errors());
        assertFalse(compilation.generated("CityStatsMetamodel.java"),
                "a plain record without @GenerateMetamodel should not get a metamodel");
        assertFalse(compilation.generated("CityStatsInstantiator.java"),
                "a plain record without @GenerateMetamodel should not get an instantiator");
    }

    private Compilation compile(String fileName, String source) throws IOException, URISyntaxException {
        Path fixtureDir = Files.createDirectories(tempDir.resolve("fixtures"));
        Path sourcePath = Files.createDirectories(tempDir.resolve("sourcepath"));
        Path generatedSources = Files.createDirectories(tempDir.resolve("generated"));
        Path classes = Files.createDirectories(tempDir.resolve("classes"));
        copyFoundationSources(sourcePath);
        Path fixture = fixtureDir.resolve(fileName);
        Files.writeString(fixture, source);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            List<String> options = List.of(
                    "-d", classes.toString(),
                    "-s", generatedSources.toString(),
                    "-sourcepath", sourcePath.toString(),
                    "-implicit:class",
                    "-classpath", jarOf(jakarta.annotation.Nonnull.class));
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null,
                    fileManager.getJavaFileObjectsFromPaths(List.of(fixture)));
            task.setProcessors(List.of(new MetamodelProcessor()));
            boolean success = task.call();
            String errors = diagnostics.getDiagnostics().stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .map(Object::toString)
                    .collect(joining("\n"));
            return new Compilation(success, errors, generatedSources, classes);
        }
    }

    /**
     * Copies the storm-foundation package tree onto the fixture sourcepath, leaving the module declaration
     * behind so the fixture compilation stays on the classpath instead of turning modular.
     */
    private static void copyFoundationSources(Path sourcePath) throws IOException {
        Path foundation = foundationSources().resolve("st");
        try (Stream<Path> sources = Files.walk(foundation)) {
            for (Path source : sources.toList()) {
                Path target = sourcePath.resolve("st").resolve(foundation.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target);
                }
            }
        }
    }

    private static Path foundationSources() {
        String configured = System.getProperty("storm.foundation.sources");
        Path path = configured != null
                ? Path.of(configured)
                : Path.of("..", "storm-foundation", "src", "main", "java");
        assertTrue(Files.isDirectory(path.resolve("st").resolve("orm")),
                "storm-foundation sources not found at " + path.toAbsolutePath());
        return path;
    }

    private static String jarOf(Class<?> type) throws URISyntaxException {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    }
}
