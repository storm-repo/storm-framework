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
package st.orm.core.spi;

/**
 * Controls the retention behavior of the transaction-scoped entity cache.
 *
 * <p>This setting determines how aggressively cached entity state is retained for dirty checking purposes. It can be
 * configured via the {@code storm.entity_cache.retention} property (see {@link st.orm.StormConfig}).</p>
 *
 * @since 1.9
 */
public enum CacheRetention {

    /**
     * Observed state may be cleaned up as soon as the application no longer holds a reference to the entity. This
     * minimizes memory overhead. Uses weak references internally.
     */
    MINIMAL,

    /**
     * Observed state is retained more aggressively, improving the dirty-check hit rate at the cost of higher memory
     * usage during transactions. Uses soft references internally.
     */
    AGGRESSIVE
}
