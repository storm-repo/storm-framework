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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    private record Compilation(boolean success, String errors, List<String> warnings, Path generatedSources, Path classes) {

        boolean generated(String relativePath) {
            return Files.exists(generatedSources.resolve(relativePath));
        }

        String generatedSource(String relativePath) throws IOException {
            return Files.readString(generatedSources.resolve(relativePath));
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
    void generatesNullableChainVariantForEveryRecord() throws Exception {
        Compilation compilation = compile("CityStats.java", """
                import st.orm.GenerateMetamodel;

                @GenerateMetamodel
                public record CityStats(String name, int inhabitants) {}
                """);
        assertTrue(compilation.success(), compilation.errors());
        assertTrue(compilation.generated("CityStatsNullableMetamodel.java"),
                "expected a nullable-chain metamodel for the @GenerateMetamodel record");
        assertTrue(Files.exists(compilation.classes().resolve("CityStatsNullableMetamodel.class")),
                "expected the generated nullable-chain metamodel to compile");
    }

    @Test
    void selectsChildMetamodelByFieldNullability() throws Exception {
        Compilation compilation = compile("Owner.java", """
                import jakarta.annotation.Nullable;
                import st.orm.GenerateMetamodel;

                @GenerateMetamodel
                public record Owner(String name, Address address, @Nullable Address previousAddress) {}

                record Address(String street, String city) {}
                """);
        assertTrue(compilation.success(), compilation.errors());
        assertTrue(compilation.generated("AddressMetamodel.java"),
                "expected a metamodel for the referenced record");
        assertTrue(compilation.generated("AddressNullableMetamodel.java"),
                "expected a nullable-chain metamodel for the referenced record");
        String ownerMetamodel = compilation.generatedSource("OwnerMetamodel.java");
        assertTrue(ownerMetamodel.contains("AddressMetamodel<T> address"),
                "a non-null field selects the base child metamodel:\n" + ownerMetamodel);
        assertTrue(ownerMetamodel.contains("AddressNullableMetamodel<T> previousAddress"),
                "a nullable field selects the nullable-chain child metamodel:\n" + ownerMetamodel);
        String ownerNullableMetamodel = compilation.generatedSource("OwnerNullableMetamodel.java");
        assertTrue(ownerNullableMetamodel.contains("AddressNullableMetamodel<T> address"),
                "inside a nullable chain every child is the nullable-chain variant:\n" + ownerNullableMetamodel);
        assertTrue(Files.exists(compilation.classes().resolve("OwnerNullableMetamodel.class")),
                "expected the generated metamodels to compile");
    }

    @Test
    void interfaceSelectsChildMetamodelByForeignKeyNullability() throws Exception {
        Compilation compilation = compile("Owner.java", """
                import jakarta.annotation.Nullable;
                import st.orm.Entity;
                import st.orm.FK;
                import st.orm.PK;

                public record Owner(@PK Integer id, @FK City city, @Nullable @FK City previousCity)
                        implements Entity<Integer> {}

                record City(@PK Integer id, String name) implements Entity<Integer> {}
                """);
        assertTrue(compilation.success(), compilation.errors());
        String ownerInterface = compilation.generatedSource("Owner_.java");
        assertTrue(ownerInterface.contains("CityMetamodel<Owner> city"),
                "a non-null foreign key reads as the base child metamodel:\n" + ownerInterface);
        assertTrue(ownerInterface.contains("CityNullableMetamodel<Owner> previousCity"),
                "a nullable foreign key reads as the nullable-chain child metamodel:\n" + ownerInterface);
        assertTrue(Files.exists(compilation.classes().resolve("Owner_.class")),
                "expected the generated metamodel interface to compile");
    }

    @Test
    void generatesNullableChainVariantForSealedInterfaces() throws Exception {
        Compilation compilation = compile("Shipment.java", """
                import st.orm.Data;

                public sealed interface Shipment extends Data permits Parcel {
                    String code();
                }

                record Parcel(String code) implements Shipment {}
                """);
        assertTrue(compilation.success(), compilation.errors());
        assertTrue(compilation.generated("ShipmentMetamodel.java"),
                "expected a metamodel for the sealed Data interface");
        assertTrue(compilation.generated("ShipmentNullableMetamodel.java"),
                "expected a nullable-chain metamodel for the sealed Data interface");
        assertTrue(Files.exists(compilation.classes().resolve("ShipmentNullableMetamodel.class")),
                "expected the generated nullable-chain metamodel to compile");
    }

    @Test
    void reportsUniqueKeyNullabilityWarningOncePerRecord() throws Exception {
        Compilation compilation = compile("Account.java", """
                import jakarta.annotation.Nullable;
                import st.orm.Entity;
                import st.orm.PK;
                import st.orm.UK;

                public record Account(@PK Integer id, @UK @Nullable String email) implements Entity<Integer> {}
                """);
        assertTrue(compilation.success(), compilation.errors());
        long emailWarnings = compilation.warnings().stream()
                .filter(warning -> warning.contains("Unique key field 'email'"))
                .count();
        assertEquals(1, emailWarnings,
                "both chain variants walk the field; the warning must print once:\n" + compilation.warnings());
    }

    @Test
    void registersProcessorsForGradleIncrementalProcessing() throws IOException {
        var descriptor = MetamodelProcessor.class.getResource("/META-INF/gradle/incremental.annotation.processors");
        assertNotNull(descriptor, "expected the Gradle incremental annotation processing descriptor");
        String content;
        try (var stream = descriptor.openStream()) {
            content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(content.contains(MetamodelProcessor.class.getName() + ",aggregating"), content);
        assertTrue(content.contains(TypeIndexProcessor.class.getName() + ",aggregating"), content);
    }

    @Test
    void rejectsNonRefForeignKeyCycleBetweenEntities() throws Exception {
        Compilation compilation = compile("Owner.java", """
                import st.orm.Entity;
                import st.orm.FK;
                import st.orm.PK;

                public record Owner(@PK Integer id, @FK Pet pet) implements Entity<Integer> {}

                record Pet(@PK Integer id, @FK Owner owner) implements Entity<Integer> {}
                """);
        assertFalse(compilation.success(),
                "the generated metamodels would construct each other until the stack overflows, so the cycle "
                + "must be rejected at generation time");
        assertTrue(compilation.errors().contains("Cycle of non-Ref foreign keys: Owner -> Pet -> Owner")
                        || compilation.errors().contains("Cycle of non-Ref foreign keys: Pet -> Owner -> Pet"),
                compilation.errors());
        assertTrue(compilation.errors().contains("Mark one of the foreign keys as Ref"), compilation.errors());
        assertEquals(1, compilation.errors().split("Cycle of non-Ref foreign keys", -1).length - 1,
                "the cycle must be reported once:\n" + compilation.errors());
    }

    @Test
    void rejectsSelfReferencingNonRefForeignKey() throws Exception {
        Compilation compilation = compile("Employee.java", """
                import st.orm.Entity;
                import st.orm.FK;
                import st.orm.PK;

                public record Employee(@PK Integer id, @FK Employee manager) implements Entity<Integer> {}
                """);
        assertFalse(compilation.success(),
                "a self-referencing non-Ref foreign key must be rejected at generation time");
        assertTrue(compilation.errors().contains("Cycle of non-Ref foreign keys: Employee -> Employee"),
                compilation.errors());
    }

    @Test
    void acceptsForeignKeyCycleThroughRefBoundary() throws Exception {
        Compilation compilation = compile("Owner.java", """
                import st.orm.Entity;
                import st.orm.FK;
                import st.orm.PK;
                import st.orm.Ref;

                public record Owner(@PK Integer id, @FK Pet pet) implements Entity<Integer> {}

                record Pet(@PK Integer id, @FK Ref<Owner> owner) implements Entity<Integer> {}
                """);
        assertTrue(compilation.success(), compilation.errors());
        assertTrue(compilation.generated("OwnerMetamodel.java"),
                "a cycle through a Ref boundary is loadable and generates as usual");
        assertTrue(compilation.generated("PetMetamodel.java"),
                "a cycle through a Ref boundary is loadable and generates as usual");
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
            List<String> warnings = diagnostics.getDiagnostics().stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.WARNING
                            || diagnostic.getKind() == Diagnostic.Kind.MANDATORY_WARNING)
                    .map(Object::toString)
                    .toList();
            return new Compilation(success, errors, warnings, generatedSources, classes);
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
