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
package st.orm.core.model;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Builder;
import st.orm.FK;
import st.orm.Projection;
import st.orm.Version;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Simple domain object representing a visit.
 *
 * @author Leon van Zantvoort
 */
@Builder(toBuilder = true)
public record VisitView(
        @Nonnull LocalDate visitDate,
        @Nullable String description,
        @Nonnull @FK PetView pet,
        @Version Instant timestamp
) implements Projection<Void> {}    // Not exposing the primary key of the underlying visit table.
