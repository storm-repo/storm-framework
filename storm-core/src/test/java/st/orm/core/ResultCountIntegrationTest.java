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
package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.core.template.SqlInterceptor.observe;
import static st.orm.core.template.TemplateString.raw;
import static st.orm.core.template.Templates.select;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.Pageable;
import st.orm.core.model.City;
import st.orm.core.model.Owner;
import st.orm.core.model.Pet;
import st.orm.core.model.Pet_;
import st.orm.core.model.Visit;
import st.orm.core.model.VisitView;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.TemplateString;

/**
 * Integration tests for {@code getResultCount()}, which executes a dedicated count query derived from the builder's
 * shape rather than fetching and counting the results.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class ResultCountIntegrationTest {

    @Autowired
    private DataSource dataSource;

    private List<String> observeStatements(Runnable runnable) {
        List<String> statements = new ArrayList<>();
        observe(sql -> statements.add(sql.statement()), runnable);
        return statements;
    }

    @Test
    public void testPlainCountReplacesSelectClause() {
        var orm = ORMTemplate.of(dataSource);
        long expected = orm.selectFrom(Pet.class).getResultList().size();
        var statements = observeStatements(() ->
                assertEquals(expected, orm.selectFrom(Pet.class).getResultCount()));
        assertEquals(1, statements.size());
        assertTrue(statements.getFirst().startsWith("SELECT COUNT(*)"));
        assertFalse(statements.getFirst().contains("FROM ("));
    }

    @Test
    public void testCountWithWhere() {
        var orm = ORMTemplate.of(dataSource);
        var filtered = orm.selectFrom(Pet.class)
                .where(Pet_.owner, Owner.builder().id(1).build());
        assertEquals(filtered.getResultList().size(), filtered.getResultCount());
    }

    @Test
    public void testCountWithRowExpandingJoin() {
        var orm = ORMTemplate.of(dataSource);
        var joined = orm.selectFrom(Pet.class)
                .innerJoin(Visit.class).on(Pet.class);
        assertEquals(joined.getResultList().size(), joined.getResultCount());
    }

    @Test
    public void testDistinctCountCountsDistinctPrimaryKeys() {
        var orm = ORMTemplate.of(dataSource);
        var distinct = orm.selectFrom(Pet.class)
                .distinct()
                .innerJoin(Visit.class).on(Pet.class);
        long expected = distinct.getResultList().size();
        var statements = observeStatements(() ->
                assertEquals(expected, distinct.getResultCount()));
        assertEquals(1, statements.size());
        assertTrue(statements.getFirst().startsWith("SELECT COUNT(*)"));
        assertTrue(statements.getFirst().contains("DISTINCT"));
    }

    @Test
    public void testDistinctCountWithCustomSelect() {
        var orm = ORMTemplate.of(dataSource);
        var distinctOwners = orm.entity(Pet.class)
                .select(Integer.class, raw("\0.id", Owner.class))
                .distinct();
        assertEquals(distinctOwners.getResultList().size(), distinctOwners.getResultCount());
    }

    @Test
    public void testCountRespectsLimitAndOffset() {
        var orm = ORMTemplate.of(dataSource);
        long total = orm.selectFrom(Visit.class).getResultCount();
        assertEquals(5, orm.selectFrom(Visit.class).limit(5).getResultCount());
        assertEquals(total - 10, orm.selectFrom(Visit.class).offset(10).getResultCount());
        assertEquals(2, orm.selectFrom(Visit.class).offset((int) total - 2).limit(5).getResultCount());
    }

    @Test
    public void testCountWithOrderByOmitsOrderBy() {
        var orm = ORMTemplate.of(dataSource);
        var ordered = orm.selectFrom(Pet.class).orderBy(Pet_.name);
        long expected = ordered.getResultList().size();
        var statements = observeStatements(() ->
                assertEquals(expected, ordered.getResultCount()));
        assertEquals(1, statements.size());
        assertFalse(statements.getFirst().contains("ORDER BY"));
    }

    @Test
    public void testCountWithCustomSelect() {
        var orm = ORMTemplate.of(dataSource);
        var names = orm.entity(City.class).select(String.class, raw("\0.name", City.class));
        long expected = names.getResultList().size();
        var statements = observeStatements(() ->
                assertEquals(expected, names.getResultCount()));
        assertEquals(1, statements.size());
        assertTrue(statements.getFirst().contains("FROM ("));
    }

    @Test
    public void testCountWithAggregateSelect() {
        var orm = ORMTemplate.of(dataSource);
        // The query returns a single aggregate row, so its result count is 1, not the number of rows counted.
        assertEquals(1, orm.entity(Pet.class)
                .select(Long.class, TemplateString.of("COUNT(*)"))
                .getResultCount());
    }

    @Test
    public void testCountWithGroupBy() {
        var orm = ORMTemplate.of(dataSource);
        var grouped = orm.entity(Pet.class)
                .select(Long.class, TemplateString.of("COUNT(*)"))
                .groupBy(Pet_.owner);
        assertEquals(grouped.getResultList().size(), grouped.getResultCount());
    }

    @Test
    public void testLockedCountFetchesResults() {
        var orm = ORMTemplate.of(dataSource);
        var locked = orm.selectFrom(Pet.class).forUpdate();
        long expected = orm.selectFrom(Pet.class).getResultList().size();
        var statements = observeStatements(() ->
                assertEquals(expected, locked.getResultCount()));
        assertEquals(1, statements.size());
        assertTrue(statements.getFirst().contains("FOR UPDATE"));
        assertFalse(statements.getFirst().contains("COUNT"));
    }

    @Test
    public void testRefCount() {
        var orm = ORMTemplate.of(dataSource);
        var refs = orm.entity(Pet.class).selectRef();
        assertEquals(refs.getResultList().size(), refs.getResultCount());
    }

    @Test
    public void testDistinctCountWithoutPrimaryKeyFetchesResults() {
        var orm = ORMTemplate.of(dataSource);
        // VisitView is a projection without a primary key, so no reduced column set preserves the distinct row
        // identity and the results are fetched and counted.
        var distinct = orm.projection(VisitView.class).select().distinct();
        long expected = distinct.getResultList().size();
        var statements = observeStatements(() ->
                assertEquals(expected, distinct.getResultCount()));
        assertEquals(1, statements.size());
        assertFalse(statements.getFirst().contains("COUNT"));
    }

    @Test
    public void testCountWithSelectElementTemplate() {
        var orm = ORMTemplate.of(dataSource);
        var cities = orm.entity(City.class).select(City.class, TemplateString.wrap(select(City.class)));
        long expected = cities.getResultList().size();
        var statements = observeStatements(() ->
                assertEquals(expected, cities.getResultCount()));
        assertEquals(1, statements.size());
        assertTrue(statements.getFirst().startsWith("SELECT COUNT(*)"));
    }

    @Test
    public void testPageUsesCountQuery() {
        var orm = ORMTemplate.of(dataSource);
        long total = orm.selectFrom(Visit.class).getResultList().size();
        var statements = observeStatements(() -> {
            var page = orm.selectFrom(Visit.class).page(Pageable.ofSize(5));
            assertEquals(total, page.totalCount());
            assertEquals(5, page.content().size());
        });
        assertEquals(2, statements.size());
        assertTrue(statements.stream().anyMatch(statement -> statement.startsWith("SELECT COUNT(*)")));
    }
}
