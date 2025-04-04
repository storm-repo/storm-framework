/*
 * Copyright 2024 - 2025 the original author or authors.
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
 * Thrown when a database or sql problem occurs.
 */
public class PersistenceException extends RuntimeException {

    /**
     * Constructs a new {@code PersistenceException} exception with {@code null} as its detail message.
     */
    public PersistenceException() {
        super();
    }

    /**
     * Constructs a new {@code PersistenceException} exception with the specified detail message.
     * @param message the detail message.
     */
    public PersistenceException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code PersistenceException} exception with the specified detail message and cause.
     * @param message the detail message.
     * @param cause the cause.
     */
    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new {@code PersistenceException} exception with the specified cause.
     * @param cause the cause.
     */
    public PersistenceException(Throwable cause) {
        super(cause);
    }
}
