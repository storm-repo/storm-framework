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

import static java.util.Objects.requireNonNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;

/**
 * A database server running in a Testcontainers-managed Docker container, shared by all tests of the JVM that ask
 * for the same {@link TestDatabase} and image.
 *
 * <p>Obtained through {@link TestDatabase#container()} or {@link TestDatabase#container(String)}, which start the
 * container on first use. The container stays up for the remainder of the JVM; Testcontainers removes it when the
 * JVM exits. Tests do not normally use the container's own database: {@link #createDatabase()} provisions a fresh,
 * empty database inside the container for each test class (or Spring context), so scripts run against an empty
 * database exactly as on H2 and test classes never observe each other's tables or rows.</p>
 *
 * @since 1.14
 */
public final class DatabaseContainer {

    private static final ConcurrentMap<Key, DatabaseContainer> CONTAINERS = new ConcurrentHashMap<>();
    private static final AtomicInteger DATABASE_COUNTER = new AtomicInteger();

    private record Key(TestDatabase database, String image) {}

    /**
     * How the started container is reached: its host and mapped port, the JDBC URL of its own database, and the user
     * Testcontainers configured for it.
     */
    record Endpoint(String host, int port, String jdbcUrl, String username, String password) {}

    private final TestDatabase database;
    private final String image;
    private final Object lock = new Object();
    private @Nullable Endpoint endpoint;

    private DatabaseContainer(TestDatabase database, String image) {
        this.database = database;
        this.image = image;
    }

    /**
     * Returns the container for the given database and image, starting it on the first call.
     */
    static DatabaseContainer of(TestDatabase database, String image) {
        requireNonNull(image, "image");
        if (image.isBlank()) {
            throw new IllegalArgumentException("The image of a " + database + " container must not be blank.");
        }
        return CONTAINERS.computeIfAbsent(new Key(database, image), key -> new DatabaseContainer(database, image))
                .start();
    }

    private DatabaseContainer start() {
        synchronized (lock) {
            if (endpoint == null) {
                // Both checks run before any Testcontainers type is touched, so a missing dependency surfaces as
                // this message rather than as a NoClassDefFoundError from deep inside the container start.
                requireClass(database.containerClassName(), "the Testcontainers module for " + database,
                        database.testcontainersArtifact());
                requireClass(database.driverClassName(), "the JDBC driver for " + database, database.driverArtifact());
                endpoint = JdbcContainers.start(database, image);
            }
        }
        return this;
    }

    private void requireClass(String className, String description, String artifact) {
        try {
            Class.forName(className, false, DatabaseContainer.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Running tests on " + database + " requires " + description
                    + " on the test classpath: add " + artifact + " in test scope.", e);
        }
    }

    /**
     * Returns the database this container runs.
     */
    public TestDatabase database() {
        return database;
    }

    /**
     * Returns the Docker image this container runs.
     */
    public String image() {
        return image;
    }

    /**
     * Returns the JDBC URL of the container's own database, the one Testcontainers creates when the container starts.
     * Tests normally use a database of their own instead, see {@link #createDatabase()}.
     */
    public String jdbcUrl() {
        return endpoint().jdbcUrl();
    }

    /**
     * Returns the user Testcontainers configured for the container's own database.
     */
    public String username() {
        return endpoint().username();
    }

    /**
     * Returns the password of {@link #username()}.
     */
    public String password() {
        return endpoint().password();
    }

    private Endpoint endpoint() {
        synchronized (lock) {
            if (endpoint == null) {
                throw new IllegalStateException(this + " has not started.");
            }
            return endpoint;
        }
    }

    /**
     * Creates a fresh, empty database inside this container and returns it. The database is dropped when the returned
     * handle is closed.
     *
     * <p>Every call provisions a new database, so callers that share one container still work in isolation: on
     * PostgreSQL, MySQL, MariaDB and SQL Server the database is a new catalog, on Oracle a new user with its own
     * schema. Creating one takes a fraction of the time a container start takes, which is what makes reusing the
     * container across test classes safe.</p>
     */
    public Database createDatabase() {
        String name = "storm_" + DATABASE_COUNTER.incrementAndGet();
        Endpoint endpoint = endpoint();
        try (Connection admin = DriverManager.getConnection(endpoint.jdbcUrl(), adminUsername(), endpoint.password())) {
            for (String statement : createStatements(name)) {
                execute(admin, statement);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create database " + name + " in " + this + ".", e);
        }
        return new Database(name);
    }

    private void dropDatabase(String name) {
        Endpoint endpoint = endpoint();
        try (Connection admin = DriverManager.getConnection(endpoint.jdbcUrl(), adminUsername(), endpoint.password())) {
            for (String statement : dropStatements(name)) {
                execute(admin, statement);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to drop database " + name + " in " + this + ".", e);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /**
     * The user that provisions databases: the container's own user where it holds the privilege, otherwise the
     * server's administrative account, whose password Testcontainers sets to the same value.
     */
    private String adminUsername() {
        return switch (database) {
            case MYSQL, MARIADB -> "root";
            case ORACLE -> "system";
            default -> endpoint().username();
        };
    }

    private List<String> createStatements(String name) {
        String user = endpoint().username();
        return switch (database) {
            case POSTGRESQL -> List.of("CREATE DATABASE \"" + name + "\"");
            case MYSQL, MARIADB -> List.of(
                    "CREATE DATABASE `" + name + "`",
                    "GRANT ALL PRIVILEGES ON `" + name + "`.* TO '" + user + "'@'%'");
            case MSSQL_SERVER -> List.of("CREATE DATABASE [" + name + "]");
            // The same grants the image gives its application user, so the test user is no more and no less
            // privileged than the one a hand-written container setup connects with.
            case ORACLE -> List.of(
                    "CREATE USER " + name + " IDENTIFIED BY \"" + endpoint().password() + "\" QUOTA UNLIMITED ON USERS",
                    "GRANT DB_DEVELOPER_ROLE TO " + name);
            case H2 -> throw new IllegalStateException(database + " has no container.");
        };
    }

    private List<String> dropStatements(String name) {
        return switch (database) {
            // FORCE terminates connections a test may still hold; without it the drop would wait on them.
            case POSTGRESQL -> List.of("DROP DATABASE \"" + name + "\" WITH (FORCE)");
            case MYSQL, MARIADB -> List.of("DROP DATABASE `" + name + "`");
            case MSSQL_SERVER -> List.of(
                    "ALTER DATABASE [" + name + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE",
                    "DROP DATABASE [" + name + "]");
            case ORACLE -> List.of("DROP USER " + name + " CASCADE");
            case H2 -> throw new IllegalStateException(database + " has no container.");
        };
    }

    private String url(String name) {
        Endpoint endpoint = endpoint();
        String server = endpoint.host() + ":" + endpoint.port();
        return switch (database) {
            case POSTGRESQL -> "jdbc:postgresql://" + server + "/" + name;
            case MYSQL -> "jdbc:mysql://" + server + "/" + name;
            case MARIADB -> "jdbc:mariadb://" + server + "/" + name;
            // The driver encrypts by default since 10.2 and the container has no trusted certificate.
            case MSSQL_SERVER -> "jdbc:sqlserver://" + server + ";databaseName=" + name + ";encrypt=false";
            // On Oracle the database is a user; the connection goes to the container's pluggable database as that
            // user.
            case ORACLE -> endpoint.jdbcUrl();
            case H2 -> throw new IllegalStateException(database + " has no container.");
        };
    }

    private String username(String name) {
        return database == TestDatabase.ORACLE ? name : endpoint().username();
    }

    @Override
    public String toString() {
        return "DatabaseContainer[" + database + ", " + image + "]";
    }

    /**
     * A database provisioned inside a {@link DatabaseContainer} by {@link DatabaseContainer#createDatabase()}. Closing
     * it drops the database.
     *
     * @since 1.14
     */
    public final class Database implements AutoCloseable {

        private final String name;
        private boolean closed;

        private Database(String name) {
            this.name = name;
        }

        /**
         * Returns the container this database lives in.
         */
        public DatabaseContainer container() {
            return DatabaseContainer.this;
        }

        /**
         * Returns the name of the database; on Oracle, the name of the user that owns the schema.
         */
        public String name() {
            return name;
        }

        /**
         * Returns the JDBC URL that connects to this database.
         */
        public String url() {
            return DatabaseContainer.this.url(name);
        }

        /**
         * Returns the user to connect with.
         */
        public String username() {
            return DatabaseContainer.this.username(name);
        }

        /**
         * Returns the password of {@link #username()}.
         */
        public String password() {
            return endpoint().password();
        }

        /**
         * Returns a {@link DataSource} that opens a new connection to this database on every request.
         */
        public DataSource dataSource() {
            return new SimpleDataSource(url(), username(), password());
        }

        /**
         * Drops the database. Subsequent calls have no effect.
         *
         * @throws IllegalStateException if the database cannot be dropped.
         */
        @Override
        public void close() {
            synchronized (this) {
                if (closed) {
                    return;
                }
                closed = true;
            }
            dropDatabase(name);
        }

        @Override
        public String toString() {
            return "Database[" + name + " in " + DatabaseContainer.this + "]";
        }
    }
}
