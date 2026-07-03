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
package st.orm.spi.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static st.orm.GenerationStrategy.NONE;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Map;
import javax.sql.DataSource;
import lombok.Builder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.Json;
import st.orm.PK;
import st.orm.core.template.PreparedStatementTemplate;

/**
 * Verifies that {@code @Json} fields bind correctly against PostgreSQL's native {@code jsonb} columns.
 *
 * <p>PostgreSQL rejects string-typed parameters for {@code json}/{@code jsonb} columns, so the JSON converter
 * output is bound as an untyped parameter via the dialect — these tests exercise the insert, update and upsert
 * (INSERT ... ON CONFLICT) paths against real {@code jsonb} columns.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)    // Prevent swapping to H2.
@DataJpaTest(showSql = false)
@Testcontainers
public class PostgreSQLJsonTest {

    @SuppressWarnings("resource")
    @Container
    public static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test")
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    // Note: @Json fields use Map types here — Jackson reflects on java.base types without requiring this
    // module to be opened to jackson-databind. Structured @Json records are covered by the jackson2/jackson3
    // module tests.
    @Builder(toBuilder = true)
    @DbTable("user_profile")
    public record UserProfile(
            @PK Integer id,
            @Nonnull String name,
            @Nonnull @Json Map<String, String> attributes,
            @Nullable @Json Map<String, String> address
    ) implements Entity<Integer> {}

    @Builder(toBuilder = true)
    @DbTable("document")
    public record Document(
            @PK(generation = NONE) String key,
            @Nonnull @Json Map<String, String> payload
    ) implements Entity<String> {}

    @Test
    public void testInsertAndReadJsonbColumns() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(UserProfile.class);
        var inserted = repo.insertAndFetch(UserProfile.builder()
                .name("Alice")
                .attributes(Map.of("theme", "dark", "language", "en"))
                .address(Map.of("address", "271 University Ave", "city", "Palo Alto"))
                .build());
        assertEquals(Map.of("theme", "dark", "language", "en"), inserted.attributes());
        assertEquals("Palo Alto", inserted.address().get("city"));
    }

    @Test
    public void testInsertNullJsonbColumn() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(UserProfile.class);
        var inserted = repo.insertAndFetch(UserProfile.builder()
                .name("Bob")
                .attributes(Map.of())
                .address(null)
                .build());
        assertNull(inserted.address());
        assertEquals(Map.of(), inserted.attributes());
    }

    @Test
    public void testUpdateJsonbColumn() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(UserProfile.class);
        var inserted = repo.insertAndFetch(UserProfile.builder()
                .name("Carol")
                .attributes(Map.of("theme", "light"))
                .build());
        repo.update(inserted.toBuilder().attributes(Map.of("theme", "dark")).build());
        assertEquals(Map.of("theme", "dark"), repo.getById(inserted.id()).attributes());
    }

    @Test
    public void testUpsertJsonbColumn() {
        // Natural key: exercises PostgreSQL's INSERT ... ON CONFLICT upsert with a jsonb parameter.
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Document.class);
        repo.upsert(Document.builder().key("settings").payload(Map.of("volume", "20")).build());
        assertEquals(Map.of("volume", "20"), repo.getById("settings").payload());
        repo.upsert(Document.builder().key("settings").payload(Map.of("volume", "80")).build());
        assertEquals(Map.of("volume", "80"), repo.getById("settings").payload());
    }

    @Test
    public void testUpsertBatchJsonbColumn() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Document.class);
        var documents = java.util.List.of(
                Document.builder().key("doc-1").payload(Map.of("state", "draft")).build(),
                Document.builder().key("doc-2").payload(Map.of("state", "draft")).build());
        repo.upsert(documents);
        repo.upsert(documents.stream().map(d -> d.toBuilder().payload(Map.of("state", "final")).build()).toList());
        assertEquals(Map.of("state", "final"), repo.getById("doc-1").payload());
        assertEquals(Map.of("state", "final"), repo.getById("doc-2").payload());
    }
}
