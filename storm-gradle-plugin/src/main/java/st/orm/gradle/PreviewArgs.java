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

import java.util.List;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * Contributes {@code --enable-preview} when enabled. Lazy, so the extension can be configured after the
 * plugin is applied.
 */
public final class PreviewArgs implements CommandLineArgumentProvider {

    private final Provider<Boolean> enabled;

    PreviewArgs(Provider<Boolean> enabled) {
        this.enabled = enabled;
    }

    @Input
    public Provider<Boolean> getEnabled() {
        return enabled;
    }

    @Override
    public Iterable<String> asArguments() {
        return enabled.getOrElse(false) ? List.of("--enable-preview") : List.of();
    }
}
