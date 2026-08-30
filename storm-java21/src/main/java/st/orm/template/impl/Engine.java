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
package st.orm.template.impl;

/**
 * Verifies that the engine backing this API is on the runtime classpath.
 *
 * <p>The API is compiled against the engine but does not carry it: applications add it as a runtime dependency,
 * and the starters and the Gradle plugin do so on their behalf. Without that dependency the engine's absence
 * would first surface as a {@code NoClassDefFoundError} at the point a template runs a statement, so the entry
 * points that create a template check for it and report what is missing instead.</p>
 *
 * @since 1.14
 */
public final class Engine {

    private static final String ENGINE_CLASS = "st.orm.core.template.ORMTemplate";

    private static final boolean PRESENT = present();

    private Engine() {
    }

    private static boolean present() {
        try {
            Class.forName(ENGINE_CLASS, false, Engine.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /**
     * Verifies that the engine is available, reporting the missing dependency when it is not.
     *
     * @throws IllegalStateException if the engine is not on the runtime classpath.
     */
    public static void require() {
        if (!PRESENT) {
            throw new IllegalStateException(
                    "Storm's engine is not on the runtime classpath: the st.orm:storm-core artifact is missing. "
                            + "Add it as a runtime dependency (Gradle: runtimeOnly(\"st.orm:storm-core\"), "
                            + "Maven: <scope>runtime</scope>), or depend on a Storm starter, which carries it.");
        }
    }
}
