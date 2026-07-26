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

import org.gradle.api.provider.Property;

/**
 * Configuration of the Storm Gradle plugin:
 *
 * <pre>{@code
 * storm {
 *     metamodel.set(true)               // generate the compile-time metamodel (default true)
 *     compilerPlugin.set(true)          // Kotlin only: apply the Storm compiler plugin (default true)
 *     compilerPluginVariant.set("2.4")  // override the auto-detected Kotlin variant
 *     javaPreview.set(true)             // Java only: --enable-preview flags (default true)
 * }
 * }</pre>
 */
public abstract class StormExtension {

    /**
     * Creates the extension. Gradle instantiates this type through its object factory.
     */
    public StormExtension() {
    }

    /**
     * Whether to wire the metamodel processor: {@code storm-metamodel-ksp} on the {@code ksp} configuration
     * for Kotlin projects, {@code storm-metamodel-processor} on {@code annotationProcessor} for Java
     * projects. Default {@code true}.
     *
     * @return whether the metamodel processor is wired.
     */
    public abstract Property<Boolean> getMetamodel();

    /**
     * Whether to add the Storm Kotlin compiler plugin, which makes string interpolations inside SQL template
     * lambdas injection-safe automatically. Kotlin projects only. Default {@code true}.
     *
     * @return whether the Kotlin compiler plugin is added.
     */
    public abstract Property<Boolean> getCompilerPlugin();

    /**
     * Overrides the auto-detected compiler-plugin variant (the Kotlin major.minor suffix, such as
     * {@code "2.4"}). Set this when the project uses a Kotlin version newer than the plugin knows about.
     *
     * @return the compiler-plugin variant to use instead of the auto-detected one.
     */
    public abstract Property<String> getCompilerPluginVariant();

    /**
     * Whether to add {@code --enable-preview} to Java compilation, tests, and execution, required by
     * storm-java21's String Templates on JDK 21. Java projects only. Default {@code true}.
     *
     * @return whether preview features are enabled for Java compilation, tests, and execution.
     */
    public abstract Property<Boolean> getJavaPreview();
}
