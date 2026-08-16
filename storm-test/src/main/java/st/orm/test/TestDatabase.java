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
package st.orm.test;

import org.jspecify.annotations.Nullable;

/**
 * The databases a Storm test can run against.
 *
 * <p>{@link #H2} runs in memory and needs nothing beyond the H2 driver. Every other constant stands for a database
 * server that runs in a Docker container managed by <a href="https://testcontainers.com/">Testcontainers</a>, started
 * on first use and shared by all test classes of the run that ask for the same database and image. Each test class
 * receives its own freshly created database (or schema, on Oracle) inside that shared container, so scripts execute
 * against an empty database exactly as they do on H2 and test classes never observe each other's tables or rows.</p>
 *
 * <p>Testcontainers is not a dependency of {@code storm-test}: a test that names a container database needs the
 * matching Testcontainers 2 module and JDBC driver on its test classpath, and fails with a message naming both when
 * one is missing. Nothing changes for tests on H2.</p>
 *
 * @see StormTest#database()
 * @since 1.14
 */
public enum TestDatabase {

    /**
     * H2 in memory. The default; no container is involved.
     */
    H2(null),

    /**
     * PostgreSQL, running from the {@code postgres} image through {@code org.testcontainers:testcontainers-postgresql}.
     */
    POSTGRESQL(new Spec("postgres:17", "postgresql", "org.testcontainers.postgresql.PostgreSQLContainer",
            "org.postgresql:postgresql", "org.postgresql.Driver", 5432)),

    /**
     * MySQL, running from the {@code mysql} image through {@code org.testcontainers:testcontainers-mysql}.
     */
    MYSQL(new Spec("mysql:8.4", "mysql", "org.testcontainers.mysql.MySQLContainer",
            "com.mysql:mysql-connector-j", "com.mysql.cj.jdbc.Driver", 3306)),

    /**
     * MariaDB, running from the {@code mariadb} image through {@code org.testcontainers:testcontainers-mariadb}.
     */
    MARIADB(new Spec("mariadb:11.8", "mariadb", "org.testcontainers.mariadb.MariaDBContainer",
            "org.mariadb.jdbc:mariadb-java-client", "org.mariadb.jdbc.Driver", 3306)),

    /**
     * Microsoft SQL Server, running from the {@code mcr.microsoft.com/mssql/server} image through
     * {@code org.testcontainers:testcontainers-mssqlserver}.
     *
     * <p>The image requires accepting Microsoft's license terms. Testcontainers reads the acceptance from a
     * {@code container-license-acceptance.txt} file on the test classpath that lists the image, including its tag,
     * on a line of its own; the container refuses to start without it, naming the file and the image.</p>
     */
    MSSQL_SERVER(new Spec("mcr.microsoft.com/mssql/server:2022-latest", "mssqlserver",
            "org.testcontainers.mssqlserver.MSSQLServerContainer",
            "com.microsoft.sqlserver:mssql-jdbc", "com.microsoft.sqlserver.jdbc.SQLServerDriver", 1433)),

    /**
     * Oracle Database Free, running from the {@code gvenzl/oracle-free} image through
     * {@code org.testcontainers:testcontainers-oracle-free}.
     */
    ORACLE(new Spec("gvenzl/oracle-free:23-slim-faststart", "oracle-free", "org.testcontainers.oracle.OracleContainer",
            "com.oracle.database.jdbc:ojdbc11", "oracle.jdbc.OracleDriver", 1521));

    /**
     * How a container database is obtained: the image to run by default, the Testcontainers module (the suffix of
     * its artifact) and the container class that run it, the JDBC driver that connects to it, and the port the
     * database listens on.
     */
    private record Spec(String defaultImage,
                        String testcontainersModule,
                        String containerClassName,
                        String driverArtifact,
                        String driverClassName,
                        int port) {}

    private final @Nullable Spec spec;

    TestDatabase(@Nullable Spec spec) {
        this.spec = spec;
    }

    /**
     * Returns whether this database runs in a Testcontainers-managed container, which is every database except
     * {@link #H2}.
     */
    public boolean isContainer() {
        return spec != null;
    }

    /**
     * Returns the Docker image used when a test does not name one, pinned to a version that Storm's own test suite
     * runs against.
     *
     * @throws IllegalStateException for {@link #H2}, which does not run in a container.
     */
    public String defaultImage() {
        return spec().defaultImage();
    }

    /**
     * Returns the shared container for this database, running its {@linkplain #defaultImage() default image}. The
     * container is started on the first call and reused for the remainder of the JVM.
     *
     * @throws IllegalStateException for {@link #H2}, which does not run in a container, and when the Testcontainers
     *                               module or the JDBC driver for this database is missing from the classpath.
     */
    public DatabaseContainer container() {
        return container(defaultImage());
    }

    /**
     * Returns the shared container for this database, running the given Docker image. The container is started on
     * the first call for this database and image and reused for the remainder of the JVM.
     *
     * @param image the Docker image, including its tag; it must be a distribution of this database, such as
     *              {@code postgres:16} or {@code pgvector/pgvector:pg17} for {@link #POSTGRESQL}.
     * @throws IllegalStateException for {@link #H2}, which does not run in a container, and when the Testcontainers
     *                               module or the JDBC driver for this database is missing from the classpath.
     */
    public DatabaseContainer container(String image) {
        spec();
        return DatabaseContainer.of(this, image);
    }

    /**
     * The Testcontainers artifact that provides the container class, such as
     * {@code org.testcontainers:testcontainers-postgresql}.
     */
    String testcontainersArtifact() {
        return "org.testcontainers:testcontainers-" + spec().testcontainersModule();
    }

    String containerClassName() {
        return spec().containerClassName();
    }

    String driverArtifact() {
        return spec().driverArtifact();
    }

    String driverClassName() {
        return spec().driverClassName();
    }

    int port() {
        return spec().port();
    }

    private Spec spec() {
        if (spec == null) {
            throw new IllegalStateException(name() + " runs in memory; it has no container.");
        }
        return spec;
    }
}
