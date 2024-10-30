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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitors whether streams are closed.
 */
final class MonitoredResource {

    private static final Logger LOGGER = Logger.getLogger(MonitoredResource.class.getName());
    private static final Cleaner CLEANER = Cleaner.create();

    static <T extends AutoCloseable> T wrap(@Nonnull T resource) {
        Exception createStackTrace = new Exception("Create stack trace");
        var closed = new AtomicBoolean();
        var cleanable = new AtomicReference<Cleanable>();
        //noinspection unchecked
        T proxy = (T) Proxy.newProxyInstance(resource.getClass().getClassLoader(),
                getInterfaces(resource.getClass()), (_, method, args) -> {
                    if (method.getName().equals("close")) {
                        // We can safely use plain mode here.
                        closed.setPlain(true);
                        cleanable.getPlain().clean();    // Invokes the cleanup method and deregisters the cleanable.
                        return null;
                    }
                    try {
                        return method.invoke(resource, args);
                    } catch (InvocationTargetException e) {
                        throw e.getTargetException();
                    }
                });
        cleanable.setPlain(CLEANER.register(proxy, () -> {
            // This callback will be invoked when the Cleanable is explicitly cleaned, or when the Cleaner is
            // invoked by the garbage collector. It will be invoked at most once.
            if (!closed.getPlain()) {
                LOGGER.log(Level.WARNING, "Stream was not closed properly.", createStackTrace);
            }
            try {
                // Close the resource, also when this call is triggered from the Cleaner.
                resource.close();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new PersistenceException(e);
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