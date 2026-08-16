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

import java.lang.reflect.InvocationTargetException;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.utility.DockerImageName;
import st.orm.test.DatabaseContainer.Endpoint;

/**
 * The only class of the module that references Testcontainers types. It is loaded when the first container starts,
 * after {@link DatabaseContainer} has verified that Testcontainers is on the classpath, so tests on H2 never load
 * it and a test that names a container database without the dependency fails with a message rather than a
 * {@code NoClassDefFoundError}.
 */
final class JdbcContainers {

    private JdbcContainers() {
    }

    /**
     * Starts a container of the given database from the given image and returns how to reach it.
     */
    static Endpoint start(TestDatabase database, String image) {
        // Any distribution of the database is accepted, such as pgvector/pgvector for PostgreSQL; the container
        // class insists on an image it knows unless told the image stands in for its own.
        String defaultRepository = DockerImageName.parse(database.defaultImage()).getUnversionedPart();
        DockerImageName imageName = DockerImageName.parse(image).asCompatibleSubstituteFor(defaultRepository);
        JdbcDatabaseContainer<?> container = newContainer(database, imageName);
        container.start();
        return new Endpoint(container.getHost(), container.getMappedPort(database.port()), container.getJdbcUrl(),
                container.getUsername(), container.getPassword());
    }

    /**
     * Instantiates the container class of the database through its {@code (DockerImageName)} constructor. Reflection
     * keeps this method free of references to the individual container classes, so verifying it never loads a
     * container module that is not on the classpath.
     */
    private static JdbcDatabaseContainer<?> newContainer(TestDatabase database, DockerImageName imageName) {
        try {
            Class<?> containerClass = Class.forName(database.containerClassName(), true,
                    JdbcContainers.class.getClassLoader());
            return (JdbcDatabaseContainer<?>) containerClass.getConstructor(DockerImageName.class)
                    .newInstance(imageName);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to create the " + database + " container for image " + imageName
                    + ".", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create the " + database + " container for image " + imageName
                    + ".", e);
        }
    }
}
