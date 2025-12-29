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
 * Specifies the selection mode for query operations.
 */
public enum SelectMode {

    /**
     * Only the primary key fields are selected.
     *
     * <p>This mode returns the minimal data necessary to identify records.</p>
     */
    PK,

    /**
     * Only the fields of the main table are selected, without including nested object hierarchies.
     *
     * <p>This mode is useful if you need the basic attributes of the record without fetching associated records.</p>
     */
    FLAT,

    /**
     * The entire object hierarchy is selected.
     *
     * <p>This mode retrieves the full record along with any nested associations, providing a complete view of the
     * record.</p>
     */
    NESTED
}
