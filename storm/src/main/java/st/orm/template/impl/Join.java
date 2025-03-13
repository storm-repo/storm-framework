/*
 * Copyright 2024 - 2025 the original author or authors.
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

import jakarta.annotation.Nonnull;
import st.orm.template.JoinType;
import st.orm.template.impl.Elements.Source;
import st.orm.template.impl.Elements.Target;

import static java.util.Objects.requireNonNull;

/**
 * A join element of a template.
 *
 * @param source the source of the join.
 * @param alias the alias of the join.
 * @param target the target of the join.
 * @param type the type of the join.
 * @param autoJoin whether the join must be automatically generated.
 */
record Join(
        @Nonnull Source source,
        @Nonnull String alias,
        @Nonnull Target target,
        @Nonnull JoinType type,
        boolean autoJoin
) implements Element {
    Join {
        requireNonNull(source, "source");
        requireNonNull(alias, "alias");
        requireNonNull(target, "target");
        requireNonNull(type, "type");
        if (source instanceof Elements.TemplateSource && !(target instanceof Elements.TemplateTarget)) {
            throw new IllegalArgumentException("TemplateSource must be used in combination with TemplateTarget.");
        }
    }
}
