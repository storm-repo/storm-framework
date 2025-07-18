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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.Element;
import st.orm.JoinType;
import st.orm.core.template.impl.Elements.Source;
import st.orm.core.template.impl.Elements.Target;
import st.orm.core.template.impl.Elements.TemplateSource;
import st.orm.core.template.impl.Elements.TemplateTarget;

import static java.util.Objects.requireNonNull;

/**
 * A join element of a template.
 *
 * @param source the source of the join.
 * @param sourceAlias the alias of the join.
 * @param target the target of the join.
 * @param type the type of the join.
 * @param autoJoin whether the join must be automatically generated.
 */
record Join(
        @Nonnull Source source,
        @Nonnull String sourceAlias,
        @Nonnull Target target,
        @Nullable String targetAlias,
        @Nonnull JoinType type,
        boolean autoJoin
) implements Element {
    Join {
        requireNonNull(source, "source");
        requireNonNull(sourceAlias, "alias");
        requireNonNull(target, "target");
        requireNonNull(type, "type");
        if (source instanceof TemplateSource && !(target instanceof TemplateTarget)) {
            throw new IllegalArgumentException("TemplateSource must be used in combination with TemplateTarget.");
        }
    }

    Join (
            @Nonnull Source source,
            @Nonnull String sourceAlias,
            @Nonnull Target target,
            @Nonnull JoinType type,
            boolean autoJoin
    ) {
        this(source, sourceAlias, target, null, type, autoJoin);
    }
}
