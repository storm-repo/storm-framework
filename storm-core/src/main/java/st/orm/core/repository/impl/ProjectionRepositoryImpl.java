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
package st.orm.core.repository.impl;

import jakarta.annotation.Nonnull;
import st.orm.Projection;
import st.orm.Ref;
import st.orm.core.template.Model;
import st.orm.core.repository.ProjectionRepository;
import st.orm.core.template.ORMTemplate;

/**
 * Default implementation of {@link ProjectionRepository}.
 *
 * @param <P> the type of projection managed by this repository.
 * @param <ID> the type of the primary key of the projection, or {@link Void} if the projection has no primary key.
 */
public final class ProjectionRepositoryImpl<P extends Projection<ID>, ID>
        extends BaseRepositoryImpl<P, ID>
        implements ProjectionRepository<P, ID> {

    public ProjectionRepositoryImpl(@Nonnull ORMTemplate ormTemplate,
                                    @Nonnull Model<P, ID> model) {
        super(ormTemplate, model);
    }

    /**
     * Creates a new ref projection instance with the specified projection.
     *
     * @param projection the projection.
     * @return a ref projection instance.
     */
    @Override
    public Ref<P> ref(@Nonnull P projection, @Nonnull ID id) {
        return ormTemplate.ref(projection, id);
    }
}
