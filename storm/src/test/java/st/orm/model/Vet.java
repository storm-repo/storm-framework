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

import lombok.Builder;
import st.orm.repository.Entity;
import st.orm.Name;
import st.orm.PK;

/**
 * Simple domain object representing a veterinarian.
 *
 * @author Leon van Zantvoort
 */
@Builder(toBuilder = true)
public record Vet(
        @PK Integer id,
        @Name("first_name") String firstName,
        @Name("last_name") String lastName
) implements Entity<Integer>, Person {
}
