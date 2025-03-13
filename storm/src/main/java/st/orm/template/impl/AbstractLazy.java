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
package st.orm.template.impl;

import st.orm.Lazy;

import java.util.Objects;

/**
 * Abstract implementation of {@link Lazy} to have consistent implementations of
 * {@link #hashCode()}, {@link #equals(Object)}.
 *
 * @param <T> record type.
 * @param <ID> primary key type.
 */
public abstract class AbstractLazy<T extends Record, ID> implements Lazy<T, ID> {

    /**
     * Returns the primary key of the record, may be null if unknown.
     */
    protected abstract Class<T> type();

    /**
     * Returns true if the record has been fetched, false otherwise.
     */
    protected abstract boolean isFetched();

    @Override
    public int hashCode() {
        return Objects.hash(type(), id());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof AbstractLazy<?,?> l) {
            return Objects.equals(type(), l.type())
                    && Objects.equals(id(), l.id());
        }
        return false;
    }

    @Override
    public String toString() {
        return STR."Lazy[pk=\{id()}, fetched=\{isFetched()}]";
    }
}
