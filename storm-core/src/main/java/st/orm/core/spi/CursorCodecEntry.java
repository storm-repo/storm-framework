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

import jakarta.annotation.Nonnull;

/**
 * A cursor codec registration entry, binding a tag, Java type, and codec together.
 *
 * @param tag the unique byte tag (0-255) identifying the type in the serialized cursor format.
 * @param type the Java type this entry handles.
 * @param codec the codec implementation.
 * @param <T> the Java type.
 * @since 1.11
 */
public record CursorCodecEntry<T>(int tag, @Nonnull Class<T> type, @Nonnull CursorCodec<T> codec) {
    public CursorCodecEntry {
        if (tag < 0 || tag > 255) {
            throw new IllegalArgumentException("Tag must be in range [0, 255], got: " + tag + ".");
        }
    }
}
