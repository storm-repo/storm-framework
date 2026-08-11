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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JavaPathFunctionalTest {

    @TempDir
    Path projectDir;

    private String runDump(String buildScript) throws Exception {
        FunctionalTestSupport.writeProject(projectDir, buildScript);
        var result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("stormDump", "-q")
                .build();
        return result.getOutput();
    }

    @Test
    public void wiresTheJavaPath() throws Exception {
        var output = runDump("""
                plugins {
                    java
                    id("st.orm")
                }
                """);
        assertTrue(output.contains("DEP implementation st.orm:storm-bom:"));
        assertTrue(output.contains("DEP implementation st.orm:storm-java21:"));
        assertTrue(output.contains("DEP runtimeOnly st.orm:storm-core:"));
        assertTrue(output.contains("DEP annotationProcessor st.orm:storm-metamodel-processor:"));
        assertTrue(output.contains("--enable-preview"), "Expected --enable-preview on the Java path.");
        assertFalse(output.contains("storm-kotlin:"), "The Java path must not add storm-kotlin.");
    }

    @Test
    public void wiresTheProcessorIntoEverySourceSet() throws Exception {
        var output = runDump("""
                plugins {
                    java
                    id("st.orm")
                }
                sourceSets.create("integration")
                """);
        assertTrue(output.contains("DEP annotationProcessor st.orm:storm-metamodel-processor:"));
        assertTrue(output.contains("DEP testAnnotationProcessor st.orm:storm-metamodel-processor:"),
                "Entities declared in test sources need the processor too:\n" + output);
        assertTrue(output.contains("DEP integrationAnnotationProcessor st.orm:storm-metamodel-processor:"),
                "Custom source sets need the processor too:\n" + output);
    }

    @Test
    public void javadocGetsThePreviewFlag() throws Exception {
        var output = runDump("""
                plugins {
                    java
                    id("st.orm")
                }
                """);
        assertTrue(output.contains("JAVADOC javadoc source=21 preview=true"),
                "Javadoc embeds javac, which rejects storm-java21's preview class files without the flag:\n"
                        + output);
    }

    @Test
    public void javadocKeepsAUserConfiguredSourceLevel() throws Exception {
        var output = runDump("""
                plugins {
                    java
                    id("st.orm")
                }
                tasks.withType(Javadoc::class) {
                    options.source = "20"
                }
                """);
        assertTrue(output.contains("JAVADOC javadoc source=20 preview=true"),
                "An explicitly configured source level must not be overwritten:\n" + output);
    }

    @Test
    public void javaPreviewCanBeDisabled() throws Exception {
        var output = runDump("""
                plugins {
                    java
                    id("st.orm")
                }
                storm {
                    javaPreview.set(false)
                }
                """);
        assertFalse(output.contains("--enable-preview"));
        assertTrue(output.contains("JAVADOC javadoc source=null preview=false"),
                "Opting out must leave the javadoc options alone:\n" + output);
        assertTrue(output.contains("DEP implementation st.orm:storm-java21:"));
    }

    @Test
    public void nonJava21ToolchainFailsFast() throws Exception {
        FunctionalTestSupport.writeProject(projectDir, """
                plugins {
                    java
                    id("st.orm")
                }
                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(22)
                    }
                }
                """);
        var result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("help", "-q")
                .buildAndFail();
        assertTrue(result.getOutput().contains("requires a JDK 21 toolchain"),
                "Expected the toolchain fail-fast message:\n" + result.getOutput());
        assertTrue(result.getOutput().contains("JavaLanguageVersion.of(21)"));
    }

    @Test
    public void java21ToolchainPasses() throws Exception {
        var output = runDump("""
                plugins {
                    java
                    id("st.orm")
                }
                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(21)
                    }
                }
                """);
        assertTrue(output.contains("DEP implementation st.orm:storm-java21:"));
    }

    @Test
    public void metamodelCanBeDisabled() throws Exception {
        var output = runDump("""
                plugins {
                    java
                    id("st.orm")
                }
                storm {
                    metamodel.set(false)
                }
                """);
        assertFalse(output.contains("storm-metamodel-processor"));
    }
}
