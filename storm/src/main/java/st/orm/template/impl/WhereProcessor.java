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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.BindVars;
import st.orm.FK;
import st.orm.PersistenceException;
import st.orm.Query;
import st.orm.Ref;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.Metamodel;
import st.orm.template.Operator;
import st.orm.template.SqlTemplate;
import st.orm.template.SqlTemplate.PositionalParameter;
import st.orm.template.SqlTemplateException;
import st.orm.template.impl.Elements.Alias;
import st.orm.template.impl.Elements.Column;
import st.orm.template.impl.Elements.Expression;
import st.orm.template.impl.Elements.ObjectExpression;
import st.orm.template.impl.Elements.Param;
import st.orm.template.impl.Elements.Subquery;
import st.orm.template.impl.Elements.Table;
import st.orm.template.impl.Elements.TemplateExpression;
import st.orm.template.impl.Elements.Unsafe;
import st.orm.template.impl.Elements.Where;
import st.orm.template.impl.TableMapper.Mapping;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;
import static st.orm.spi.Providers.getORMConverter;
import static st.orm.template.Metamodel.root;
import static st.orm.template.Operator.EQUALS;
import static st.orm.template.ResolveScope.CASCADE;
import static st.orm.template.ResolveScope.INNER;
import static st.orm.template.impl.RecordReflection.getColumnName;
import static st.orm.template.impl.RecordReflection.getForeignKeys;
import static st.orm.template.impl.RecordReflection.getPkComponent;
import static st.orm.template.impl.RecordReflection.getPrimaryKeys;
import static st.orm.template.impl.RecordReflection.getRefRecordType;
import static st.orm.template.impl.RecordReflection.getVersionComponent;
import static st.orm.template.impl.SqlParser.removeComments;

/**
 * A processor for a where element of a template.
 */
final class WhereProcessor implements ElementProcessor<Where> {

    private final static ORMReflection REFLECTION = Providers.getORMReflection();

    private final SqlTemplateProcessor templateProcessor;
    private final SqlTemplate template;
    private final SqlDialectTemplate dialectTemplate;
    private final AliasMapper aliasMapper;
    private final TableMapper tableMapper;
    private final PrimaryTable primaryTable;
    private final List<SqlTemplate.Parameter> parameters;
    private final AtomicInteger parameterPosition;
    private final AtomicBoolean versionAware;

    WhereProcessor(@Nonnull SqlTemplateProcessor templateProcessor) {
        this.templateProcessor = templateProcessor;
        this.template = templateProcessor.template();
        this.dialectTemplate = templateProcessor.dialectTemplate();
        this.aliasMapper = templateProcessor.aliasMapper();
        this.tableMapper = templateProcessor.tableMapper();
        this.primaryTable = templateProcessor.primaryTable();
        this.parameters = templateProcessor.parameters();
        this.parameterPosition = templateProcessor.parameterPosition();
        this.versionAware = templateProcessor.versionAware();
    }

    /**
     * Process a where element of a template.
     *
     * @param where the where element to process.
     * @return the result of processing the element.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    @Override
    public ElementResult process(@Nonnull Where where) throws SqlTemplateException {
        if (primaryTable == null) {
            throw new SqlTemplateException("Primary entity not found.");
        }
        if (where.expression() != null) {
            return new ElementResult(getExpressionString(where.expression()));
        }
        if (where.bindVars() != null) {
            if (where.bindVars() instanceof BindVarsImpl vars) {
                List<String> bindVarsColumns = getBindVarsColumns(primaryTable.table(), primaryTable.alias(), versionAware.getPlain());
                templateProcessor.setBindVars(vars);
                final int fixedParameterPosition = parameterPosition.get();
                vars.addParameterExtractor(record -> {
                    try {
                        AtomicInteger position = new AtomicInteger(fixedParameterPosition);
                        var values = getValues(record, null, primaryTable.table(), primaryTable.table(), primaryTable.alias(), versionAware.getPlain())
                                .values().stream()
                                .map(o -> new PositionalParameter(position.getAndIncrement(), o))
                                .toList();
                        if (values.size() != bindVarsColumns.size()) {
                            throw new PersistenceException(STR."BindVars size mismatch: \{values.size()} != \{bindVarsColumns.size()}.");
                        }
                        return values;
                    } catch (SqlTemplateException ex) {
                        // BindVars works at the abstraction level of the ORM, so we throw a PersistenceException here.
                        throw new PersistenceException(ex);
                    }
                });
                parameterPosition.set(parameterPosition.get() + bindVarsColumns.size());
                String bindVarsString = bindVarsColumns.stream()
                        .map(columnName -> STR."\{columnName} = ?")
                        .collect(joining(" AND "));
                return new ElementResult(bindVarsString);
            }
            throw new SqlTemplateException("Unsupported BindVars type.");
        }
        throw new SqlTemplateException("No values found for Where.");
    }

    //
    // This class will utilize a QueryModel to simplify / unify the record mapping logic, similarly to how the Model is
    // used to generate insert and update SQL. This will allow for a more consistent and maintainable code
    // base.
    //

    /**
     * Returns the bind-vars string for the specified {@code recordType}.
     *
     * @param recordType the record type.
     * @param alias the alias to use for the column names.
     * @param updating whether version columns must be included to uniquely identify the record.
     * @return the bind-vars string for the specified record type.
     * @throws SqlTemplateException if the bind-vars string cannot be resolved.
     */
    private List<String> getBindVarsColumns(@Nonnull Class<? extends Record> recordType,
                                            @Nonnull String alias,
                                            boolean updating) throws SqlTemplateException {
        var pkComponent = getPkComponent(recordType).orElseThrow(() ->
                new SqlTemplateException(STR."Primary key not found for \{recordType.getSimpleName()}."));
        List<String> names = new ArrayList<>();
        getPrimaryKeys(pkComponent, template.columnNameResolver()).stream()
                .map(columnName -> dialectTemplate."\{alias.isEmpty() ? "" : STR."\{alias}."}\{columnName}")
                .forEach(names::add);
        if (updating) {
            var versionComponent = getVersionComponent(recordType).orElse(null);
            if (versionComponent != null) {
                names.add(dialectTemplate."\{alias.isEmpty() ? "" : STR."\{alias}."}\{getColumnName(versionComponent, template.columnNameResolver())}");
            }
        }
        return names;
    }

    @SuppressWarnings("DuplicatedCode")
    private Object validatePk(@Nullable Object id, @Nonnull RecordComponent component) throws SqlTemplateException {
        if (id == null) {
            throw new SqlTemplateException(STR."Null primary key value for \{component.getType()}.\{component.getName()}.");
        }
        if (REFLECTION.isDefaultValue(id)) {
            throw new SqlTemplateException(STR."Default primary key value for \{component.getType()}.\{component.getName()}.");
        }
        return id;
    }

    @SuppressWarnings("DuplicatedCode")
    private Object validateFk(@Nullable Object id, @Nonnull RecordComponent component) throws SqlTemplateException {
        if (id == null) {
            throw new SqlTemplateException(STR."Null foreign key value for \{component.getType()}.\{component.getName()}.");
        }
        if (REFLECTION.isDefaultValue(id)) {
            throw new SqlTemplateException(STR."Default foreign key value for \{component.getType()}.\{component.getName()}.");
        }
        return id;
    }

    @SuppressWarnings("DuplicatedCode")
    private SequencedMap<String, Object> getPkValues(@Nonnull Record record,
                                                     @Nonnull RecordComponent pkComponent,
                                                     @Nonnull String alias,
                                                     boolean updating) throws SqlTemplateException {
        try {
            var values = new LinkedHashMap<String, Object>();
            var pkNames = getPrimaryKeys(pkComponent, template.columnNameResolver());
            var id = validatePk(REFLECTION.invokeComponent(pkComponent, record), pkComponent);
            if (!pkComponent.getType().isRecord()) {
                assert pkNames.size() == 1;
                values.put(dialectTemplate."\{alias.isEmpty() ? "" : STR."\{alias}."}\{pkNames.getFirst()}", id);
            } else {
                var nestedPkComponents = pkComponent.getType().getRecordComponents();
                assert pkNames.size() == nestedPkComponents.length;
                for (int i = 0; i < nestedPkComponents.length; i++) {
                    values.put(dialectTemplate."\{alias.isEmpty() ? "" : STR."\{alias}."}\{pkNames.get(i)}", validatePk(REFLECTION.invokeComponent(nestedPkComponents[i], id), nestedPkComponents[i]));
                }
            }
            if (updating) {
                var versionComponent = getVersionComponent(record.getClass()).orElse(null);
                if (versionComponent != null) {
                    values.put(dialectTemplate."\{alias.isEmpty() ? "" : STR."\{alias}."}\{getColumnName(versionComponent, template.columnNameResolver())}", REFLECTION.invokeComponent(versionComponent, record));
                }
            }
            return values;
        } catch (SqlTemplateException e) {
            throw e;
        } catch (Throwable t) {
            throw new SqlTemplateException(t);
        }
    }

    @SuppressWarnings({"DuplicatedCode", "unchecked"})
    private SequencedMap<String, Object> getFkValues(@Nullable Object id,
                                                     @Nonnull RecordComponent fkComponent,
                                                     @Nonnull String alias) throws SqlTemplateException {
        try {
            var values = new LinkedHashMap<String, Object>();
            Class<? extends Record> fkType;
            if (Ref.class.isAssignableFrom(fkComponent.getType())) {
                fkType = getRefRecordType(fkComponent);
            } else {
                fkType = (Class<? extends Record>) fkComponent.getType();
            }
            var fkNames = getForeignKeys(fkComponent, template.foreignKeyResolver(), template.columnNameResolver());
            var pkComponent = getPkComponent(fkType).orElseThrow(() ->
                    new SqlTemplateException(STR."Primary key not found for \{fkType.getSimpleName()}"));
            if (!pkComponent.getType().isRecord()) {
                assert fkNames.size() == 1;
                values.put(dialectTemplate."\{alias.isEmpty() ? "" : STR."\{alias}."}\{fkNames.getFirst()}", REFLECTION.isDefaultValue(id) ? null : id);
                return values;
            } else {
                var nestedPkComponents = pkComponent.getType().getRecordComponents();
                assert fkNames.size() == nestedPkComponents.length;
                for (int i = 0; i < nestedPkComponents.length; i++) {
                    values.put(dialectTemplate."\{alias.isEmpty() ? "" : STR."\{alias}."}\{fkNames.get(i)}", id == null ? null : validateFk(REFLECTION.invokeComponent(nestedPkComponents[i], id), nestedPkComponents[i]));
                }
            }
            return values;
        } catch (SqlTemplateException e) {
            throw e;
        } catch (Throwable t) {
            throw new SqlTemplateException(t);
        }
    }

    @SuppressWarnings({"DuplicatedCode", "unchecked"})
    private SequencedMap<String, Object> getFkValues(@Nullable Record record,
                                                     @Nonnull RecordComponent fkComponent,
                                                     @Nonnull String alias) throws SqlTemplateException {
        try {
            var values = new LinkedHashMap<String, Object>();
            var fkNames = getForeignKeys(fkComponent, template.foreignKeyResolver(), template.columnNameResolver());
            Class<? extends Record> fkType;
            if (Ref.class.isAssignableFrom(fkComponent.getType())) {
                fkType = getRefRecordType(fkComponent);
            } else {
                fkType = (Class<? extends Record>) fkComponent.getType();
            }
            var pkComponent = getPkComponent(fkType).orElseThrow(() ->
                    new SqlTemplateException(STR."Primary key not found for \{fkType.getSimpleName()}"));
            if (!pkComponent.getType().isRecord()) {
                assert fkNames.size() == 1;
                var id = record == null ? null : REFLECTION.invokeComponent(pkComponent, record);
                values.put(dialectTemplate."\{alias.isEmpty() ? "" : STR."\{alias}."}\{fkNames.getFirst()}", REFLECTION.isDefaultValue(id) ? null : id);
                return values;
            } else {
                var pk = record == null ? null : REFLECTION.invokeComponent(pkComponent, record);
                var nestedPkComponents = pkComponent.getType().getRecordComponents();
                assert fkNames.size() == nestedPkComponents.length;
                for (int i = 0; i < nestedPkComponents.length; i++) {
                    values.put(dialectTemplate."\{alias.isEmpty() ? "" : STR."\{alias}."}\{fkNames.get(i)}", pk == null ? null : validateFk(REFLECTION.invokeComponent(nestedPkComponents[i], pk), nestedPkComponents[i]));
                }
            }
            return values;
        } catch (SqlTemplateException e) {
            throw e;
        } catch (Throwable t) {
            throw new SqlTemplateException(t);
        }
    }

    /**
     * Maps the values of the specified {@code record} to a map of column names and values.
     *
     * @param record the record to map the values for.
     * @param path the path to the record.
     * @param primaryTable the primary table of the query.
     * @param rootTable the root table of the expression.
     * @param alias the alias of the root table.
     * @param updating whether the record is being used in an update mode.
     * @return the map of column names and values for the specified record.
     * @throws SqlTemplateException if the values cannot be mapped.
     */
    private SequencedMap<String, Object> getValues(@Nonnull Record record,
                                                   @Nullable String path,
                                                   @Nonnull Class<? extends Record> primaryTable,
                                                   @Nonnull Class<? extends Record> rootTable,
                                                   @Nonnull String alias,
                                                   boolean updating) throws SqlTemplateException {
        try {
            if (rootTable.isInstance(record)) {
                RecordComponent pkComponent = getPkComponent(record.getClass()).orElseThrow(() ->
                        new SqlTemplateException(STR."Primary key not found for \{record.getClass().getSimpleName()}"));
                return getPkValues(record, pkComponent, alias, updating);
            }
            String searchPath = rootTable != primaryTable && path != null && path.isEmpty() ? null : path;
            Mapping mapping = tableMapper.getMapping(record.getClass(), searchPath == null ? null : rootTable, searchPath);
            String a = mapping.alias();
            if (mapping.primaryKey()) {
                return getPkValues(record, mapping.component(), a, false);  // Updating is only supported for root tables.
            }
            return getFkValues(record, mapping.component(), a); // Updating is only supported for root tables.
        } catch (SqlTemplateException e) {
            throw e;
        } catch (Throwable t) {
            throw new SqlTemplateException(t);
        }
    }

    /**
     * Maps the values of the specified {@code id} to a map of column names and values.
     *
     * @param id the id to map the values for (optional).
     * @param recordType the record type.
     * @param alias the alias to use for the column names.
     * @return the map of column names and values for the specified id.
     * @throws SqlTemplateException if the values cannot be mapped.
     */
    @SuppressWarnings("DuplicatedCode")
    private SequencedMap<String, Object> getValues(@Nullable Object id,
                                                   @Nonnull Class<? extends Record> recordType,
                                                   @Nonnull String alias) throws SqlTemplateException {
        try {
            var values = new LinkedHashMap<String, Object>();
            RecordComponent pkComponent = getPkComponent(recordType).orElseThrow(() ->
                    new SqlTemplateException(STR."Primary key not found for \{recordType.getSimpleName()}."));
            var pkNames = getPrimaryKeys(pkComponent, template.columnNameResolver());
            if (!pkComponent.getType().isRecord()) {
                assert pkNames.size() == 1;
                values.put(dialectTemplate."\{alias.isEmpty() ? "" : STR."\{alias}."}\{pkNames.getFirst()}", id);
                return values;
            }
            var nestedPkComponents = pkComponent.getType().getRecordComponents();
            assert pkNames.size() == nestedPkComponents.length;
            for (int i = 0; i < nestedPkComponents.length; i++) {
                values.put(dialectTemplate."\{alias.isEmpty() ? "" : STR."\{alias}."}\{pkNames.get(i)}", id == null ? null : REFLECTION.invokeComponent(nestedPkComponents[i], id));
            }
            return values;
        } catch (SqlTemplateException e) {
            throw e;
        } catch (Throwable t) {
            throw new SqlTemplateException(t);
        }
    }

    /**
     * Maps the values of the specified {@code value} to a map of column names and values.
     *
     * @param value the object to map the values for.
     * @param path the path to the value.
     * @param primaryTable the primary table.
     * @param recordType the record type.
     * @return the map of column names and values for the specified value.
     * @throws SqlTemplateException if the values cannot be mapped.
     */
    private SequencedMap<String, Object> getValues(@Nullable Object value,
                                                   @Nonnull String path,
                                                   @Nonnull Class<? extends Record> primaryTable,
                                                   @Nonnull Class<? extends Record> recordType) throws SqlTemplateException {
        return getValuesForPath(value, path, primaryTable, recordType, 0, null, 0);
    }

    private SequencedMap<String, Object> getNestedValuesAtPath(
            @Nullable Object value,
            @Nonnull Class<? extends Record> primaryTable,
            @Nonnull Class<? extends Record> recordType,
            int depth,
            int inlineDepth,
            @Nonnull String[] parts,
            @Nonnull RecordComponent[] components
    ) throws SqlTemplateException {
        var values = new LinkedHashMap<String, Object>();
        String name = parts[depth];
        for (var component : components) {
            if (component.getName().equals(name)) {
                String searchPath = Stream.of(parts).limit(depth - inlineDepth).collect(joining("."));
                String alias;
                if (recordType != primaryTable && searchPath.isEmpty()) {
                    alias = aliasMapper.getAlias(recordType, null, INNER, template.dialect(),
                            () -> new SqlTemplateException(STR."Table \{recordType.getSimpleName()} not found at \{searchPath}."));
                } else {
                    alias = aliasMapper.getAlias(recordType, searchPath, INNER, template.dialect(),
                            () -> new SqlTemplateException(STR."Table \{recordType.getSimpleName()} not found at \{searchPath}."));
                }
                if (REFLECTION.isAnnotationPresent(component, FK.class)) {
                    if (value instanceof Record record) {
                        values.putAll(getFkValues(record, component, alias));
                    } else {
                        values.putAll(getFkValues(value, component, alias));
                    }
                } else {
                    values.put(dialectTemplate."\{alias.isEmpty() ? "" : STR."\{alias}."}\{getColumnName(component, template.columnNameResolver())}", value);
                }
                break;
            }
        }
        return values;
    }

    private SequencedMap<String, Object> getNestedValuesForPath(
            @Nullable Object value,
            @Nonnull String path,
            @Nonnull Class<? extends Record> primaryTable,
            @Nonnull Class<? extends Record> recordType,
            int depth,
            int inlineDepth,
            @Nonnull String[] parts,
            @Nonnull RecordComponent[] components
    ) throws SqlTemplateException {
        var values = new LinkedHashMap<String, Object>();
        for (var component : components) {
            if (component.getName().equals(parts[depth])) {
                if (component.getType().isRecord()) {
                    var converter = getORMConverter(component);
                    if (converter.isPresent()) {
                        continue;
                    }
                    boolean fk = REFLECTION.isAnnotationPresent(component, FK.class);
                    Class<? extends Record> type;
                    Class<? extends Record> inlineType;
                    if (fk) {
                        //noinspection unchecked
                        type = (Class<? extends Record>) component.getType();
                        if (Ref.class.isAssignableFrom(type)) {
                            // Cannot traverse components of a Ref.
                            continue;
                        }
                        inlineType = null;
                        inlineDepth = 0;
                    } else {    // Can either be PK or Inline.
                        // Assuming @Inline; No need to check for optional annotation.
                        type = recordType;
                        //noinspection unchecked
                        inlineType = (Class<? extends Record>) component.getType();
                    }
                    values.putAll(getValuesForPath(value, path, primaryTable, type, depth + 1,
                            inlineType, inlineDepth + (inlineType != null ? 1 : 0)));
                }
                break;
            }
        }
        return values;
    }

    /**
     * Maps the values of the specified {@code value} to a map of column names and values.
     *
     * @param value the object to map the values for.
     * @param path the path to the value.
     * @param primaryTable the primary table.
     * @param recordType the record type.
     * @param depth the depth of the value in the record hierarchy.
     * @param inlineParentType the inline parent type.
     * @param inlineDepth the depth of the inline parent type.
     * @return the map of column names and values for the specified value.
     * @throws SqlTemplateException if the values cannot be mapped.
     */
    private SequencedMap<String, Object> getValuesForPath(@Nullable Object value,
                                                          @Nonnull String path,
                                                          @Nonnull Class<? extends Record> primaryTable,
                                                          @Nonnull Class<? extends Record> recordType,
                                                          int depth,
                                                          @Nullable Class<? extends Record> inlineParentType,
                                                          int inlineDepth) throws SqlTemplateException {
        assert value == null || !value.getClass().isRecord();
        assert !(value instanceof Ref);
        var parts = path.split("\\.");
        var components = (inlineParentType != null ? inlineParentType : recordType).getRecordComponents();
        if (parts.length == depth + 1) {
            return getNestedValuesAtPath(value, primaryTable, recordType, depth, inlineDepth, parts, components);
        }
        return getNestedValuesForPath(value, path, primaryTable, recordType, depth, inlineDepth, parts, components);
    }

    /**
     * Returns the SQL string for the specified {@code template} expression.
     *
     * @param stringTemplate the template to process.
     * @return the SQL string for the specified template expression.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    private String getTemplateExpressionString(@Nonnull StringTemplate stringTemplate) throws SqlTemplateException{
        var fragments = stringTemplate.fragments();
        var values = stringTemplate.values();
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < fragments.size(); i++) {
            String fragment = fragments.get(i);
            parts.add(fragment);
            if (i < values.size()) {
                Object value = resolveElements(values.get(i), fragment, i + 1 < fragments.size() ? fragments.get(i + 1) : "");
                switch (value) {
                    case Expression exp -> parts.add(getExpressionString(exp));
                    case Subquery s -> parts.add(new SubqueryProcessor(templateProcessor).process(s).get());
                    case Column c -> parts.add(new ColumnProcessor(templateProcessor).process(c).get());
                    case Unsafe u -> parts.add(new UnsafeProcessor(templateProcessor).process(u).get());
                    case Table t -> parts.add(new TableProcessor(templateProcessor).process(t).get());
                    case Alias a -> parts.add(new AliasProcessor(templateProcessor).process(a).get());
                    case Param p -> parts.add(new ParamProcessor(templateProcessor).process(p).get());
                    case Ref<?> r -> parts.add(getObjectExpressionString(r));
                    case Record r -> parts.add(getObjectExpressionString(r));
                    case Class<?> c when c.isRecord() -> //noinspection unchecked
                            parts.add(dialectTemplate."\{aliasMapper.getAlias(root((Class<? extends Record>) c), CASCADE, template.dialect())}");
                    case Object k when REFLECTION.isSupportedType(k) -> parts.add(dialectTemplate."\{aliasMapper.getAlias(root(REFLECTION.getRecordType(k)), CASCADE, template.dialect())}");
                    case Stream<?> _ -> throw new SqlTemplateException("Stream not supported in expression.");
                    case Query _ -> throw new SqlTemplateException("Query not supported in expression. Use QueryBuilder instead.");
                    case Element e -> throw new SqlTemplateException(STR."Unsupported element type in expression: \{e.getClass().getSimpleName()}.");
                    default -> parts.add(templateProcessor.registerParam(value));
                }
            }
        }
        return String.join("", parts);
    }

    private static final Pattern ENDS_WITH_OPERATOR = Pattern.compile(".*[<=>]$");
    private static final Pattern STARTS_WITH_OPERATOR = Pattern.compile("^[<=>].*");

    /**
     * Resolves the specified {@code value} into an element that can be processed downstream.
     *
     * @param value the value to resolve the element for.
     * @return the resolved element.
     * @throws SqlTemplateException if the element cannot be resolved.
     */
    private Object resolveElements(@Nullable Object value, @Nonnull String previousFragment, @Nonnull String nextFragment) throws SqlTemplateException {
        return switch (value) {
            case StringTemplate _ -> throw new SqlTemplateException("StringTemplate not allowed as string template value.");
            case Stream<?> _ -> throw new SqlTemplateException("Stream not supported as string template value.");
            case Subqueryable t -> new Subquery(t.getSubquery(), true);
            case Metamodel<?, ?> m when m.isColumn() -> new Column(m, CASCADE);
            case Metamodel<?, ?> _ -> throw new SqlTemplateException("Metamodel does not reference a column.");
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
     * Returns the SQL string for the specified expression.
     *
     * @param expression the expression to process.
     * @return the SQL string for the specified expression.
     * @throws SqlTemplateException if the expression cannot be resolved.
     */
    private String getExpressionString(Expression expression) throws SqlTemplateException {
        return switch(expression) {
            case TemplateExpression it -> getTemplateExpressionString(it.template());
            case ObjectExpression it -> getObjectExpressionString(it.metamodel(), it.operator(), it.object());
        };
    }

    /**
     * Transforms the specified {@code object} into an object expression without using a metamodel or operator.
     *
     * @param object the object to transform.
     * @return the object expression string.
     * @throws SqlTemplateException if the object expression cannot be resolved.
     */
    private String getObjectExpressionString(@Nonnull Object object)
            throws SqlTemplateException {
        return getObjectExpressionString(null, EQUALS, object);
    }

    /**
     * Transforms or casts the specified {@code object} into an iterable based on its type.
     *
     * @param object the object to transform.
     * @return the iterable based on the object type.
     * @throws SqlTemplateException if the object cannot be transformed or cast into an iterable.
     */
    private Iterable<?> getObjectIterable(@Nonnull Object object) throws SqlTemplateException {
        return switch (object) {
            case null -> throw new SqlTemplateException("Null object not allowed.");
            case Object[] a -> asList(a);   // Use this instead of List.of() to allow null values.
            case Iterable<?> i -> i;
            case BindVars _ -> throw new SqlTemplateException("BindVars not allowed in this context.");
            case Stream<?> _ -> throw new SqlTemplateException("Stream not allowed in this context. Use Iterable or varargs instead.");
            case StringTemplate _ -> throw new SqlTemplateException("String template not allowed in this context. Use expression method instead.");
            default -> List.of(object); // Not expected at the moment though.
        };
    }

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
     * Returns the SQL string for the object expression using an optional {@code metamodel}, the {@code operator} and
     * the {@code object}.
     *
     * @param metamodel the metamodel to use for the object expression (optional).
     * @param operator the operator to use for the object expression.
     * @param object the object to represent in the expression.
     * @return the SQL string for the object expression.
     * @throws SqlTemplateException if the object expression cannot be resolved.
     */
    private String getObjectExpressionString(@Nullable Metamodel<?, ?> metamodel,
                                             @Nonnull Operator operator,
                                             @Nonnull Object object) throws SqlTemplateException {
        Class<? extends Record> rootTable;
        String path;
        String alias;
        Class<?> pkType;
        if (primaryTable == null) {
            throw new SqlTemplateException("Primary table unknown.");
        }
        if (metamodel == null) {
            rootTable = primaryTable.table();
            alias = primaryTable.alias();
            pkType = REFLECTION.findPKType(primaryTable.table()).orElse(null);
            path = null;
        } else {
            rootTable = metamodel.root();
            path = metamodel.componentPath();
            alias = aliasMapper.getAlias(rootTable, null, INNER, template.dialect(),
                    () -> new SqlTemplateException(STR."Table \{rootTable.getSimpleName()} not found."));
            pkType = null;  // PKs only supported when using primaryTable directly.
        }
        String column = null;
        int size = 0;
        List<Map<String, Object>> multiValues = new ArrayList<>();
        for (var o : getObjectIterable(object)) {
            if (o == null) {
                throw new SqlTemplateException("Null object not allowed, use IS_NULL operator instead.");
            }
            if (o instanceof Ref<?> ref && ref.isNull()) {
                throw new SqlTemplateException("Null Ref not allowed, use IS_NULL operator instead.");
            }
            Object v = o instanceof Ref<?> ref ? ref.id() : o;
            assert v != null;
            Class<?> elementType = v.getClass();
            Map<String, Object> valueMap;
            if (metamodel == null) {
                if (isPrimaryKeyValue(v, pkType)) {
                    valueMap = getValues(v, rootTable, alias);
                    if (valueMap.isEmpty()) {
                        throw new SqlTemplateException(STR."Failed to find primary key field for \{rootTable.getSimpleName()} table.");
                    }
                } else if (elementType.isRecord()) {
                    assert v instanceof Record;
                    valueMap = getValues((Record) v, null, primaryTable.table(), rootTable, alias, versionAware.getPlain());
                    if (valueMap.isEmpty()) {
                        throw new SqlTemplateException(STR."Failed to find \{o.getClass().getSimpleName()} record on \{rootTable.getSimpleName()} table graph.");
                    }
                } else {
                    throw new SqlTemplateException("Specify a metamodel to uniquely identify a field.");
                }
            } else {
                if (elementType.isRecord()) {
                    assert v instanceof Record;
                    valueMap = getValues((Record) v, path, primaryTable.table(), rootTable, alias, versionAware.getPlain());
                    if (valueMap.isEmpty()) {
                        throw new SqlTemplateException(STR."Failed to find field for \{o.getClass().getSimpleName()} argument on \{rootTable.getSimpleName()} table graph.");
                    }
                } else {
                    assert path != null;
                    valueMap = getValues(v, path, primaryTable.table(), rootTable);
                    if (valueMap.isEmpty()) {
                        throw new SqlTemplateException(STR."Failed to find field for \{o.getClass().getSimpleName()} argument on \{rootTable.getSimpleName()} table at path '\{path}'.");
                    }
                }
            }
            if (multiValues.isEmpty() && valueMap.size() == 1) {
                var entry = valueMap.entrySet().iterator().next();
                parameters.add(new PositionalParameter(parameterPosition.getAndIncrement(), entry.getValue()));
                var k = entry.getKey();
                if (column != null) {
                    if (!column.equals(k)) {
                        throw new SqlTemplateException(STR."Multiple columns specified by where-clause argument: \{column} and \{k}.");
                    }
                }
                column = k;
                size++;
            } else {
                if (column != null) {
                    throw new SqlTemplateException("Multiple columns specified by where-clause arguments.");
                }
                multiValues.add(valueMap);
            }
        }
        if (!multiValues.isEmpty()) {
            return getMultiValuesIn(multiValues);
        }
        if (column == null) {
            var valueMap = path != null
                    ? getValues(null, path, primaryTable.table(), rootTable)
                    : getValues(null, rootTable, alias);
            if (valueMap.size() == 1) {
                column = valueMap.sequencedKeySet().getFirst();
            } else if (valueMap.isEmpty()) {
                throw new SqlTemplateException(STR."Failed to find field on \{rootTable.getSimpleName()} table at path '\{path}'.");
            }
        }
        // If column is still not resolved, we will let the operator take care it, and potentially raise an error.
        // A common (valid) use case is where object is an empty array or collection and no path is specified.
        try {
            return operator.format(column, size);
        } catch (IllegalArgumentException e) {
            throw new SqlTemplateException(e);
        }
    }

    /**
     * Returns the SQL string for the specified values using the multi-value IN clause.
     *
     * @param values the values to process.
     * @return the SQL string for the specified values.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    private String getMultiValuesIn(@Nonnull List<Map<String, Object>> values) throws SqlTemplateException {
        return template.dialect().multiValueIn(values,
                o -> parameters.add(new PositionalParameter(parameterPosition.getAndIncrement(), o)));
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
}
