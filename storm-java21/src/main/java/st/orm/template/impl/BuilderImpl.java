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

import static java.util.Objects.requireNonNull;

import java.util.function.UnaryOperator;
import st.orm.StormConfig;
import st.orm.mapping.TemplateDecorator;
import st.orm.spi.ExceptionMapper;
import st.orm.spi.QueryObserver;
import st.orm.spi.SqlCommenter;
import st.orm.template.ORMTemplate;

/**
 * Builds the Java facade over an engine template builder.
 *
 * <p>Storm's integrations compose the engine builder first, handing it the connection and transaction strategies
 * that bridge their platform, and wrap it here so the application-facing options and the customization callbacks
 * apply on top of that composition.</p>
 *
 * @since 1.14
 */
public final class BuilderImpl implements ORMTemplate.Builder {
    private final st.orm.core.template.ORMTemplate.Builder core;

    public BuilderImpl(st.orm.core.template.ORMTemplate.Builder core) {
        this.core = requireNonNull(core, "core");
    }

    @Override
    public ORMTemplate.Builder config(StormConfig config) {
        core.config(config);
        return this;
    }

    @Override
    public ORMTemplate.Builder decorator(UnaryOperator<TemplateDecorator> decorator) {
        core.decorator(decorator);
        return this;
    }

    @Override
    public ORMTemplate.Builder manualCommitConnections() {
        core.manualCommitConnections();
        return this;
    }

    @Override
    public ORMTemplate.Builder exceptionMapper(ExceptionMapper exceptionMapper) {
        core.exceptionMapper(exceptionMapper);
        return this;
    }

    @Override
    public ORMTemplate.Builder queryObserver(QueryObserver queryObserver) {
        core.queryObserver(queryObserver);
        return this;
    }

    @Override
    public ORMTemplate.Builder sqlCommenter(SqlCommenter sqlCommenter) {
        core.sqlCommenter(sqlCommenter);
        return this;
    }

    @Override
    public ORMTemplate build() {
        return new ORMTemplateImpl(core.build());
    }
}
