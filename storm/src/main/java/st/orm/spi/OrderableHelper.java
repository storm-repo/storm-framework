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
import st.orm.spi.Orderable.After;
import st.orm.spi.Orderable.AfterAny;
import st.orm.spi.Orderable.Before;
import st.orm.spi.Orderable.BeforeAny;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * Helper class for sorting objects that implement the {@link Orderable} interface.
 */
final class OrderableHelper {

    /** Cache for storing the order of classes. */
    private static final Map<List<Class<?>>, List<Class<?>>> CLASS_ORDER_CACHE = new ConcurrentHashMap<>();

    /**
     * Sorts a stream of orderables.
     *
     * @param orderables Stream of orderables to be sorted.
     * @param <T>        Type of orderable.
     * @return Sorted stream of orderables.
     */
    static <T extends Orderable<?>> Stream<T> sort(@Nonnull Stream<T> orderables) {
        return sort(orderables, true);  // Always cache.
    }

    /**
     * Sorts a stream of orderables with an option to cache.
     *
     * @param orderables Stream of orderables to be sorted.
     * @param cache      Whether to cache the class order.
     * @param <T>        Type of orderable.
     * @return Sorted stream of orderables.
     */
    static <T extends Orderable<?>> Stream<T> sort(@Nonnull Stream<T> orderables, boolean cache) {
        return sort(orderables.collect(toList()), cache).stream();
    }

    /**
     * Sorts a list of orderables.
     *
     * @param orderables List of orderables to be sorted.
     * @param <T>        Type of orderable.
     * @return Sorted list of orderables.
     */
    static <T extends Orderable<?>> List<T> sort(@Nonnull List<T> orderables) {
        return sort(orderables, true);  // Always cache.
    }

    /**
     * Sorts a list of orderables with an option to cache.
     *
     * @param orderables List of orderables to be sorted.
     * @param cache      Whether to cache the class order.
     * @param <T>        Type of orderable.
     * @return Sorted list of orderables.
     */
    static <T extends Orderable<?>> List<T> sort(@Nonnull List<T> orderables, boolean cache) {
        List<Class<?>> classOrder = getClassOrder(orderables.stream()
                .map(Object::getClass)
                .collect(toList()), cache);
        return orderables.stream()
                .sorted(Comparator.comparingInt(o -> classOrder.indexOf(o.getClass())))
                .collect(toList());
    }

    /**
     * Retrieves the order of classes, with an option to cache.
     *
     * @param classes List of classes to determine the order.
     * @param cache   Whether to cache the class order.
     * @return Ordered list of classes.
     */
    private static List<Class<?>> getClassOrder(@Nonnull List<Class<?>> classes, boolean cache) {
        if (cache) {
            return CLASS_ORDER_CACHE.computeIfAbsent(classes, cls -> topologicalSort(buildClassDependencyGraph(cls)));
        }
        return topologicalSort(buildClassDependencyGraph(classes));
    }

    /**
     * Builds a dependency graph for the given classes.
     *
     * @param classes List of classes to build the dependency graph.
     * @return Dependency graph.
     */
    private static Map<Class<?>, Node> buildClassDependencyGraph(@Nonnull List<Class<?>> classes) {
        Map<Class<?>, Node> graph = new HashMap<>();
        for (Class<?> cls : classes) {
            graph.put(cls, new Node(cls));
        }
        for (Class<?> cls : classes) {
            Node currentNode = graph.get(cls);
            if (cls.isAnnotationPresent(Before.class)) {
                for (Class<?> beforeClass : cls.getAnnotation(Before.class).value()) {
                    if (classes.contains(beforeClass) && beforeClass != cls) {
                        graph.get(beforeClass).getDependencies().add(currentNode);
                    }
                }
            }
            if (cls.isAnnotationPresent(After.class)) {
                for (Class<?> afterClass : cls.getAnnotation(After.class).value()) {
                    if (classes.contains(afterClass) && afterClass != cls) {
                        currentNode.getDependencies().add(graph.get(afterClass));
                    }
                }
            }
        }
        return graph;
    }

    /**
     * Performs topological sort on the given graph.
     *
     * @param graph Dependency graph.
     * @return Ordered list of classes.
     */
    private static List<Class<?>> topologicalSort(@Nonnull Map<Class<?>, Node> graph) {
        List<Class<?>> result = new ArrayList<>();
        Set<Class<?>> visited = new HashSet<>();
        Set<Class<?>> visiting = new HashSet<>();
        Deque<Node> path = new LinkedList<>();
        for (Node node : graph.values()) {
            if (!visited.contains(node.getValue())) {
                visitNode(node, visited, visiting, result, path);
            }
        }
        // Handle BeforeAny and AfterAny constraints.
        List<Class<?>> beforeAnyList = graph.keySet().stream()
                .filter(cls -> cls.isAnnotationPresent(BeforeAny.class))
                .toList();
        List<Class<?>> afterAnyList = graph.keySet().stream()
                .filter(cls -> cls.isAnnotationPresent(AfterAny.class))
                .toList();
        result.removeAll(beforeAnyList);
        result.removeAll(afterAnyList);
        List<Class<?>> finalResult = new ArrayList<>();
        finalResult.addAll(beforeAnyList);
        finalResult.addAll(result);
        finalResult.addAll(afterAnyList);
        return finalResult;
    }

    /**
     * Checks if there are multiple distinct classes in the provided list that have the same simple name.
     *
     * @param classes List of classes to check.
     * @return True if there are different classes with the same simple name, otherwise false.
     */
    private static boolean hasDuplicateSimpleNames(Deque<Class<?>> classes) {
        Map<String, Long> nameCounts = classes.stream()
                .distinct()  // Remove exact duplicates.
                .collect(Collectors.groupingBy(Class::getSimpleName, counting()));
        return nameCounts.values().stream().anyMatch(count -> count > 1);
    }

    /**
     * Visits a node in the graph during topological sort.
     *
     * @param node      Node to visit.
     * @param visited   Set of visited nodes.
     * @param visiting  Set of nodes currently being visited.
     * @param result    Result list to append to.
     * @param path      Current path in the graph.
     */
    private static void visitNode(@Nonnull Node node,
                                  @Nonnull Set<Class<?>> visited,
                                  @Nonnull Set<Class<?>> visiting,
                                  @Nonnull List<Class<?>> result,
                                  @Nonnull Deque<Node> path) {
        if (visiting.contains(node.getValue())) {
            Deque<Class<?>> cycleList = new LinkedList<>();
            Iterator<Node> reversePath = new LinkedList<>(path).descendingIterator();
            while (reversePath.hasNext()) {
                Node current = reversePath.next();
                cycleList.addFirst(current.getValue());
                if (current.getValue() == node.getValue() && cycleList.size() > 1) {
                    break;
                }
            }
            cycleList.addFirst(node.getValue());  // Prepend the starting node to complete the cycle.
            boolean useFullyQualifiedClassNames = hasDuplicateSimpleNames(cycleList);
            String cycle = cycleList.stream()
                    .map(useFullyQualifiedClassNames ? Class::getName : Class::getSimpleName)
                    .collect(joining(" -> "));
            throw new IllegalStateException("Circular dependency detected: " + cycle + ".");
        }
        visiting.add(node.getValue());
        path.push(node);
        for (Node dependency : node.getDependencies()) {
            if (!visited.contains(dependency.getValue())) {
                visitNode(dependency, visited, visiting, result, path);
            }
        }
        visiting.remove(node.getValue());
        visited.add(node.getValue());
        result.add(node.getValue());
        path.pop();
    }

    /**
     * Represents a node in the dependency graph.
     */
    private static class Node {
        private final Class<?> value;
        private final List<Node> dependencies;

        /**
         * Constructs a new node with the given value.
         *
         * @param value Value of the node.
         */
        Node(@Nonnull Class<?> value) {
            this.value = value;
            this.dependencies = new ArrayList<>();
        }

        /**
         * Retrieves the value of the node.
         *
         * @return Value of the node.
         */
        Class<?> getValue() {
            return value;
        }

        /**
         * Retrieves the list of nodes that this node depends on.
         *
         * @return List of dependency nodes.
         */
        List<Node> getDependencies() {
            return dependencies;
        }
    }
}
