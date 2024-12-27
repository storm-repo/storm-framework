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
package st.orm.template;

import st.orm.Templates;
import st.orm.repository.EntityRepository;
import st.orm.repository.ProjectionRepository;
import st.orm.repository.RepositoryLookup;

/**
 * <p>The {@code ORMTemplate} is the primary interface that extends the {@code QueryTemplate} and
 * {@code RepositoryLooking} interfaces, providing both the SQL Template engine and ORM repository logic.
 *
 * @see Templates
 * @see EntityRepository
 * @see ProjectionRepository
 */
public interface ORMTemplate extends QueryTemplate, RepositoryLookup {
}
