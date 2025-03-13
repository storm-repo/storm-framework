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
package st.orm.template.impl;

import st.orm.template.impl.Elements.Alias;
import st.orm.template.impl.Elements.Column;
import st.orm.template.impl.Elements.Delete;
import st.orm.template.impl.Elements.From;
import st.orm.template.impl.Elements.Insert;
import st.orm.template.impl.Elements.Param;
import st.orm.template.impl.Elements.Select;
import st.orm.template.impl.Elements.Set;
import st.orm.template.impl.Elements.Subquery;
import st.orm.template.impl.Elements.Table;
import st.orm.template.impl.Elements.Unsafe;
import st.orm.template.impl.Elements.Update;
import st.orm.template.impl.Elements.Values;
import st.orm.template.impl.Elements.Var;
import st.orm.template.impl.Elements.Where;
import st.orm.template.impl.SqlTemplateImpl.Wrapped;

/**
 * Represents an element in a ST/ORM SQL query.
 */
public sealed interface Element
        permits Alias, Column, Delete, From, Insert, Param, Select, Set, Table, Update, Values, Where, Join, Var, Subquery, Unsafe, Wrapped {

}
