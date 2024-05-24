/*
 * Copyright 2024 the original author or authors.
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
package st.orm.spi;

import jakarta.annotation.Nonnull;
import st.orm.FK;
import st.orm.Inline;
import st.orm.PK;

import java.lang.reflect.RecordComponent;
import java.util.Optional;
import java.util.stream.Stream;

import static st.orm.spi.Providers.getORMReflection;

public interface ORMConverterProvider extends Provider {

    default boolean isSupported(@Nonnull RecordComponent component) {
        var reflection = getORMReflection();
        return Stream.of(PK.class, FK.class, Inline.class).noneMatch(a -> reflection.isAnnotationPresent(component, a));
    }

    Optional<ORMConverter> getConverter(@Nonnull RecordComponent component);
}
