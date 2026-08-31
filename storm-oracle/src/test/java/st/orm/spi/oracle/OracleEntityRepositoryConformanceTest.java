package st.orm.spi.oracle;

import static java.util.Map.entry;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.testcontainers.oracle.OracleContainer;
import st.orm.tck.AbstractEntityRepositoryConformanceTest;
import st.orm.tck.ContainerDataSource;
import st.orm.tck.Expected;
import st.orm.tck.Statement;
import st.orm.test.StormTest;

@StormTest(scripts = "/data.sql")
public class OracleEntityRepositoryConformanceTest extends AbstractEntityRepositoryConformanceTest {

    private static OracleContainer container;

    /**
     * {@code @StormTest} takes the data source from this method, so the suite owns its container rather than the
     * {@code database} attribute, and the module stays on the Testcontainers generation its other tests use.
     */
    public static synchronized DataSource dataSource() {
        if (container == null) {
            container = new OracleContainer("gvenzl/oracle-free:23");
            container.start();
        }
        return ContainerDataSource.of(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    @Override
    protected List<String> schemaDdl() {
        return List.of(
                "DROP TABLE non_autogen_entity",
                """
                        CREATE TABLE non_autogen_entity (
                        id NUMBER PRIMARY KEY,
                        name VARCHAR2(255),
                        version NUMBER DEFAULT 0
                        )""",
                "INSERT INTO non_autogen_entity (id, name) VALUES (1, 'First')",
                "INSERT INTO non_autogen_entity (id, name) VALUES (2, 'Second')",
                "DROP TABLE pk_only_entity",
                """
                        CREATE TABLE pk_only_entity (
                        id integer PRIMARY KEY
                        )""",
                "INSERT INTO pk_only_entity (id) VALUES (1)",
                "INSERT INTO pk_only_entity (id) VALUES (2)",
                "DROP TABLE version_long_entity",
                """
                        CREATE TABLE version_long_entity (
                        id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        name VARCHAR2(255),
                        version NUMBER(19) DEFAULT 0
                        )""",
                "INSERT INTO version_long_entity (name) VALUES ('Alice')",
                "INSERT INTO version_long_entity (name) VALUES ('Bob')",
                "DROP TABLE version_instant_entity",
                """
                        CREATE TABLE version_instant_entity (
                        id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        name VARCHAR2(255),
                        version TIMESTAMP DEFAULT SYSTIMESTAMP
                        )""",
                "INSERT INTO version_instant_entity (name) VALUES ('Alice')",
                "INSERT INTO version_instant_entity (name) VALUES ('Bob')");
    }

    @Override
    protected boolean supportsUpsertFetchWithSequences() {
        return false;
    }

    @Override
    protected Map<Statement, Expected> expectedSql() {
        return Map.ofEntries(
                entry(Statement.INSERT_AND_FETCH, Expected.sql("""
                INSERT INTO vet (first_name, last_name)
                VALUES (?, ?)""").keys("id")),
                entry(Statement.INSERT_AND_FETCH_BATCH, Expected.sql("""
                INSERT INTO vet (first_name, last_name)
                VALUES (?, ?)""").keys("id").bound(true)),
                entry(Statement.INSERT_AND_FETCH_COMPOUND_PK, Expected.sql("""
                INSERT INTO vet_specialty (vet_id, specialty_id)
                VALUES (?, ?)""").keys()),
                entry(Statement.INSERT_AND_FETCH_BATCH_COMPOUND_PK, Expected.sql("""
                INSERT INTO vet_specialty (vet_id, specialty_id)
                VALUES (?, ?)""").keys().bound(true)),
                entry(Statement.INSERT_AND_FETCH_INLINE, Expected.sql("""
                INSERT INTO owner (first_name, last_name, address, city, telephone, version)
                VALUES (?, ?, ?, ?, ?, ?)""").keys("id")),
                entry(Statement.INSERT_AND_FETCH_INLINE_BATCH, Expected.sql("""
                INSERT INTO owner (first_name, last_name, address, city, telephone, version)
                VALUES (?, ?, ?, ?, ?, ?)""").keys("id").bound(true)),
                entry(Statement.SELECT_LIMIT, Expected.sql("""
                SELECT o.id, o.first_name, o.last_name, o.address, o.city, o.telephone, o.version
                FROM owner o
                FETCH FIRST 2 ROWS ONLY""")),
                entry(Statement.SELECT_OFFSET, Expected.sql("""
                SELECT o.id, o.first_name, o.last_name, o.address, o.city, o.telephone, o.version
                FROM owner o
                ORDER BY o.id
                OFFSET 1 ROWS""")),
                entry(Statement.SELECT_LIMIT_OFFSET, Expected.sql("""
                SELECT o.id, o.first_name, o.last_name, o.address, o.city, o.telephone, o.version
                FROM owner o
                ORDER BY o.id
                OFFSET 1 ROWS FETCH NEXT 2 ROWS ONLY""")),
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
                        VALUES (?, ?)""")),
                entry(Statement.UPSERT_AND_FETCH_BATCH, Expected.sql("""
                        UPDATE vet
                        SET first_name = ?, last_name = ?
                        WHERE id = ?""")),
                entry(Statement.UPSERT_AND_FETCH_BATCH_EXISTING_COMPOUND_PK, Expected.sql("""
                        MERGE INTO vet_specialty t
                        USING (SELECT ? AS vet_id, ? AS specialty_id FROM DUAL) src
                        ON (t.vet_id = src.vet_id AND t.specialty_id = src.specialty_id)
                        WHEN NOT MATCHED THEN
                        	INSERT (vet_id, specialty_id)
                        	VALUES (src.vet_id, src.specialty_id)""")),
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
                        VALUES (?, ?)"""))
,
                entry(Statement.UPSERT_INLINE_VERSION, Expected.sql("""
                        UPDATE owner
                        SET first_name = ?, last_name = ?, address = ?, city = ?, telephone = ?, version = version + 1
                        WHERE id = ? AND version = ?""")),
                entry(Statement.UPSERT_AND_FETCH_NON_AUTO_GENERATED, Expected.sql("""
                        MERGE INTO specialty t
                        USING (SELECT ? AS id, ? AS name FROM DUAL) src
                        ON (t.id = src.id)
                        WHEN MATCHED THEN
                        	UPDATE SET t.name = src.name
                        WHEN NOT MATCHED THEN
                        	INSERT (id, name)
                        	VALUES (src.id, src.name)""")),
                entry(Statement.UPSERT_NON_AUTO_GENERATED, Expected.sql("""
                        MERGE INTO specialty t
                        USING (SELECT ? AS id, ? AS name FROM DUAL) src
                        ON (t.id = src.id)
                        WHEN MATCHED THEN
                        	UPDATE SET t.name = src.name
                        WHEN NOT MATCHED THEN
                        	INSERT (id, name)
                        	VALUES (src.id, src.name)""")),
                entry(Statement.UPSERT_AND_FETCH_BATCH_NEW_COMPOUND_PK, Expected.sql("""
                        MERGE INTO vet_specialty t
                        USING (SELECT ? AS vet_id, ? AS specialty_id FROM DUAL) src
                        ON (t.vet_id = src.vet_id AND t.specialty_id = src.specialty_id)
                        WHEN NOT MATCHED THEN
                        	INSERT (vet_id, specialty_id)
                        	VALUES (src.vet_id, src.specialty_id)""")),
                entry(Statement.UPSERT_AND_FETCH_NON_AUTO_GENERATED_BATCH, Expected.sql("""
                        MERGE INTO specialty t
                        USING (SELECT ? AS id, ? AS name FROM DUAL) src
                        ON (t.id = src.id)
                        WHEN MATCHED THEN
                        	UPDATE SET t.name = src.name
                        WHEN NOT MATCHED THEN
                        	INSERT (id, name)
                        	VALUES (src.id, src.name)"""))
,
                entry(Statement.UPSERT_NON_AUTO_GENERATED_BATCH, Expected.sql("""
                        MERGE INTO specialty t
                        USING (SELECT ? AS id, ? AS name FROM DUAL) src
                        ON (t.id = src.id)
                        WHEN MATCHED THEN
                        	UPDATE SET t.name = src.name
                        WHEN NOT MATCHED THEN
                        	INSERT (id, name)
                        	VALUES (src.id, src.name)"""))
);
    }
}
