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
package st.orm.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import st.orm.Ref;

import java.io.IOException;

@SuppressWarnings("rawtypes")
public class RefSerializer extends StdSerializer<Ref> {

    public RefSerializer() {
        super(Ref.class, false);
    }

    @Override
    public void serialize(Ref ref, JsonGenerator generator, SerializerProvider provider)
            throws IOException {
        generator.writeObject(ref.id());
    }
}