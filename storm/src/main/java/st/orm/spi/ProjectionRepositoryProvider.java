/*
 * Copyright 2024 the original author or authors.
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
import st.orm.repository.Model;
import st.orm.repository.Projection;
import st.orm.repository.ProjectionRepository;
import st.orm.template.ORMTemplate;

/**
 * Provides pluggable projection repository logic.
 */
public interface ProjectionRepositoryProvider extends Provider {

    <ID, P extends Record & Projection<ID>> ProjectionRepository<P, ID> getProjectionRepository(@Nonnull ORMTemplate orm,
                                                                                                @Nonnull Model<P, ID> model);
}
