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
package st.orm.core.spi;

import jakarta.annotation.Nonnull;
import st.orm.PersistenceException;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Scans the classpath for classes assignable to a given type.
 *
 * @since 1.7
 */
public final class ClasspathScanner {

    private static final Map<Class<?>, List<Class<?>>> CACHE = new ConcurrentHashMap<>();

    private ClasspathScanner() {}

    /**
     * Returns all non-abstract classes assignable to the given type.
     *
     * This scans the entire classpath (once) and caches the result.
     */
    public static <T> List<Class<? extends T>> getSubTypesOf(@Nonnull Class<T> parentType) {
        //noinspection unchecked
        return (List<Class<? extends T>>) (Object) CACHE.computeIfAbsent(parentType, ClasspathScanner::scanSubTypes);
    }

    private static List<Class<?>> scanSubTypes(@Nonnull Class<?> parentType) {
        Set<Class<?>> result = new LinkedHashSet<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = ClasspathScanner.class.getClassLoader();
        }
        try {
            Enumeration<URL> resources = classLoader.getResources("");
            while (resources.hasMoreElements()) {
                URL root = resources.nextElement();
                collectFrom(root, classLoader, parentType, result);
            }
        } catch (IOException e) {
            throw new PersistenceException(e);
        }
        return List.copyOf(result);
    }

    private static void collectFrom(URL root,
                                    ClassLoader cl,
                                    Class<?> parentType,
                                    Set<Class<?>> result) {

        try {
            URLConnection connection = root.openConnection();
            if (connection instanceof JarURLConnection jarConn) {
                try (JarFile jar = jarConn.getJarFile()) {
                    scanJar(jar, cl, parentType, result);
                }
                return;
            }
            Path rootPath = Path.of(root.toURI());
            if (Files.isDirectory(rootPath)) {
                scanDirectory(rootPath, cl, parentType, result);
            }
        } catch (Exception e) {
            // Ignore broken entries. This matches how most scanners behave.
        }
    }

    private static void scanDirectory(@Nonnull Path rootPath,
                                      @Nonnull ClassLoader classLoader,
                                      @Nonnull Class<?> parentType,
                                      @Nonnull Set<Class<?>> result) throws IOException {

        int rootLen = rootPath.toString().length() + 1;
        try (var stream = Files.walk(rootPath)) {
            stream.filter(path -> path.toString().endsWith(".class"))
                    .forEach(path -> {
                        String className = toClassName(path, rootLen);
                        loadIfSubclass(className, classLoader, parentType, result);
                    });

        }
    }

    private static void scanJar(@Nonnull JarFile jar,
                                @Nonnull ClassLoader classLoader,
                                @Nonnull Class<?> parentType,
                                @Nonnull Set<Class<?>> result) {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!name.endsWith(".class")) {
                continue;
            }
            String className = name.replace('/', '.')
                    .substring(0, name.length() - 6);
            loadIfSubclass(className, classLoader, parentType, result);
        }
    }

    private static String toClassName(Path cls, int rootLen) {
        String full = cls.toString().substring(rootLen);
        return full
                .replace(FileSystems.getDefault().getSeparator(), ".")
                .replaceAll("\\.class$", "");
    }

    private static void loadIfSubclass(String className,
                                       ClassLoader cl,
                                       Class<?> parentType,
                                       Set<Class<?>> result) {

        try {
            Class<?> cls = Class.forName(className, false, cl);
            if (cls.isInterface() || java.lang.reflect.Modifier.isAbstract(cls.getModifiers())) {
                return;
            }
            if (parentType.isAssignableFrom(cls) && cls != parentType) {
                result.add(cls);
            }
        } catch (Throwable ignored) {
            // Swallow: class cannot be loaded or fails static init.
        }
    }
}