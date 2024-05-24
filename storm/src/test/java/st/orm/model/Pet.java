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
import st.orm.FK;
import st.orm.Name;
import st.orm.PK;
import st.orm.Persist;
import st.orm.repository.Entity;

import java.time.LocalDate;

/**
 * Simple business object representing a pet.
 */
@Builder(toBuilder = true)
@Name("pet")
public record Pet(
        @PK Integer id,
        @Nonnull String name,
        @Nonnull @Name("birth_date") @Persist(updatable = false) LocalDate birthDate,
        @Nonnull @FK @Name("type_id") @Persist(updatable = false) PetType petType,
        @Nullable @FK @Name("owner_id") Owner owner
) implements Entity<Integer> {}
