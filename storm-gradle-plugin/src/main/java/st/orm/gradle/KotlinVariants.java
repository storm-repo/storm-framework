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

import java.util.LinkedHashMap;
import java.util.Map;
import org.gradle.api.GradleException;

/**
 * Maps the project's Kotlin version to the Storm compiler-plugin variant compiled against that Kotlin
 * compiler API, and to the recommended KSP version.
 *
 * <p>Keep this matrix in sync with {@code website/src/components/tutorial/tutorialTheme.js}
 * (KOTLIN_VARIANTS) and {@code docs/installation.md}.</p>
 */
final class KotlinVariants {

    /**
     * Kotlin major.minor to recommended KSP version. Kotlin 2.0 and 2.1 require Kotlin-paired KSP builds;
     * KSP 2.3+ is Kotlin-version-independent and supports Kotlin 2.2 and newer.
     */
    private static final Map<String, String> KSP_BY_KOTLIN = new LinkedHashMap<>();

    static {
        KSP_BY_KOTLIN.put("2.0", "2.0.21-1.0.28");
        KSP_BY_KOTLIN.put("2.1", "2.1.21-2.0.2");
        KSP_BY_KOTLIN.put("2.2", "2.2.21-2.0.5");
        KSP_BY_KOTLIN.put("2.3", "2.3.10");
        KSP_BY_KOTLIN.put("2.4", "2.3.10");
    }

    private KotlinVariants() {
    }

    /**
     * Returns the Storm compiler-plugin variant (the artifact suffix, such as {@code 2.4}) for the given
     * Kotlin version.
     *
     * @param kotlinVersion the full Kotlin version, such as {@code 2.4.0}.
     * @throws GradleException if the Kotlin version has no matching variant.
     */
    static String variantFor(String kotlinVersion) {
        String majorMinor = majorMinor(kotlinVersion);
        if (!KSP_BY_KOTLIN.containsKey(majorMinor)) {
            throw new GradleException(("""
                    Storm: no compiler-plugin variant for Kotlin %s. Supported Kotlin versions: %s.
                    Pin a variant explicitly with:
                        storm { compilerPluginVariant.set("%s") }
                    or disable the compiler plugin with:
                        storm { compilerPlugin.set(false) }""")
                    .formatted(kotlinVersion, String.join(", ", KSP_BY_KOTLIN.keySet()), newestVariant()));
        }
        return majorMinor;
    }

    /**
     * Returns the recommended KSP version for the given Kotlin version, falling back to the newest known
     * recommendation for unknown Kotlin versions.
     *
     * @param kotlinVersion the full Kotlin version, such as {@code 2.4.0}.
     */
    static String kspFor(String kotlinVersion) {
        return KSP_BY_KOTLIN.getOrDefault(majorMinor(kotlinVersion), KSP_BY_KOTLIN.get(newestVariant()));
    }

    private static String majorMinor(String kotlinVersion) {
        int firstDot = kotlinVersion.indexOf('.');
        int secondDot = kotlinVersion.indexOf('.', firstDot + 1);
        return secondDot > 0 ? kotlinVersion.substring(0, secondDot) : kotlinVersion;
    }

    private static String newestVariant() {
        String newest = null;
        for (String variant : KSP_BY_KOTLIN.keySet()) {
            newest = variant;
        }
        return newest;
    }
}
