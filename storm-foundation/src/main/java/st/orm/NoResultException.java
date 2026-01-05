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
package st.orm;

/**
 * Thrown when {@code getSingleResult} is executed on a query and there is no result to return.
 */
public class NoResultException extends PersistenceException {

    /**
     * Constructs a new {@code NoResultException} exception with {@code null} its detail message.
     */
    public NoResultException() {
        super();
    }

    /**
     * Constructs a new {@code NoResultException} exception with the specified detail message.
     *
     * @param message the detail message.
     */
    public NoResultException(String message) {
        super(message);
    }
}
