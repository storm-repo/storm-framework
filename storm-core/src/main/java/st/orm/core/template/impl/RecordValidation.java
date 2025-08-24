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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import st.orm.Element;
import st.orm.Entity;
import st.orm.FK;
import st.orm.GenerationStrategy;
import st.orm.Inline;
import st.orm.PK;
import st.orm.Projection;
import st.orm.ProjectionQuery;
import st.orm.Ref;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;
import st.orm.core.template.SqlTemplate;
import st.orm.core.template.SqlTemplate.Parameter;
import st.orm.core.template.SqlTemplate.PositionalParameter;
import st.orm.core.template.SqlTemplateException;

import java.lang.reflect.RecordComponent;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.Optional.empty;
import static st.orm.core.spi.Providers.getORMConverter;
import static st.orm.core.template.impl.RecordReflection.getPkComponent;
import static st.orm.core.template.impl.RecordReflection.getRefRecordType;

/**
 * Helper class for validating record types and named parameters.
 */
@SuppressWarnings("ALL")
final class RecordValidation {

    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    private RecordValidation() {
    }

    record TypeValidationKey(@Nonnull Class<? extends Record> type, boolean requirePrimaryKey) {}
    private static final Map<TypeValidationKey, String> VALIDATE_RECORD_TYPE_CACHE = new ConcurrentHashMap<>();

    /**
     * Checks if the provided type is a valid primary key type.
     *
     * <p><strong>Note:</strong> Floating point types are prohibited as primary keys.</p>
     *
     * @param type the type to check.
     * @return true if the type is a valid primary key type, false otherwise.
     */
    private static boolean isValidPrimaryKeyType(@Nonnull Class<?> type) {
        if (!(type == boolean.class || type == Boolean.class
                || type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == short.class || type == Short.class
                || type == String.class
                || type == UUID.class
                || type == BigInteger.class
                || type.isEnum())) {
            return false;
        }
        return true;
    }

    /**
     * Validates the provided record type for ORM mapping.
     *
     * @param type the record type to validate.
     * @param requirePrimaryKey true if a primary key is required, false otherwise.
     * @param duplicates a set to track duplicate record types to ensure no cycles.
     * @return an empty string if the record type is valid, otherwise an error message.
     */
    private static String validate(@Nonnull Class<? extends Record> type, boolean requirePrimaryKey, Set<Class<?>> duplicates) {
        if (!duplicates.add(type)) {
            return "";
        }
        boolean pkFound = false;
        for (var component : RecordReflection.getRecordComponents(type)) {
            if (getORMConverter(component).isPresent()) {
                for (var annotation : List.of(PK.class, FK.class, Inline.class)) {
                    if (REFLECTION.isAnnotationPresent(component, annotation)) {
                        return "Converted component must not be @%s: %s.%s.".formatted(annotation.getSimpleName(), type.getSimpleName(), component.getName());
                    }
                }
                continue;
            }
            PK pk = REFLECTION.getAnnotation(component, PK.class);
            FK fk = REFLECTION.getAnnotation(component, FK.class);
            Inline inline = REFLECTION.getAnnotation(component, Inline.class);
            if (pk != null) {
                if (pkFound) {
                    return "Multiple primary keys found: %s.".formatted(type.getSimpleName());
                }
                pkFound = true;
                if (fk != null && pk.generation() != GenerationStrategy.NONE) {
                    return "Foreign key must not be an auto-generated primary key: %s.%s.".formatted(type.getSimpleName(), component.getName());
                }
                if (component.getType().isRecord()) {
                    if (fk == null) {
                        for (var nestedComponent : RecordReflection.getRecordComponents(component.getType())) {
                            if (!isValidPrimaryKeyType(nestedComponent.getType())) {
                                return "Invalid primary key type %s.%s.%s.".formatted(type.getSimpleName(), component.getName(), nestedComponent.getName());
                            }
                        }
                    }
                } else if (!isValidPrimaryKeyType(component.getType())) {
                    return "Invalid primary key type: %s.%s.".formatted(type.getSimpleName(), component.getName());
                }
            }
            if (fk != null) {
                if (inline != null) {
                    return "Foreign key must not be inlined: %s.%s.".formatted(type.getSimpleName(), component.getName());
                }
                Class<? extends Record> fkType;
                if (component.getType().isRecord()) {
                    fkType = (Class<? extends Record>) component.getType();
                } else if (Ref.class.isAssignableFrom(component.getType())) {
                    try {
                        fkType = getRefRecordType(component);
                    } catch (SqlTemplateException e) {
                        return e.getMessage();
                    }
                } else {
                    return "Foreign key must either be a record or a Ref: %s.%s.".formatted(type.getSimpleName(), component.getName());
                }
                String message = validate(fkType, true, duplicates);
                if (!message.isEmpty()) {
                    return message + " Should %s.%s be marked as @FK?".formatted(component.getDeclaringRecord().getSimpleName(), component.getName());
                }
            }
            if (inline != null) {
                if (!component.getType().isRecord()) {
                    return "Inlined component must be a record: %s.%s.".formatted(type.getSimpleName(), component.getName());
                }
            }
            if (component.getType().isRecord()) {
                if (!REFLECTION.isAnnotationPresent(component, FK.class)) {
                    if (Entity.class.isAssignableFrom(component.getType())) {
                        return "Entity component of must be marked as @FK or @Inline: %s.%s.".formatted(type.getSimpleName(), component.getName());
                    }
                    if (Projection.class.isAssignableFrom(component.getType())) {
                        return "Projection component of must be marked as @FK or @Inline: %s.%s.".formatted(type.getSimpleName(), component.getName());
                    }
                    if (getPkComponent((Class<? extends Record>) component.getType()).isPresent()) {
                        return "Inlined component must not have a primary key: %s.%s.".formatted(type.getSimpleName(), component.getName());
                    }
                }
            }
        }
        if (requirePrimaryKey && !pkFound) {
            return "No primary key found for %s.".formatted(type.getSimpleName());
        }
        ProjectionQuery projectionQuery = REFLECTION.getAnnotation(type, ProjectionQuery.class);
        if (projectionQuery != null) {
            if (!Projection.class.isAssignableFrom(type)) {
                return "ProjectionQuery must only be used on records implementing Projection: %s".formatted(type.getSimpleName());
            }
            if (projectionQuery.value().isEmpty()) {
                return "ProjectionQuery must specify a query: %s".formatted(type.getSimpleName());
            }
        }
        return "";
    }

    /**
     * Validates whether the specified record type is valid for ORM mapping.
     *
     * <p>The results of this validation are cached.</p>
     *
     * @param recordType the record type to validate.
     * @param requirePrimaryKey true if a primary key is required, false otherwise.
     * @throws SqlTemplateException if the record type is invalid for ORM mapping.
     */
    static void validateRecordType(@Nonnull Class<? extends Record> recordType, boolean requirePrimaryKey)
            throws SqlTemplateException {
        String message = VALIDATE_RECORD_TYPE_CACHE.computeIfAbsent(new TypeValidationKey(recordType, requirePrimaryKey), ignore -> {
            // Note that this result can be cached as we're inspecting types.
            return validate(recordType, requirePrimaryKey, new HashSet<>());
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
        String message = VALIDATE_RECORD_GRAPH_CACHE.computeIfAbsent(new GraphValidationKey(recordType), ignore -> {
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
            return Optional.of("Cyclic dependency detected: %s.".formatted(buildCyclePath(recordType, currentPath)));
        }
        currentPath.add(recordType);
        for (RecordComponent component : RecordReflection.getRecordComponents(recordType)) {
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
            throw new SqlTemplateException("Positional parameters must start at 1, but found %d instead.".formatted(minPos));
        }
        // Check for consecutive coverage from 1 through maxPos
        int expected = 1;
        for (int pos : positionSet) {
            if (pos != expected) {
                throw new SqlTemplateException("Missing positional parameter at position %d".formatted(expected));
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
                            throw new SqlTemplateException("Named parameter '%s' is being used multiple times with varying values.".formatted(value.name()));
                        }
                    }
                }
            }
        }
    }

    /**
     * Validates that there is only one WHERE clause in the SQL template.
     *
     * @param elements the list of SQL template elements to validate.
     * @throws SqlTemplateException if multiple WHERE clauses are found.
     */
    static void validateWhere(@Nonnull List<Element> elements) throws SqlTemplateException {
        if (elements.stream().filter(Elements.Where.class::isInstance).count() > 1) {
            throw new SqlTemplateException("Multiple Where elements found.");
        }
    }
}
