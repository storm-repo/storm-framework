/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package st.orm.model;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Builder;
import st.orm.Inline;
import st.orm.Name;
import st.orm.PK;
import st.orm.Version;
import st.orm.repository.Entity;

import java.time.Instant;

/**
 * Simple domain object representing an owner.
 *
 */
@Builder(toBuilder = true)
@Name("owner")
public record Owner(
        @PK Integer id,
        @Nonnull @Name("first_name") String firstName,
        @Nonnull @Name("last_name") String lastName,
        @Nonnull @Inline Address address,
        @Nullable String telephone,
        @Version int version
) implements Person, Entity<Integer> {
}
