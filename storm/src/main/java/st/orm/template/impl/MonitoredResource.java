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
package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import st.orm.PersistenceException;

import java.lang.ref.Cleaner;
import java.lang.ref.Cleaner.Cleanable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitors whether streams are closed.
 */
final class MonitoredResource {

    private static final Logger LOGGER = Logger.getLogger("st.orm.resource");
    private static final Cleaner CLEANER = Cleaner.create();

    static <T extends AutoCloseable> T wrap(@Nonnull T resource) {
        return wrap(resource, new AtomicInteger());
    }
    private static <T extends AutoCloseable> T wrap(@Nonnull T resource, AtomicInteger openCount) {
        Exception createStackTrace = new Exception("Create stack trace");
        openCount.getAndIncrement();
        var cleanable = new AtomicReference<Cleanable>();
        //noinspection unchecked
        T proxy = (T) Proxy.newProxyInstance(resource.getClass().getClassLoader(),
                getInterfaces(resource.getClass()), (p, method, args) -> {
                    if (method.getName().equals("close")) {
                        // We can safely use plain mode here.
                        openCount.setPlain(-1);
                        cleanable.getPlain().clean();    // Invokes the cleanup method and deregisters the cleanable.
                        return null;
                    }
                    try {
                        Object result = method.invoke(resource, args);
                        if (result == resource) {
                            return p;   // Ensure monitored resource is returned.
                        }
                        if (result instanceof AutoCloseable c) {
                            return MonitoredResource.wrap(c, openCount);
                        }
                        return result;
                    } catch (InvocationTargetException e) {
                        throw e.getTargetException();
                    }
                });
        cleanable.setPlain(CLEANER.register(proxy, () -> {
            // This callback will be invoked when the Cleanable is explicitly cleaned, or when the Cleaner is
            // invoked by the garbage collector. It will be invoked at most once.
            int count = openCount.decrementAndGet();
            if (count == 0) {
                LOGGER.log(Level.WARNING, "Resource was not closed properly.", createStackTrace);
            }
            if (count <= 0) {
                try {
                    // Close the resource, also when this call is triggered from the Cleaner.
                    resource.close();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new PersistenceException(e);
                }
            }
        }));
        return proxy;
    }

    private static Class<?>[] getInterfaces(Class<?> clazz) {
        Set<Class<?>> allInterfaces = new HashSet<>();
        Class<?> current = clazz;
        while (current != null) {
            allInterfaces.addAll(Arrays.asList(current.getInterfaces()));
            current = current.getSuperclass();
        }
        return allInterfaces.toArray(new Class<?>[0]);
    }
}