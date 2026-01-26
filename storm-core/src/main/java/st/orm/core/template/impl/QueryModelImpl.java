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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.BindVars;
import st.orm.Data;
import st.orm.Element;
import st.orm.Metamodel;
import st.orm.Operator;
import st.orm.Ref;
import st.orm.SelectMode;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;
import st.orm.core.template.Column;
import st.orm.core.template.Model;
import st.orm.core.template.Query;
import st.orm.core.template.SqlTemplate;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.TemplateString;
import st.orm.core.template.impl.Elements.Expression;
import st.orm.core.template.impl.Elements.ObjectExpression;
import st.orm.core.template.impl.Elements.Subquery;
import st.orm.core.template.impl.Elements.TemplateExpression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static st.orm.ResolveScope.CASCADE;
import static st.orm.ResolveScope.INNER;
import static st.orm.core.template.Templates.alias;
import static st.orm.core.template.Templates.param;
import static st.orm.core.template.impl.SqlParser.removeComments;

/**
 * Query model implementation responsible for translating high-level query expressions into SQL fragments and bind
 * values.
 *
 * <p>This class acts as the central coordinator between template parsing, model resolution, alias management,
 * expression compilation, and parameter binding.</p>
 *
 * <p>It is stateful per query and assumes that table aliases, joins, and models have already been established by the
 * surrounding query building process.</p>
 *
 * @since 1.8
 */
final class QueryModelImpl implements QueryModel {

    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    private final SqlTemplate template;
    private final ModelBuilder modelBuilder;
    private final AliasedTable table;
    private final TableMapper tableMapper;
    private final AliasMapper aliasMapper;

    private final Model<?, ?> model;

    /**
     * Creates a new query model for the given SQL template and root table.
     *
     * @param template     the SQL template driving compilation and dialect behavior.
     * @param modelBuilder the model builder used to resolve entity metadata.
     * @param table        the root aliased table of the query.
     * @param tableMapper  mapper tracking table usage and uniqueness.
     * @param aliasMapper  mapper responsible for resolving table aliases.
     * @throws SqlTemplateException if the root model cannot be built.
     */
    QueryModelImpl(
            @Nonnull SqlTemplate template,
            @Nonnull ModelBuilder modelBuilder,
            @Nonnull AliasedTable table,
            @Nonnull TableMapper tableMapper,
            @Nonnull AliasMapper aliasMapper) throws SqlTemplateException {
        this.template = template;
        this.modelBuilder = modelBuilder;
        this.table = table;
        this.tableMapper = tableMapper;
        this.aliasMapper = aliasMapper;
        this.model = modelBuilder.build(table.type(), false);
    }

    /**
     * Returns the root table of this query model.
     *
     * <p>The returned table represents the primary table involved in the query and is used as the anchor for alias
     * resolution, column qualification, and model construction.</p>
     *
     * @return the aliased root table of the query.
     */
    @Override
    public AliasedTable getTable() {
        return table;
    }

    /**
     * Returns the columns to be selected for the root table, according to the specified selection mode.
     *
     * <p>The selection mode determines whether only primary key columns, only declared columns, or all nested columns
     * are included.</p>
     *
     * @param mode the selection mode that controls which columns are returned.
     * @return the list of aliased columns for the root table.
     */
    @Override
    public List<AliasedColumn> getColumns(@Nonnull SelectMode mode) {
        return getColumns(table.type(), mode);
    }

    /**
     * Returns the columns to be selected for the specified table type, according to the given selection mode.
     *
     * <p>If the requested table type differs from the root table, a corresponding model is resolved or built to
     * determine the correct column set.</p>
     *
     * @param table the table type for which columns should be returned.
     * @param mode  the selection mode that controls which columns are included.
     * @return the list of aliased columns for the specified table type.
     */
    @Override
    public List<AliasedColumn> getColumns(@Nonnull Class<? extends Data> table, @Nonnull SelectMode mode) {
        try {
            var m = model.type() == table ? model : modelBuilder.build(table, false);
            return switch (mode) {
                case PK -> m.declaredColumns().stream().filter(Column::primaryKey).map(this::toAliasedColumn).toList();
                case DECLARED -> m.declaredColumns().stream().map(this::toAliasedColumn).toList();
                case NESTED -> m.columns().stream().map(this::toAliasedColumn).toList();
            };
        } catch (SqlTemplateException e) {
            throw new UncheckedSqlTemplateException(e);
        }
    }

    /**
     * Resolves the metamodel to be used for an object-based expression.
     *
     * <p>The metamodel may be explicitly provided by the expression or inferred from the runtime object type,
     * reference type, or primary key value.</p>
     *
     * @param objectExpression the expression containing the object reference.
     * @return the resolved metamodel.
     * @throws SqlTemplateException if the metamodel cannot be uniquely determined.
     */
    private Metamodel<?, ?> getMetamodel(@Nonnull ObjectExpression objectExpression)
            throws SqlTemplateException {
        Metamodel<?, ?> metamodel = objectExpression.metamodel();
        if (metamodel != null) {
            return metamodel;
        }
        for (var object : getObjectIterable(objectExpression.object())) {
            var type = switch (object) {
                case Ref<?> ref -> ref.type();
                case Data data -> data.getClass();
                default -> null;
            };
            if (type != null) {
                if (tableMapper.isUnique(type)) {
                    var m = model.findMetamodel(type);
                    if (m.isPresent()) {
                        return m.get();
                    } else {
                        return modelBuilder.build(type, true).getPrimaryKeyMetamodel().orElseThrow();
                    }
                }
                throw new SqlTemplateException("Cannot uniquely identify object in expression.");
            }
            if (isPrimaryKeyValue(object, model.primaryKeyType())) {
                return model.getPrimaryKeyMetamodel().orElseThrow();
            }
            throw new SqlTemplateException("Cannot identify object in expression.");
        }
        // We cannot check the metamodel in case of an empty list. In this case, we return a root metamodel and deal
        // with the empty list later.
        return model.getPrimaryKeyMetamodel()
                .orElseThrow(() -> new SqlTemplateException("Cannot identify object in expression."));
    }

    /**
     * Compiles the given expression into its SQL representation.
     *
     * <p>This method resolves the expression type and delegates to the appropriate compilation strategy. Any template
     * placeholders or object-based expressions are converted into SQL fragments using the provided compiler.</p>
     *
     * @param expression the expression to compile.
     * @param compiler   the compiler responsible for producing SQL fragments.
     * @return the compiled SQL fragment representing the expression.
     */
    @Override
    public String compileExpression(@Nonnull Expression expression, @Nonnull TemplateCompiler compiler) {
        try {
            return switch (expression) {
                case TemplateExpression it -> compileTemplateExpression(it.template(), compiler);
                case ObjectExpression it -> compileObjectExpression(getMetamodel(it), it.operator(), it.object(), compiler);
            };
        } catch (SqlTemplateException e) {
            throw new UncheckedSqlTemplateException(e);
        }
    }

    /**
     * Binds all parameters required by the given expression to the provided binder.
     *
     * <p>The binding order is guaranteed to match the order used during compilation of the same expression. Nested
     * expressions and object-based expressions are handled recursively.</p>
     *
     * @param expression the expression whose parameters should be bound.
     * @param binder     the binder responsible for collecting parameter values.
     */
    @Override
    public void bindExpression(@Nonnull Expression expression, @Nonnull TemplateBinder binder) {
        try {
            switch (expression) {
                case TemplateExpression it -> bindTemplateExpression(it.template(), binder);
                case ObjectExpression it -> bindObjectExpression(getMetamodel(it), it.object(), binder);
            }
        } catch (SqlTemplateException e) {
            throw new UncheckedSqlTemplateException(e);
        }
    }

    /**
     * Compiles a {@link TemplateString} into an SQL fragment.
     *
     * <p>Template fragments are concatenated while embedded values are resolved into SQL elements, parameters,
     * aliases, or nested expressions.</p>
     *
     * @param stringTemplate the template to compile.
     * @param compiler       the compiler used to generate SQL fragments.
     * @return the compiled SQL fragment.
     * @throws SqlTemplateException if an unsupported value is encountered.
     */
    private String compileTemplateExpression(@Nonnull TemplateString stringTemplate, @Nonnull TemplateCompiler compiler) throws SqlTemplateException{
        var fragments = stringTemplate.fragments();
        var values = stringTemplate.values();
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < fragments.size(); i++) {
            String fragment = fragments.get(i);
            parts.add(fragment);
            if (i < values.size()) {
                Object value = resolveElements(values.get(i), fragment, i + 1 < fragments.size() ? fragments.get(i + 1) : "");
                switch (value) {
                    case Stream<?> ignore -> throw new SqlTemplateException("Stream not supported in expression.");
                    case Query ignore -> throw new SqlTemplateException("Query not supported in expression. Use QueryBuilder instead.");
                    case Expression it -> parts.add(compileExpression(it, compiler));
                    case Ref<?> it -> parts.add(compileExpression(new ObjectExpression(it), compiler));
                    case Data it -> parts.add(compileExpression(new ObjectExpression(it), compiler));
                    case Class<?> it -> parts.add(compiler.compile(alias(REFLECTION.getDataType(it))));
                    case Object it when REFLECTION.isSupportedType(it) -> parts.add(compiler.compile(alias(REFLECTION.getDataType(it))));
                    case Element it -> parts.add(compiler.compile(it));
                    default -> parts.add(compiler.compile(param(value)));
                }
            }
        }
        return String.join("", parts);
    }

    /**
     * Binds all parameters required by a {@link TemplateString}.
     *
     * <p>The binding order matches the order used during compilation of the same template.</p>
     *
     * @param stringTemplate the template whose parameters should be bound.
     * @param binder         the binder collecting parameter values.
     * @throws SqlTemplateException if an unsupported value is encountered.
     */
    private void bindTemplateExpression(@Nonnull TemplateString stringTemplate, @Nonnull TemplateBinder binder) throws SqlTemplateException{
        var fragments = stringTemplate.fragments();
        var values = stringTemplate.values();
        for (int i = 0; i < fragments.size(); i++) {
            String fragment = fragments.get(i);
            if (i < values.size()) {
                Object value = resolveElements(values.get(i), fragment, i + 1 < fragments.size() ? fragments.get(i + 1) : "");
                switch (value) {
                    case Stream<?> ignore -> throw new SqlTemplateException("Stream not supported in expression.");
                    case Query ignore -> throw new SqlTemplateException("Query not supported in expression. Use QueryBuilder instead.");
                    case Expression it -> bindExpression(it, binder);
                    case Ref<?> it -> bindExpression(new ObjectExpression(it), binder);
                    case Data it -> bindExpression(new ObjectExpression(it), binder);
                    case Class<?> it -> binder.bind(alias(REFLECTION.getDataType(it)));
                    case Object it when REFLECTION.isSupportedType(it) -> binder.bind(alias(REFLECTION.getDataType(it)));
                    case Element it -> binder.bind(it);
                    default -> binder.bind(param(value));
                }
            }
        }
    }

    /**
     * Transforms or casts the specified {@code object} into an iterable based on its type.
     *
     * @param object the object to transform.
     * @return the iterable based on the object type.
     * @throws SqlTemplateException if the object cannot be transformed or cast into an iterable.
     */
    private Iterable<?> getObjectIterable(@Nullable Object object) throws SqlTemplateException {
        return switch (object) {
            case null -> throw new SqlTemplateException("Null object not allowed, use IS_NULL operator instead.");
            case Object[] a -> asList(a);   // Use this instead of List.of() to allow null values.
            case Iterable<?> i -> i;
            case BindVars ignore -> throw new SqlTemplateException("BindVars not allowed in this context.");
            case Stream<?> ignore -> throw new SqlTemplateException("Stream not allowed in this context. Use Iterable or varargs instead.");
            case TemplateString ignore -> throw new SqlTemplateException("TemplateString not allowed in this context. Use expression method instead.");
            default -> List.of(object); // Not expected at the moment though.
        };
    }

    /**
     * Compiles an object-based expression into an SQL predicate.
     *
     * <p>The object may represent a primary key value, entity instance, reference, or a collection
     * thereof. Multi-column and multi-value expressions are handled transparently.</p>
     *
     * @param metamodel the metamodel describing the target columns.
     * @param operator  the operator used to format the predicate.
     * @param object    the object providing the comparison value(s).
     * @param compiler  the compiler used to generate SQL fragments.
     * @return the compiled SQL predicate.
     * @throws SqlTemplateException if the object cannot be mapped to columns or values.
     */
    private String compileObjectExpression(@Nonnull Metamodel<?, ?> metamodel,
                                           @Nonnull Operator operator,
                                           @Nonnull Object object,
                                           @Nonnull TemplateCompiler compiler) throws SqlTemplateException {
        //noinspection DuplicatedCode
        Model<Data, ?> model = getModel(metamodel);
        List<SequencedMap<String, Object>> multiValues = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        String column = null;
        for (var o : getObjectIterable(object)) {
            //noinspection DuplicatedCode
            SequencedMap<String, Object> valueMap = new LinkedHashMap<>();
            var derivedObject = switch (o) {
                case Ref<?> ref -> ref.id();
                case Data data -> data;
                case Object it -> it;
            };
            //noinspection unchecked
            model.forEachValue((Metamodel<Data, ?>) metamodel, derivedObject,
                    (k, v) -> valueMap.put(toFullyQualifiedColumn(k), v));
            if (compiler.isVersionAware()) {
                if (o instanceof Data data) {
                    var versionColumn = model.declaredColumns().stream()
                            .filter(Column::version)
                            .findFirst()
                            .orElseThrow();
                    model.forEachValue(List.of(versionColumn), data,
                            (k, v) -> valueMap.put(toFullyQualifiedColumn(k), v));
                } else {
                    throw new SqlTemplateException("Data object expected for version-aware statement.");
                }
            }
            if (multiValues.isEmpty() && valueMap.size() == 1) {
                var entry = valueMap.entrySet().iterator().next();
                var k = entry.getKey();
                if (column != null && !column.equals(k)) {
                    throw new SqlTemplateException("Multiple columns specified by where-clause argument: %s and %s.".formatted(column, k));
                }
                placeholders.add(compiler.mapParameter(entry.getValue()));
                column = k;
            } else {
                if (column != null) {
                    throw new SqlTemplateException("Multiple columns specified by where-clause arguments.");
                }
                multiValues.add(valueMap);
            }
        }
        if (!multiValues.isEmpty()) {
            return compileMultiValues(multiValues, compiler);
        }
        if (column == null) {
            column = toFullyQualifiedColumn(model.getSingleColumn(metamodel));
        }
        try {
            return operator.format(column, placeholders.toArray(new String[0]));
        } catch (IllegalArgumentException e) {
            throw new SqlTemplateException(e);
        }
    }

    /**
     * Binds parameter values for an object-based expression.
     *
     * <p>The binding order matches the placeholder order produced during compilation.</p>
     *
     * @param metamodel the metamodel describing the target columns.
     * @param object    the object providing the bind values.
     * @param binder    the binder collecting parameter values.
     * @throws SqlTemplateException if binding fails or versioning rules are violated.
     */
    private void bindObjectExpression(@Nonnull Metamodel<?, ?> metamodel,
                                      @Nonnull Object object,
                                      @Nonnull TemplateBinder binder) throws SqlTemplateException {
        var model = getModel(metamodel);
        List<List<Object>> multiValues = new ArrayList<>();
        for (var o : getObjectIterable(object)) {
            //noinspection DuplicatedCode
            var valueList = new ArrayList<>();
            var derivedObject = switch (o) {
                case Ref<?> ref -> ref.id();
                case Data data -> data;
                case Object it -> it;
            };
            //noinspection unchecked
            model.forEachValue((Metamodel<Data, ?>) metamodel, derivedObject,
                    (k, v) -> valueList.add(v));
            if (binder.isVersionAware()) {
                if (o instanceof Data data) {
                    var versionColumn = model.declaredColumns().stream()
                            .filter(Column::version)
                            .findFirst()
                            .orElseThrow();
                    model.forEachValue(List.of(versionColumn), data, (k, v) -> valueList.add(v));
                } else {
                    throw new SqlTemplateException("Data object expected for version-aware statement.");
                }
            }
            if (multiValues.isEmpty() && valueList.size() == 1) {
                binder.bindParameter(valueList.getFirst());
            } else {
                multiValues.add(valueList);
            }
        }
        if (!multiValues.isEmpty()) {
            bindMultiValues(multiValues, binder);
        }
    }

    /**
     * Compiles a multi-column, multi-row value set into a dialect-specific SQL fragment.
     *
     * @param multiValues the list of column-to-value mappings.
     * @param compiler   the compiler used to map parameters.
     * @return the compiled SQL fragment.
     * @throws SqlTemplateException if the dialect cannot handle the value set.
     */
    private String compileMultiValues(@Nonnull List<SequencedMap<String, Object>> multiValues,
                                      @Nonnull TemplateCompiler compiler) throws SqlTemplateException {
        return compiler.dialect().multiValueIn(multiValues, compiler::mapParameter);
    }

    /**
     * Binds all values of a multi-row expression in the correct order.
     *
     * @param multiValues the values to bind.
     * @param binder     the binder collecting parameter values.
     */
    private void bindMultiValues(@Nonnull List<List<Object>> multiValues,
                                 @Nonnull TemplateBinder binder) {
        multiValues.forEach(list -> list.forEach(binder::bindParameter));
    }

    /**
     * Determines whether the given value matches the expected primary key type.
     *
     * @param value  the value to test.
     * @param pkType the primary key type.
     * @return {@code true} if the value represents a primary key value.
     */
    private boolean isPrimaryKeyValue(@Nonnull Object value, @Nullable Class<?> pkType) {
        if (pkType == null) {
            return false;
        }
        if (pkType == value.getClass()) {
            return true;
        }
        return pkType.isPrimitive() && isPrimitiveCompatible(value, pkType);
    }

    /**
     * Tests if the specified object is compatible with the specified primitive class.
     *
     * @param o the object to test.
     * @param clazz the primitive class to test against.
     * @return {@code true} if the object is compatible with the primitive class, {@code false} otherwise.
     */
    private static boolean isPrimitiveCompatible(@Nonnull Object o, @Nonnull Class<?> clazz) {
        if (clazz == int.class) return o instanceof Integer;
        if (clazz == long.class) return o instanceof Long;
        if (clazz == boolean.class) return o instanceof Boolean;
        if (clazz == byte.class) return o instanceof Byte;
        if (clazz == char.class) return o instanceof Character;
        if (clazz == short.class) return o instanceof Short;
        if (clazz == float.class) return o instanceof Float;
        if (clazz == double.class) return o instanceof Double;
        return false;
    }

    private static final Pattern ENDS_WITH_OPERATOR = Pattern.compile(".*[<=>]$");
    private static final Pattern STARTS_WITH_OPERATOR = Pattern.compile("^[<=>].*");

    /**
     * Resolves a template value into a form that can be processed by the compiler or binder.
     *
     * <p>This method validates contextual correctness, such as preventing records from being used
     * next to operators.</p>
     *
     * @param value            the value to resolve.
     * @param previousFragment the preceding SQL fragment.
     * @param nextFragment     the following SQL fragment.
     * @return the resolved value.
     * @throws SqlTemplateException if the value is invalid in this context.
     */
    private Object resolveElements(@Nullable Object value, @Nonnull String previousFragment, @Nonnull String nextFragment) throws SqlTemplateException {
        return switch (value) {
            case TemplateString ignore -> throw new SqlTemplateException("TemplateString not allowed as string template value.");
            case Stream<?> ignore -> throw new SqlTemplateException("Stream not supported as string template value.");
            case Subqueryable t -> new Subquery(t.getSubquery(), true);
            case Metamodel<?, ?> m when m.isColumn() -> new st.orm.core.template.impl.Elements.Column(m, CASCADE);
            case Metamodel<?, ?> ignore -> throw new SqlTemplateException("Metamodel does not reference a column.");
            case null, default -> {
                if (!(value instanceof Element) && value instanceof Record) {
                    String previous = removeComments(previousFragment, template.dialect()).stripTrailing().toUpperCase();
                    if (ENDS_WITH_OPERATOR.matcher(previous).find()) {
                        throw new SqlTemplateException("Record not allowed in expression with operator.");
                    }
                    String next = removeComments(nextFragment, template.dialect()).stripLeading().toUpperCase();
                    if (STARTS_WITH_OPERATOR.matcher(next).find()) {
                        throw new SqlTemplateException("Record not allowed in expression with operator.");
                    }
                }
                yield value;
            }
        };
    }

    /**
     * Resolves the {@link Model} instance corresponding to the given metamodel.
     *
     * @param metamodel the metamodel describing the table.
     * @param <T>       the entity type.
     * @return the resolved model.
     */
    private <T extends Data> Model<T, ?> getModel(@Nonnull Metamodel<?, ?> metamodel) {
        if (model.type() == metamodel.root()) {
            //noinspection unchecked
            return (Model<T, ?>) model;
        }
        try {
            //noinspection unchecked
            return (Model<T, ?>) modelBuilder.build(metamodel.tableType(), false);
        } catch (SqlTemplateException e) {
            throw new UncheckedSqlTemplateException(e);
        }
    }

    /**
     * Converts the specified column into an {@link AliasedColumn} using the current alias resolution rules.
     *
     * <p>The resolved alias depends on the column's metamodel path and the joins that were introduced while building
     * the query model. An exception is thrown if no suitable alias can be found.</p>
     *
     * @param column the column to convert.
     * @return the aliased representation of the column.
     */
    @Override
    public AliasedColumn toAliasedColumn(@Nonnull Column column) {
        try {
            var metamodel = column.metamodel();
            String alias;
            if (metamodel.root() == model.type() && metamodel.path().isEmpty()) {
                alias = table.alias();
            } else {
                alias = aliasMapper.findAlias(metamodel.tableType(), metamodel.path(), INNER).orElse(null);
            }
            if (alias == null) {
                alias = aliasMapper.findAlias(metamodel.tableType(), null, INNER).orElse(null);
            }
            if (alias == null) {
                throw new SqlTemplateException("Cannot find alias for column: %s.".formatted(column.qualifiedName(template.dialect())));
            }
            return new AliasedColumn(column.type(), column.qualifiedName(template.dialect()), alias, column.index());
        } catch (SqlTemplateException e) {
            throw new UncheckedSqlTemplateException(e);
        }
    }

    /**
     * Returns the fully qualified column name including its table alias.
     *
     * @param column the column to qualify.
     * @return the fully qualified column name.
     */
    private String toFullyQualifiedColumn(@Nonnull Column column) {
        var aliasedColumn = toAliasedColumn(column);
        return "%s%s".formatted(aliasedColumn.alias().isEmpty() ? "" : aliasedColumn.alias() + ".", aliasedColumn.name());
    }
}