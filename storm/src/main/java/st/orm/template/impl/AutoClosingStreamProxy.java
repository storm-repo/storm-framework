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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;
import java.util.stream.BaseStream;
import java.util.stream.Stream;

import static java.util.logging.Level.WARNING;

/**
 * A proxy that wraps a stream and automatically closes it after a terminal operation is performed.
 *
 * @param <T> the type of the stream elements.
 */
public final class AutoClosingStreamProxy<T> implements InvocationHandler {
    private static final boolean TRIPWIRE_PROPERTY = Boolean.getBoolean("st.orm.tripwire");

    public static ThreadLocal<Boolean> TRIPWIRE = ThreadLocal.withInitial(() -> TRIPWIRE_PROPERTY);

    private final Stream<T> stream;
    private volatile boolean isClosed;

    /**
     * Wraps the specified stream and returns a proxy that automatically closes the stream after a terminal operation is
     * performed.
     *
     * @param stream the stream to wrap.
     * @return a proxy that automatically closes the stream after a terminal operation is performed.
     * @param <T> the type of the stream elements.
     */
    @SuppressWarnings("unchecked")
    public static <T> Stream<T> wrap(Stream<T> stream) {
        return (Stream<T>) Proxy.newProxyInstance(
                stream.getClass().getClassLoader(),
                new Class<?>[]{Stream.class},
                new AutoClosingStreamProxy<>(stream));
    }

    private AutoClosingStreamProxy(Stream<T> stream) {
        this.stream = stream;
    }

    private boolean isTerminalOperation(@Nonnull Method method) {
        // We will not treat iterator and spliterator as a terminal operation.
        return !method.getName().equals("onClose") &&
                !method.getName().equals("iterator") &&
                !method.getName().equals("spliterator") &&
                !BaseStream.class.isAssignableFrom(method.getReturnType());
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            boolean close = method.getName().equals("close");
            if (close) {
                close();
                return null;
            }
            // If the stream is already closed, prevent the method from being called.
            if (isClosed) {
                throw new IllegalStateException("Stream is already closed");
            }
            Object result;
            if (method.getName().equals("iterator")) {
                if (TRIPWIRE.get()) {
                    Logger.getLogger(getClass().getName()).log(WARNING, "Tripwire: Iterator requested for stream is upgraded to the terminal list operation to force the stream to be closed.", new Exception());
                }
                result = stream.toList().iterator();
            } else if (method.getName().equals("spliterator")) {
                if (TRIPWIRE.get()) {
                    Logger.getLogger(getClass().getName()).log(WARNING, "Tripwire: Spliterator requested for stream is upgraded to the terminal list operation to force the stream to be closed.", new Exception());
                }
                result = stream.toList().spliterator();
            } else {
                result = method.invoke(stream, args);
            }
            if (isTerminalOperation(method)) {
                // If it's a terminal operation, close the stream.
                close();
            }
            return result;
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    private void close() {
        if (!isClosed) {
            stream.close();
            isClosed = true; // Mark the stream as closed.
        }
    }
}
