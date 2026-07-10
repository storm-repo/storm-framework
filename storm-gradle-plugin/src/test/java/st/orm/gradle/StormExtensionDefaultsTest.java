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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

public class StormExtensionDefaultsTest {

    @Test
    public void registersTheExtensionWithDefaults() {
        var project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("st.orm");
        var extension = (StormExtension) project.getExtensions().getByName("storm");
        assertNotNull(extension);
        assertTrue(extension.getMetamodel().get());
        assertTrue(extension.getCompilerPlugin().get());
        assertTrue(extension.getJavaPreview().get());
        assertFalse(extension.getCompilerPluginVariant().isPresent());
    }

    @Test
    public void wiresJavaPathDependenciesOnTheJavaPlugin() {
        var project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("st.orm");
        project.getPluginManager().apply("java");
        var version = StormVersion.get();
        var implementation = project.getConfigurations().getByName("implementation").getIncoming().getDependencies();
        assertTrue(implementation.stream().anyMatch(dependency ->
                "storm-bom".equals(dependency.getName()) && version.equals(dependency.getVersion())));
        assertTrue(implementation.stream().anyMatch(dependency -> "storm-java21".equals(dependency.getName())));
        var runtimeOnly = project.getConfigurations().getByName("runtimeOnly").getIncoming().getDependencies();
        assertTrue(runtimeOnly.stream().anyMatch(dependency -> "storm-core".equals(dependency.getName())));
        var annotationProcessor =
                project.getConfigurations().getByName("annotationProcessor").getIncoming().getDependencies();
        assertTrue(annotationProcessor.stream().anyMatch(dependency ->
                "storm-metamodel-processor".equals(dependency.getName())));
    }

    @Test
    public void loadsTheBakedVersion() {
        // The baked version follows the build's -Pversion (0.0.0-SNAPSHOT by default), so only its presence
        // is asserted here; release builds run with the tag version.
        assertFalse(StormVersion.get().isBlank());
    }
}
