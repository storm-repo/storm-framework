package st.orm.spi.sqlite;

import static java.util.Map.entry;

import java.util.List;
import java.util.Map;
import st.orm.tck.AbstractEntityRepositoryConformanceTest;
import st.orm.tck.Expected;
import st.orm.tck.Statement;
import st.orm.test.StormTest;

@StormTest(url = "jdbc:sqlite:target/conformance.db", scripts = "/data.sql")
public class SQLiteEntityRepositoryConformanceTest extends AbstractEntityRepositoryConformanceTest {

    @Override
    protected String currentTimestampExpression() {
        return "strftime('%Y-%m-%d %H:%M:%f', 'now')";
    }

    @Override
    protected List<String> schemaDdl() {
        return List.of(
                "DROP TABLE IF EXISTS pk_only_entity",
                """
                        CREATE TABLE pk_only_entity (
                        id integer PRIMARY KEY
                        )""",
                "INSERT INTO pk_only_entity (id) VALUES (1)",
                "INSERT INTO pk_only_entity (id) VALUES (2)",
                "DROP TABLE IF EXISTS version_long_entity",
                """
                        CREATE TABLE version_long_entity (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name VARCHAR(255),
                        version INTEGER DEFAULT 0
                        )""",
                "INSERT INTO version_long_entity (name) VALUES ('Alice')",
                "INSERT INTO version_long_entity (name) VALUES ('Bob')",
                "DROP TABLE IF EXISTS version_instant_entity",
                """
                        CREATE TABLE version_instant_entity (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name VARCHAR(255),
                        version TIMESTAMP DEFAULT (strftime('%Y-%m-%d %H:%M:%f', 'now'))
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
                "RETURNING \"id\"")),
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
                "RETURNING \"id\"")),
                entry(Statement.SELECT_LIMIT, Expected.sql("""
                SELECT o.id, o.first_name, o.last_name, o.address, o.city, o.telephone, o.version
                FROM owner o
                LIMIT 2""")),
                entry(Statement.SELECT_OFFSET, Expected.sql("""
                SELECT o.id, o.first_name, o.last_name, o.address, o.city, o.telephone, o.version
                FROM owner o
                ORDER BY o.id
                LIMIT -1 OFFSET 1""")),
                entry(Statement.SELECT_LIMIT_OFFSET, Expected.sql("""
                SELECT o.id, o.first_name, o.last_name, o.address, o.city, o.telephone, o.version
                FROM owner o
                ORDER BY o.id
                LIMIT 2 OFFSET 1""")),
                entry(Statement.UPDATE_AND_FETCH_INLINE_VERSION, Expected.sql("""
                UPDATE owner
                SET first_name = ?, last_name = ?, address = ?, city = ?, telephone = ?, version = version + 1
                WHERE id = ? AND version = ?""").keys()),
                entry(Statement.UPDATE_AND_FETCH_INLINE_VERSION_BATCH, Expected.sql("""
                UPDATE owner
                SET first_name = ?, last_name = ?, address = ?, city = ?, telephone = ?, version = version + 1
                WHERE id = ? AND version = ?""").keys().bound(true)),
                entry(Statement.UPSERT_BATCH, Expected.sql("""
                        INSERT INTO vet (first_name, last_name)
                        VALUES (?, ?)
                        ON CONFLICT (id) DO UPDATE SET first_name = EXCLUDED.first_name, last_name = EXCLUDED.last_name""")),
                entry(Statement.UPSERT_AND_FETCH_BATCH, Expected.sql("""
                        UPDATE vet
                        SET first_name = ?, last_name = ?
                        WHERE id = ?""")),
                entry(Statement.UPSERT_AND_FETCH_BATCH_EXISTING_COMPOUND_PK, Expected.sql("""
                        INSERT INTO vet_specialty (vet_id, specialty_id)
                        VALUES (?, ?)
                        ON CONFLICT (vet_id, specialty_id) DO NOTHING""")),
                entry(Statement.UPSERT_AND_FETCH_INLINE_VERSION, Expected.sql("""
                        UPDATE owner
                        SET first_name = ?, last_name = ?, address = ?, city = ?, telephone = ?, version = version + 1
                        WHERE id = ? AND version = ?""")),
                entry(Statement.UPSERT_INLINE_VERSION_BATCH, Expected.sql("""
                        UPDATE owner
                        SET first_name = ?, last_name = ?, address = ?, city = ?, telephone = ?, version = version + 1
                        WHERE id = ? AND version = ?""")),
                entry(Statement.UPSERT, Expected.sql("""
                        INSERT INTO vet (first_name, last_name)
                        VALUES (?, ?)
                        ON CONFLICT (id) DO UPDATE SET first_name = EXCLUDED.first_name, last_name = EXCLUDED.last_name"""))
);
    }
}
