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
package st.orm.spi;

import jakarta.annotation.Nonnull;
import st.orm.template.Model;
import st.orm.repository.Projection;
import st.orm.repository.ProjectionRepository;
import st.orm.template.ORMTemplate;

/**
 * Default implementation of {@link ProjectionRepository}.
 */
public final class ProjectionRepositoryImpl<P extends Record & Projection<ID>, ID>
        extends BaseRepositoryImpl<P, ID>
        implements ProjectionRepository<P, ID> {

    public ProjectionRepositoryImpl(@Nonnull ORMTemplate ormTemplate,
                                    @Nonnull Model<P, ID> model) {
        super(ormTemplate, model);
    }
}
