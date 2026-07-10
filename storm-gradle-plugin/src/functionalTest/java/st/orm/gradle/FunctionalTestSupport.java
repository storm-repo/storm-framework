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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helpers for TestKit projects. The generated build script registers a {@code stormDump} task that queries
 * the declared dependencies (triggering the plugin's dependency callbacks without resolving artifacts) and
 * prints the compiler argument providers, so assertions run offline and fast.
 */
final class FunctionalTestSupport {

    static final String SETTINGS = """
            pluginManagement {
                repositories {
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            rootProject.name = "storm-test-project"
            """;

    static final String DUMP_TASK = """

            tasks.register("stormDump") {
                doLast {
                    listOf("implementation", "runtimeOnly", "annotationProcessor", "ksp", "kotlinCompilerPluginClasspath").forEach { name ->
                        configurations.findByName(name)?.incoming?.dependencies?.forEach { d ->
                            println("DEP $name ${d.group}:${d.name}:${d.version}")
                        }
                    }
                    tasks.withType(JavaCompile::class).forEach { t ->
                        println("ARGS ${t.name} " + t.options.compilerArgumentProviders.flatMap { it.asArguments() })
                    }
                }
            }
            """;

    private FunctionalTestSupport() {
    }

    static void writeProject(Path directory, String buildScript) throws IOException {
        Files.writeString(directory.resolve("settings.gradle.kts"), SETTINGS);
        Files.writeString(directory.resolve("build.gradle.kts"), buildScript + DUMP_TASK);
    }
}
