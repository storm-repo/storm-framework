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
import st.orm.template.SqlTemplateException;

import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import java.util.function.Function;

public interface ORMConverter {

    default int getParameterCount() throws SqlTemplateException {
        return getParameterTypes().size();
    }

    List<Class<?>> getParameterTypes() throws SqlTemplateException;

    Object convert(@Nonnull Object[] args) throws SqlTemplateException;

    List<String> getColumns(@Nonnull Function<RecordComponent, String> columnNameResolver) throws SqlTemplateException;

    List<Object> getValues(@Nonnull Record record) throws SqlTemplateException;

    default SequencedMap<String, Object> getValues(@Nonnull Record record, @Nonnull Function<RecordComponent, String> columnNameResolver) throws SqlTemplateException {
        var columns = getColumns(columnNameResolver);
        var values = getValues(record);
        if (columns.size() != values.size()) {
            throw new SqlTemplateException("Column count does not match value count.");
        }
        var map = new LinkedHashMap<String, Object>(columns.size(), 1f);
        for (int i = 0; i < columns.size(); i++) {
            map.put(columns.get(i), values.get(i));
        }
        return map;
    }
}
