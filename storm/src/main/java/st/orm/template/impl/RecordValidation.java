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
import st.orm.FK;
import st.orm.Inline;
import st.orm.Ref;
import st.orm.PK;
import st.orm.repository.Entity;
import st.orm.repository.Projection;
import st.orm.repository.ProjectionQuery;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.SqlTemplate;
import st.orm.template.SqlTemplate.Parameter;
import st.orm.template.SqlTemplate.PositionalParameter;
import st.orm.template.SqlTemplateException;
import st.orm.template.impl.SqlParser.SqlMode;

import java.lang.reflect.RecordComponent;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.Optional.empty;
import static st.orm.spi.Providers.getORMConverter;
import static st.orm.template.impl.RecordReflection.getFkComponents;
import static st.orm.template.impl.RecordReflection.getPkComponents;
import static st.orm.template.impl.SqlParser.SqlMode.SELECT;
import static st.orm.template.impl.SqlParser.SqlMode.UNDEFINED;

/**
 * Helper class for validating record types and named parameters.
 */
final class RecordValidation {

    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    private RecordValidation() {
    }

    record TypeValidationKey(@Nonnull SqlMode sqlMode, @Nonnull Class<? extends Record> recordType) {}
    private static final Map<TypeValidationKey, String> VALIDATE_RECORD_TYPE_CACHE = new ConcurrentHashMap<>();

    /**
     * Validates whether the specified record type is valid for the given SQL mode.
     *
     * @param recordType the record type to validate.
     * @param sqlMode    the SQL mode to validate against.
     * @throws SqlTemplateException if the record type is invalid for the given SQL mode.
     */
    @SuppressWarnings("unchecked")
    static void validateRecordType(@Nonnull Class<? extends Record> recordType, @Nonnull SqlMode sqlMode)
            throws SqlTemplateException {
        String message = VALIDATE_RECORD_TYPE_CACHE.computeIfAbsent(new TypeValidationKey(sqlMode, recordType), _ -> {
            // Note that this result can be cached as we're inspecting types.
            var pkComponents = getPkComponents(recordType).toList();
            if (pkComponents.isEmpty()) {
                if (sqlMode != SELECT && sqlMode != UNDEFINED) {
                    return STR."No primary key found for record \{recordType.getSimpleName()}.";
                }
            }
            for (var pkComponent : pkComponents) {
                if (Ref.class.isAssignableFrom(pkComponent.getType())) {
                    return STR."Primary key must not be a Ref: \{recordType.getSimpleName()}.";
                }
            }
            if (pkComponents.size() > 1) {
                return STR."Multiple primary keys found for record \{recordType.getSimpleName()}.";
            }
            for (var fkComponent : getFkComponents(recordType).toList()) {
                if (fkComponent.getType().isRecord()) {
                    if (getPkComponents((Class<? extends Record>) fkComponent.getType()).anyMatch(pk -> pk.getType().isRecord())) {
                        return STR."Foreign key must not specify a compound primary key: \{fkComponent.getType().getSimpleName()} \{fkComponent.getName()}.";
                    }
                    if (REFLECTION.isAnnotationPresent(fkComponent, Inline.class)) {
                        return STR."Foreign key must not be inlined: \{fkComponent.getType().getSimpleName()} \{fkComponent.getName()}.";
                    }
                } else if (!Ref.class.isAssignableFrom(fkComponent.getType())) {
                    return STR."Foreign key must be a record: \{fkComponent.getType().getSimpleName()} \{fkComponent.getName()}.";
                }
            }
            for (var component : recordType.getRecordComponents()) {
                if (getORMConverter(component).isPresent()) {
                    for (var annotation : List.of(PK.class, FK.class, Inline.class)) {
                        if (REFLECTION.isAnnotationPresent(component, annotation)) {
                            return STR."Converted component must not be @\{annotation.getSimpleName()}: \{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}.";
                        }
                    }
                } else if (component.getType().isRecord()) {
                    if (!REFLECTION.isAnnotationPresent(component, FK.class) && !REFLECTION.isAnnotationPresent(component, Inline.class)) {
                        // Accidentally inlining entities can have unexpected side effects.
                        if (Entity.class.isAssignableFrom(component.getType())) {
                            return STR."Entity must be marked as @FK or @Inline: \{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}.";
                        }
                        if (Projection.class.isAssignableFrom(component.getType())) {
                            return STR."Projection must be marked as @FK or @Inline: \{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}.";
                        }
                    }
                } else if (REFLECTION.isAnnotationPresent(component, Inline.class)) {
                    return STR."Inlined component must be a record: \{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}.";
                }
            }
            ProjectionQuery projectionQuery = REFLECTION.getAnnotation(recordType, ProjectionQuery.class);
            if (projectionQuery != null) {
                if (!Projection.class.isAssignableFrom(recordType)) {
                    return STR."ProjectionQuery must only be used on records implementing Projection: \{recordType.getSimpleName()}";
                }
                if (projectionQuery.value().isEmpty()) {
                    return STR."ProjectionQuery must specify a query: \{recordType.getSimpleName()}";
                }
            }
            return "";
        });
        if (!message.isEmpty()) {
            throw new SqlTemplateException(message);
        }
    }

    record GraphValidationKey(@Nonnull Class<? extends Record> recordType) {}
    private static final Map<GraphValidationKey, String> VALIDATE_RECORD_GRAPH_CACHE = new ConcurrentHashMap<>();

    /**
     * Validates that the provided record type does not contain cyclic dependencies. Specifically, it ensures that no
     * record type appears multiple times along any path from the specified {@code recordType}.
     *
     * <p>For example:
     * <ul>
     *     <li>Record A(B, C) and Record B(C) is valid.</li>
     *     <li>Record A(B, C) and Record B(A) is invalid due to a cycle A → B → A.</li>
     * </ul>
     *
     * @param recordType The root Record class to validate. Must not be null.
     * @throws SqlTemplateException if a cycle is detected in the Record graph.
     */
    static void validateRecordGraph(@Nonnull Class<? extends Record> recordType) throws SqlTemplateException {
        String message = VALIDATE_RECORD_GRAPH_CACHE.computeIfAbsent(new GraphValidationKey(recordType), _ -> {
            // Initialize an empty set to keep track of the current traversal path.
            Set<Class<? extends Record>> currentPath = new LinkedHashSet<>();
            // Start the recursive validation with the root record type.
            return validateRecordGraph(recordType, currentPath).orElse("");
        });
        if (!message.isEmpty()) {
            throw new SqlTemplateException(message);
        }
    }

    /**
     * Recursively validates the record graph to detect cycles.
     *
     * @param recordType  The current Record class being validated.
     * @param currentPath The set of Record classes in the current traversal path.
     * @return an empty optional if the record graph is valid, otherwise an optional containing an error message.
     */
    static Optional<String> validateRecordGraph(@Nonnull Class<? extends Record> recordType,
                                                 @Nonnull Set<Class<? extends Record>> currentPath) {
        // Check if the current record type is already in the path (cycle detected).
        if (currentPath.contains(recordType)) {
            return Optional.of(STR."Cyclic dependency detected: \{buildCyclePath(recordType, currentPath)}.");
        }
        currentPath.add(recordType);
        RecordComponent[] components = recordType.getRecordComponents();
        for (RecordComponent component : components) {
            Class<?> componentType = component.getType();
            if (Record.class.isAssignableFrom(componentType)) {
                @SuppressWarnings("unchecked")
                Class<? extends Record> componentRecordType = (Class<? extends Record>) componentType;
                // Recursively validate the component record type.
                var path = validateRecordGraph(componentRecordType, currentPath);
                if (path.isPresent()) {
                    return path;
                }
            }
        }
        // Remove the current record type from the path after processing.
        currentPath.remove(recordType);
        return empty();
    }

    /**
     * Builds a string representation of the cycle path for error messaging.
     *
     * @param currentType the record type where the cycle was detected.
     * @param path        the current traversal path leading up to the cycle.
     * @return a string describing the cycle path.
     */
    static String buildCyclePath(@Nonnull Class<? extends Record> currentType,
                                  @Nonnull Set<Class<? extends Record>> path) {
        StringBuilder cyclePath = new StringBuilder();
        for (Class<? extends Record> type : path) {
            cyclePath.append(type.getSimpleName()).append(" -> ");
        }
        cyclePath.append(currentType.getSimpleName());
        return cyclePath.toString();
    }

    /**
     * Validates the parameters of a SQL template.
     *
     * @param parameters the parameters to validate.
     * @throws SqlTemplateException if the parameters are invalid.
     */
    static void validateParameters(@Nonnull List<Parameter> parameters) throws SqlTemplateException {
        validatePositionalParameters(parameters);
        validateNamedParameters(parameters);
    }

    /**
     * Validates that positional parameters cover all the positions from 1 to n without gaps.
     *
     * @param parameters the parameters to validate.
     * @throws SqlTemplateException if a positional parameter is missing or if there are gaps in the positions.
     */
    static void validatePositionalParameters(@Nonnull List<Parameter> parameters) throws SqlTemplateException {
        SortedSet<Integer> positionSet = new TreeSet<>();
        for (Parameter param : parameters) {
            if (param instanceof PositionalParameter pp) {
                positionSet.add(pp.position());
            }
        }
        if (positionSet.isEmpty()) {
            return;
        }
        int minPos = positionSet.first();
        if (minPos != 1) {
            throw new SqlTemplateException(STR."Positional parameters must start at 1, but found \{minPos} instead.");
        }
        // Check for consecutive coverage from 1 through maxPos
        int expected = 1;
        for (int pos : positionSet) {
            if (pos != expected) {
                throw new SqlTemplateException(STR."Missing positional parameter at position \{expected}");
            }
            expected++;
        }
    }

    /**
     * Validates that named parameters are not being used multiple times with varying values.
     *
     * @param parameters the parameters to validate.
     * @throws SqlTemplateException if a named parameter is being used multiple times with varying values.
     */
    static void validateNamedParameters(List<Parameter> parameters) throws SqlTemplateException {
        var namedParameters = parameters.stream()
                .filter(SqlTemplate.NamedParameter.class::isInstance)
                .map(SqlTemplate.NamedParameter.class::cast)
                .collect(Collectors.<SqlTemplate.NamedParameter, String>groupingBy(SqlTemplate.NamedParameter::name));
        for (var entry : namedParameters.entrySet()) {
            var list = entry.getValue();
            if (list.size() > 1) {
                Object first = null;
                for (var value : list) {
                    var v = value.dbValue();
                    if (first == null) {
                        first = v;
                    } else {
                        if (!first.equals(v)) {
                            throw new SqlTemplateException(STR."Named parameter '\{value.name()}' is being used multiple times with varying values.");
                        }
                    }
                }
            }
        }
    }

    static void validateWhere(@Nonnull List<Element> elements) throws SqlTemplateException {
        if (elements.stream().filter(Elements.Where.class::isInstance).count() > 1) {
            throw new SqlTemplateException("Multiple Where elements found.");
        }
    }
}
