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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

public class KotlinVariantsTest {

    @Test
    public void mapsEverySupportedKotlinVersionToItsVariant() {
        assertEquals("2.0", KotlinVariants.variantFor("2.0.21"));
        assertEquals("2.1", KotlinVariants.variantFor("2.1.21"));
        assertEquals("2.2", KotlinVariants.variantFor("2.2.21"));
        assertEquals("2.3", KotlinVariants.variantFor("2.3.21"));
        assertEquals("2.4", KotlinVariants.variantFor("2.4.0"));
    }

    @Test
    public void unknownKotlinVersionFailsWithOverrideHints() {
        var exception = assertThrows(GradleException.class, () -> KotlinVariants.variantFor("2.5.0"));
        assertTrue(exception.getMessage().contains("Kotlin 2.5.0"));
        assertTrue(exception.getMessage().contains("compilerPluginVariant.set(\"2.4\")"));
        assertTrue(exception.getMessage().contains("compilerPlugin.set(false)"));
    }

    @Test
    public void recommendsThePairedKspVersion() {
        assertEquals("2.0.21-1.0.28", KotlinVariants.kspFor("2.0.21"));
        assertEquals("2.1.21-2.0.2", KotlinVariants.kspFor("2.1.21"));
        assertEquals("2.2.21-2.0.5", KotlinVariants.kspFor("2.2.21"));
        assertEquals("2.3.10", KotlinVariants.kspFor("2.3.21"));
        assertEquals("2.3.10", KotlinVariants.kspFor("2.4.0"));
        // Unknown versions fall back to the newest known recommendation.
        assertEquals("2.3.10", KotlinVariants.kspFor("2.5.0"));
    }
}
