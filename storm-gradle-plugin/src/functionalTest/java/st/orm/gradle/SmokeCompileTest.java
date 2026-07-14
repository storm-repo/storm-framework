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
package st.orm.gradle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compiles real Kotlin and Java projects against the locally installed Storm snapshot artifacts and asserts
 * the generated metamodel exists. Both builds run with {@code --configuration-cache} and run twice, so the
 * full task graph (KSP respectively the annotation processor and the preview-flag argument providers) is
 * proven to serialize and be reusable. Requires a prior {@code mvn install -DskipTests} of the reactor;
 * gated behind {@code -Dstorm.smoke=true}.
 */
public class SmokeCompileTest {

    @TempDir
    Path projectDir;

    private GradleRunner runner() {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("build", "-x", "test", "--configuration-cache");
    }

    @Test
    @EnabledIfSystemProperty(named = "storm.smoke", matches = "true")
    public void compilesAKotlinEntityAndGeneratesTheMetamodel() throws Exception {
        Files.writeString(projectDir.resolve("settings.gradle.kts"), FunctionalTestSupport.SETTINGS);
        Files.writeString(projectDir.resolve("build.gradle.kts"), """
                plugins {
                    id("org.jetbrains.kotlin.jvm") version "2.4.0"
                    id("com.google.devtools.ksp") version "2.3.10"
                    id("st.orm")
                }
                repositories {
                    mavenLocal()
                    mavenCentral()
                }
                kotlin {
                    jvmToolchain(21)
                }
                """);
        var sourceDir = projectDir.resolve("src/main/kotlin/demo");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("City.kt"), """
                package demo

                import st.orm.Entity
                import st.orm.PK

                data class City(
                    @PK val id: Int = 0,
                    val name: String,
                ) : Entity<Int>
                """);
        var result = runner().build();
        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"), result.getOutput());
        assertTrue(result.getOutput().contains("Configuration cache entry stored."), result.getOutput());
        try (var generated = Files.walk(projectDir.resolve("build/generated/ksp"))) {
            assertTrue(generated.anyMatch(path -> path.getFileName().toString().startsWith("City_")),
                    "Expected a generated City_ metamodel under build/generated/ksp.");
        }
        var second = runner().build();
        assertTrue(second.getOutput().contains("Reusing configuration cache."), second.getOutput());
    }

    @Test
    @EnabledIfSystemProperty(named = "storm.smoke", matches = "true")
    public void compilesAJavaEntityAndGeneratesTheMetamodel() throws Exception {
        Files.writeString(projectDir.resolve("settings.gradle.kts"), FunctionalTestSupport.SETTINGS);
        Files.writeString(projectDir.resolve("build.gradle.kts"), """
                plugins {
                    java
                    id("st.orm")
                }
                repositories {
                    mavenLocal()
                    mavenCentral()
                }
                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(21)
                    }
                }
                """);
        var sourceDir = projectDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("City.java"), """
                package demo;

                import st.orm.Entity;
                import st.orm.PK;

                public record City(@PK Integer id, String name) implements Entity<Integer> {
                }
                """);
        var result = runner().build();
        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"), result.getOutput());
        assertTrue(result.getOutput().contains("Configuration cache entry stored."), result.getOutput());
        try (var generated = Files.walk(projectDir.resolve("build/generated/sources/annotationProcessor"))) {
            assertTrue(generated.anyMatch(path -> path.getFileName().toString().startsWith("City_")),
                    "Expected a generated City_ metamodel under build/generated/sources/annotationProcessor.");
        }
        var second = runner().build();
        assertTrue(second.getOutput().contains("Reusing configuration cache."), second.getOutput());
    }
}
