package st.orm.spi.postgresql;

import static java.util.Map.entry;
import static st.orm.test.TestDatabase.POSTGRESQL;

import java.util.List;
import java.util.Map;
import st.orm.tck.AbstractEntityRepositoryConformanceTest;
import st.orm.tck.Expected;
import st.orm.tck.Statement;
import st.orm.test.StormTest;

@StormTest(database = POSTGRESQL, scripts = "/data.sql")
public class PostgreSQLEntityRepositoryConformanceTest extends AbstractEntityRepositoryConformanceTest {

    @Override
    protected List<String> schemaDdl() {
        return List.of(
                "DROP TABLE IF EXISTS version_long_entity CASCADE",
                """
                        CREATE TABLE version_long_entity (
                        id serial PRIMARY KEY,
                        name varchar(255),
                        version bigint DEFAULT 0
                        )""",
                "INSERT INTO version_long_entity (name) VALUES ('Alice')",
                "INSERT INTO version_long_entity (name) VALUES ('Bob')",
                "DROP TABLE IF EXISTS version_instant_entity CASCADE",
                """
                        CREATE TABLE version_instant_entity (
                        id serial PRIMARY KEY,
                        name varchar(255),
                        version timestamp DEFAULT CURRENT_TIMESTAMP
                        )""",
                "INSERT INTO version_instant_entity (name) VALUES ('Alice')",
                "INSERT INTO version_instant_entity (name) VALUES ('Bob')");
    }

    @Override
    protected Map<Statement, Expected> expectedSql() {
        return Map.ofEntries(
                entry(Statement.INSERT_AND_FETCH, Expected.sql("""
                INSERT INTO vet (first_name, last_name)
                VALUES (?, ?)""").keys("id")),
                entry(Statement.INSERT_AND_FETCH_BATCH, Expected.sql("INSERT INTO vet (first_name, last_name)\n" +
                "VALUES (?, ?), (?, ?)\n" +
                "RETURNING \"id\"").keys().bound(false)),
                entry(Statement.INSERT_AND_FETCH_COMPOUND_PK, Expected.sql("""
                INSERT INTO vet_specialty (vet_id, specialty_id)
                VALUES (?, ?)""").keys()),
                entry(Statement.INSERT_AND_FETCH_BATCH_COMPOUND_PK, Expected.sql("""
                INSERT INTO vet_specialty (vet_id, specialty_id)
                VALUES (?, ?)""").keys().bound(true)),
                entry(Statement.INSERT_AND_FETCH_INLINE, Expected.sql("""
                INSERT INTO owner (first_name, last_name, address, city, telephone, version)
                VALUES (?, ?, ?, ?, ?, ?)""").keys("id")),
                entry(Statement.INSERT_AND_FETCH_INLINE_BATCH, Expected.sql("INSERT INTO owner (first_name, last_name, address, city, telephone, version)\n" +
                "VALUES (?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?)\n" +
                "RETURNING \"id\"").keys().bound(false)),
                entry(Statement.SELECT_LIMIT, Expected.sql("""
                SELECT o.id, o.first_name, o.last_name, o.address, o.city, o.telephone, o.version
                FROM owner o
                LIMIT 2""")),
                entry(Statement.SELECT_OFFSET, Expected.sql("""
                SELECT o.id, o.first_name, o.last_name, o.address, o.city, o.telephone, o.version
                FROM owner o
                ORDER BY o.id
                OFFSET 1""")),
                entry(Statement.SELECT_LIMIT_OFFSET, Expected.sql("""
                SELECT o.id, o.first_name, o.last_name, o.address, o.city, o.telephone, o.version
                FROM owner o
                ORDER BY o.id
                OFFSET 1 LIMIT 2""")),
                entry(Statement.UPDATE_AND_FETCH_INLINE_VERSION, Expected.sql("""
                UPDATE owner
                SET first_name = ?, last_name = ?, address = ?, city = ?, telephone = ?, version = version + 1
                WHERE id = ? AND version = ?""").keys()),
                entry(Statement.UPDATE_AND_FETCH_INLINE_VERSION_BATCH, Expected.sql("""
                UPDATE owner
                SET first_name = ?, last_name = ?, address = ?, city = ?, telephone = ?, version = version + 1
                WHERE id = ? AND version = ?""").keys().bound(true)));
    }
}
