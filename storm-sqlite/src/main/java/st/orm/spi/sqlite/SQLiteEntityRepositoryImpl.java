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
package st.orm.spi.sqlite;

import static st.orm.GenerationStrategy.NONE;
import static st.orm.core.template.TemplateString.raw;

import java.util.List;
import st.orm.Entity;
import st.orm.core.repository.EntityRepository;
import st.orm.core.repository.impl.OnConflictEntityRepositoryImpl;
import st.orm.core.template.Model;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.impl.JoinedEntityHelper;

/**
 * Implementation of {@link EntityRepository} for SQLite.
 *
 * <p>SQLite supports upserts using {@code INSERT ... ON CONFLICT(pk) DO UPDATE SET ...} syntax
 * (available since SQLite 3.24).</p>
 */
public class SQLiteEntityRepositoryImpl<E extends Entity<ID>, ID>
        extends OnConflictEntityRepositoryImpl<E, ID> {

    public SQLiteEntityRepositoryImpl(ORMTemplate ormTemplate, Model<E, ID> model) {
        super(ormTemplate, model);
    }

    /**
     * Overrides joined entity batch insert to use SQLite's {@code RETURNING} clause instead of
     * {@code executeBatch()} followed by {@code getGeneratedKeys()}, which the SQLite JDBC driver
     * does not support for batch operations.
     *
     * <p>Phase 1 (base table insert) uses a multi-value INSERT with {@code RETURNING} to retrieve
     * generated keys. Phase 2 (extension table inserts) delegates to the standard
     * {@link JoinedEntityHelper#insertExtensionTables} logic.</p>
     */
    @Override
    protected List<ID> insertJoinedBatch(List<E> entities) {
        if (generationStrategy == NONE) {
            return super.insertJoinedBatch(entities);
        }
        // SQLite does not support getGeneratedKeys() after executeBatch().
        // Use RETURNING clause for the base table insert instead.
        assert primaryKeyColumns.size() == 1;
        var primaryKeyColumn = primaryKeyColumns.getFirst();
        String primaryKeyName = primaryKeyColumn.qualifiedName(ormTemplate.dialect());
        // Phase 1: Base table INSERT with RETURNING.
        var query = ormTemplate.query(raw("""
            INSERT INTO \0
            VALUES \0
            RETURNING %s""".formatted(primaryKeyName), model.type(), entities))
                .managed();
        List<ID> ids = query.getResultList(model.primaryKeyType());
        // Phase 2: Extension table INSERTs.
        JoinedEntityHelper.insertExtensionTables(ormTemplate, model, entities, ids);
        return ids;
    }
}
