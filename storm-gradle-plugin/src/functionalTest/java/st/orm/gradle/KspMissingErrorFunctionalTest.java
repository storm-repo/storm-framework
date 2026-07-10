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

import java.nio.file.Path;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class KspMissingErrorFunctionalTest {

    @TempDir
    Path projectDir;

    @Test
    public void kotlinWithoutKspFailsWithTheExactPluginLine() throws Exception {
        FunctionalTestSupport.writeProject(projectDir, """
                plugins {
                    id("org.jetbrains.kotlin.jvm") version "2.4.0"
                    id("st.orm")
                }
                """);
        var result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("help", "-q")
                .buildAndFail();
        assertTrue(result.getOutput().contains("id(\"com.google.devtools.ksp\") version \"2.3.10\""),
                "Expected the copy-pasteable KSP plugin line:\n" + result.getOutput());
        assertTrue(result.getOutput().contains("storm { metamodel.set(false) }"));
    }

    @Test
    public void kotlinWithoutKspPassesWhenMetamodelIsDisabled() throws Exception {
        FunctionalTestSupport.writeProject(projectDir, """
                plugins {
                    id("org.jetbrains.kotlin.jvm") version "2.4.0"
                    id("st.orm")
                }
                storm {
                    metamodel.set(false)
                }
                """);
        var result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("stormDump", "-q")
                .build();
        assertTrue(result.getOutput().contains("DEP implementation st.orm:storm-kotlin:"));
    }
}
