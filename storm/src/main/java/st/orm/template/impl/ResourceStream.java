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
import jakarta.annotation.Nullable;

import java.lang.ref.Cleaner;
import java.lang.ref.Cleaner.Cleanable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.BaseStream;
import java.util.stream.Stream;

import static java.lang.Long.toHexString;
import static java.lang.System.identityHashCode;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Stream.generate;

/**
 * Represents a stream that is backed by an open resource.
 *
 * The resource stream automatically closes the underlying stream after a terminal operation is performed.
 *
 * @param <T> the type of the stream elements.
 */
public final class ResourceStream<T> implements InvocationHandler {
    private static final Logger LOGGER = Logger.getLogger(ResourceStream.class.getName());
    private static final Cleaner CLEANER = Cleaner.create();

    private final Stream<T> stream;
    private final State state;
    private final boolean autoClose;
    private final Cleanable cleanable;

    @SuppressWarnings("FieldCanBeLocal")    // Parent to maintain strong reference.
    private final ResourceStream<?> parent;

    /**
     * Returns a stream that lazily creates the stream when it is needed and automatically closes it after a terminal
     * operation is performed.
     *
     * @param streamSupplier the supplier of the stream to wrap.
     * @param <T> the type of the stream elements.
     * @return a lazily created stream that automatically closes the stream after a terminal operation is performed.
     */
    public static <T> Stream<T> lazy(@Nonnull Supplier<Stream<T>> streamSupplier) {
        boolean autoClose = Tripwire.isAutoClose();
        var supplier = new AtomicReference<>(streamSupplier);
        var open = new AtomicBoolean();
        var stream = generate(() -> {
            open.set(true);
            return supplier.getAndSet(null);
        })
                .takeWhile(Objects::nonNull)
                .flatMap(Supplier::get);
        return wrap(stream, new State(stream, new Exception("Stream creation stack trace"), open), autoClose, null);
    }

    /**
     * Wraps the specified stream and returns a proxy that automatically closes the stream after a terminal operation is
     * performed.
     *
     * @param stream the stream to wrap.
     * @param <T>    the type of the stream elements.
     * @return a proxy that automatically closes the stream after a terminal operation is performed.
     */
    public static <T> Stream<T> wrap(@Nonnull Stream<T> stream) {
        boolean autoClose = Tripwire.isAutoClose();
        return wrap(stream, new State(stream, new Exception("Stream creation stack trace")), autoClose, null);
    }

    @SuppressWarnings("unchecked")
    private static <T> Stream<T> wrap(@Nonnull Stream<T> stream, @Nonnull State state, boolean autoClose, @Nullable ResourceStream<?> parent) {
        return (Stream<T>) Proxy.newProxyInstance(
                stream.getClass().getClassLoader(),
                new Class<?>[]{Stream.class},
                new ResourceStream<>(stream, state, autoClose, parent));
    }

    private ResourceStream(@Nonnull Stream<T> stream, @Nonnull State state, boolean autoClose, @Nullable ResourceStream<?> parent) {
        this.stream = requireNonNull(stream, "stream");
        this.state = requireNonNull(state, "state");
        this.autoClose = autoClose;
        this.parent = parent;
        if (parent == null) {
            this.cleanable = CLEANER.register(this, state);
        } else {
            this.cleanable = null;
        }
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
        boolean close = method.getName().equals("close");
        try {
            if (method.getName().equals("toString")) {
                return STR."ResourceStream@\{toHexString(identityHashCode(proxy))} wrapping \{stream.toString()}";
            }
            if (close) {
                return null;
            }
            // If the stream is already closed, prevent the method from being called.
            if (state.isClosed()) {
                throw new IllegalStateException("Stream is already closed.");
            }
            Object result;
            if (!autoClose && method.getName().equals("iterator")) {
                Tripwire.iterator();
                result = stream.toList().iterator();
                close = true;
            } else if (!autoClose && method.getName().equals("spliterator")) {
                Tripwire.spliterator();
                result = stream.toList().spliterator();
                close = true;
            } else {
                result = method.invoke(stream, args);
                if (!autoClose) {
                    close = isTerminalOperation(method);    // If it's a terminal operation, close the stream.
                }
            }
            if (result == stream) {
                result = proxy;
            } else if (result instanceof Stream<?> s) {
                result = wrap(s, state, autoClose, this);
            }
            return result;
        } catch (InvocationTargetException e) {
            close = !autoClose;
            throw e.getTargetException();
        } finally {
            if (close) {
                close();
            }
        }
    }

    private void close() {
        if (cleanable != null) {
            state.close(false);
            cleanable.clean();
        } else {
            state.close(false);
        }
    }

    private static class State implements Runnable {
        private final Stream<?> stream;
        private final Exception creationException;
        private final AtomicBoolean open;
        private final AtomicBoolean closed;

        State(Stream<?> stream, Exception creationException) {
            this(stream, creationException, new AtomicBoolean());
        }

        State(Stream<?> stream, Exception creationException, AtomicBoolean open) {
            this.stream = stream;
            this.creationException = creationException;
            this.open = open;
            this.closed = new AtomicBoolean();
        }

        boolean isClosed() {
            return closed.get();
        }

        void close(boolean cleaner) {
            if (open.get() && closed.compareAndSet(false, true)) {
                if (cleaner) {
                    LOGGER.log(WARNING, "Stream was not properly closed. A terminal operation or an explicit close must be called on the stream. Stream creation stack trace:", creationException);
                }
                stream.close();
            }
        }

        @Override
        public void run() {
            close(true);
        }
    }
}
