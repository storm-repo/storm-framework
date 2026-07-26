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

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;

/**
 * Applies Storm ORM to a Kotlin or Java project.
 *
 * <p>The plugin imports the Storm BOM and adds the core dependencies for the detected language path: Kotlin
 * projects (the {@code org.jetbrains.kotlin.jvm} plugin is applied) get {@code storm-kotlin}, the metamodel
 * processor on the {@code ksp} configuration, and the Storm compiler-plugin variant matching the project's
 * Kotlin version; Java projects get {@code storm-java21}, the annotation processor, and the
 * {@code --enable-preview} flags its String Templates require on JDK 21. All Storm coordinates use the
 * plugin's own version: the plugin and the artifacts are released together.</p>
 *
 * <p>The plugin has no compile-time dependency on the Kotlin Gradle plugin or KSP: it reacts to plugin ids
 * and wires dependencies by configuration name. KSP itself is applied by the user (its version is paired to
 * the Kotlin version); when it is missing, the build fails with the exact line to add.</p>
 */
public class StormPlugin implements Plugin<Project> {

    /**
     * Creates the plugin. Gradle instantiates this type when the plugin is applied.
     */
    public StormPlugin() {
    }

    private static final String KOTLIN_JVM_PLUGIN_ID = "org.jetbrains.kotlin.jvm";
    private static final String KSP_PLUGIN_ID = "com.google.devtools.ksp";

    @Override
    public void apply(Project project) {
        var extension = project.getExtensions().create("storm", StormExtension.class);
        extension.getMetamodel().convention(true);
        extension.getCompilerPlugin().convention(true);
        extension.getJavaPreview().convention(true);
        String version = StormVersion.get();
        var kotlin = new AtomicBoolean(false);
        var pluginManager = project.getPluginManager();
        pluginManager.withPlugin(KOTLIN_JVM_PLUGIN_ID, applied -> kotlin.set(true));
        pluginManager.withPlugin("java", applied -> {
            // The dependency callbacks run when the configuration is resolved, so the language path and the
            // extension are read after the whole build script has been evaluated; plugins-block order and
            // extension configuration order never matter.
            configure(project, "implementation", dependencies -> {
                dependencies.add(project.getDependencies().platform("st.orm:storm-bom:" + version));
                dependencies.add(project.getDependencies().create(
                        kotlin.get() ? "st.orm:storm-kotlin:" + version : "st.orm:storm-java21:" + version));
            });
            configure(project, "runtimeOnly", dependencies ->
                    dependencies.add(project.getDependencies().create("st.orm:storm-core:" + version)));
            configure(project, "annotationProcessor", dependencies -> {
                if (!kotlin.get() && extension.getMetamodel().get()) {
                    dependencies.add(project.getDependencies().create("st.orm:storm-metamodel-processor:" + version));
                }
            });
            Provider<Boolean> previewEnabled = extension.getJavaPreview().map(enabled -> enabled && !kotlin.get());
            var previewArgs = new PreviewArgs(previewEnabled);
            project.getTasks().withType(JavaCompile.class).configureEach(task ->
                    task.getOptions().getCompilerArgumentProviders().add(previewArgs));
            project.getTasks().withType(Test.class).configureEach(task ->
                    task.getJvmArgumentProviders().add(previewArgs));
            project.getTasks().withType(JavaExec.class).configureEach(task ->
                    task.getJvmArgumentProviders().add(previewArgs));
            project.afterEvaluate(evaluated -> {
                // storm-java21 ships JDK 21 preview class files, which are version-locked: they only load
                // on JDK 21. A different explicit toolchain is guaranteed breakage, so fail fast.
                if (kotlin.get() || !extension.getJavaPreview().get()) {
                    return;
                }
                var toolchain = evaluated.getExtensions()
                        .getByType(org.gradle.api.plugins.JavaPluginExtension.class)
                        .getToolchain()
                        .getLanguageVersion();
                if (toolchain.isPresent() && toolchain.get().asInt() != 21) {
                    throw new GradleException(("""
                            Storm: storm-java21 requires a JDK 21 toolchain: its String Templates are JDK 21 \
                            preview class files, which are version-locked and only load on JDK 21. The \
                            project's toolchain selects Java %s. Set:
                                java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
                            or opt out of the preview setup with storm { javaPreview.set(false) }.""")
                            .formatted(toolchain.get().asInt()));
                }
            });
        });
        pluginManager.withPlugin(KSP_PLUGIN_ID, applied ->
                project.getConfigurations()
                        .matching(configuration -> configuration.getName().equals("ksp"))
                        .configureEach(configuration -> {
                            // KSP decides whether to run from the configuration's dependency list without
                            // triggering Gradle's lazy dependency callbacks, so the processor must be added
                            // eagerly; the metamodel opt-out is honored once the build script has been
                            // evaluated.
                            var metamodelProcessor = project.getDependencies()
                                    .create("st.orm:storm-metamodel-ksp:" + version);
                            configuration.getDependencies().add(metamodelProcessor);
                            project.afterEvaluate(evaluated -> {
                                if (!extension.getMetamodel().get()) {
                                    configuration.getDependencies().remove(metamodelProcessor);
                                }
                            });
                        }));
        pluginManager.withPlugin(KOTLIN_JVM_PLUGIN_ID, applied ->
                configure(project, "kotlinCompilerPluginClasspath", dependencies -> {
                    if (extension.getCompilerPlugin().get()) {
                        String variant = extension.getCompilerPluginVariant().isPresent()
                                ? extension.getCompilerPluginVariant().get()
                                : KotlinVariants.variantFor(detectKotlinVersion(project));
                        dependencies.add(project.getDependencies().create(
                                "st.orm:storm-compiler-plugin-" + variant + ":" + version));
                    }
                }));
        project.afterEvaluate(evaluated -> {
            // Validation only: the Kotlin metamodel processor runs through KSP, which the user applies
            // because its version is paired to the project's Kotlin version.
            if (kotlin.get() && extension.getMetamodel().get() && !pluginManager.hasPlugin(KSP_PLUGIN_ID)) {
                throw new GradleException(("""
                        Storm: the Kotlin metamodel processor requires KSP. Add it to your plugins block:
                            id("com.google.devtools.ksp") version "%s"
                        or disable metamodel generation with:
                            storm { metamodel.set(false) }""")
                        .formatted(KotlinVariants.kspFor(detectKotlinVersion(evaluated))));
            }
        });
    }

    /**
     * Registers a dependency-set action on the named configuration, whenever it exists or appears.
     */
    private static void configure(Project project, String configurationName,
                                  org.gradle.api.Action<? super org.gradle.api.artifacts.DependencySet> action) {
        project.getConfigurations()
                .matching(configuration -> configuration.getName().equals(configurationName))
                .configureEach(configuration -> configuration.withDependencies(action));
    }

    /**
     * Returns the project's Kotlin version by reflectively reading {@code pluginVersion} from the applied
     * Kotlin JVM plugin (the stable {@code KotlinBasePlugin} API), avoiding a compile-time dependency on the
     * Kotlin Gradle plugin.
     */
    private static String detectKotlinVersion(Project project) {
        var plugin = project.getPlugins().findPlugin(KOTLIN_JVM_PLUGIN_ID);
        if (plugin == null) {
            throw new GradleException("Storm: the Kotlin JVM plugin is not applied.");
        }
        try {
            Method getPluginVersion = plugin.getClass().getMethod("getPluginVersion");
            return (String) getPluginVersion.invoke(plugin);
        } catch (ReflectiveOperationException e) {
            throw new GradleException("""
                    Storm: cannot determine the Kotlin version from the Kotlin JVM plugin. Pin the Storm \
                    compiler-plugin variant explicitly with storm { compilerPluginVariant.set("2.4") } or \
                    disable it with storm { compilerPlugin.set(false) }.""", e);
        }
    }
}
