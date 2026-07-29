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
package st.orm.spring.boot;

import io.micrometer.context.ContextRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import st.orm.core.template.impl.SqlInterceptorManager;

/**
 * Carries an open SQL log recording across the boundaries a request crosses.
 *
 * <p>A scope opened at the request is bound to the thread that opened it. A suspending controller runs its body on
 * another thread, and so does anything the application dispatches, which leaves the statements those threads issue
 * outside the scope. Registering the scope with Micrometer's {@link ContextRegistry} makes it travel the same way
 * the trace context does: captured where the work is handed over and restored on the thread that picks it up.</p>
 *
 * <p>What travels is an immutable snapshot, so restoring it on another thread hands that thread the operators the
 * scope was opened with and nothing more.</p>
 *
 * <p>This covers the hand-overs that context propagation instruments. A coroutine the application launches itself
 * inherits its parent's coroutine context, so a scope opened inside coroutine code with
 * {@code st.orm.template.sqlLog} covers its own children by construction; that is the boundary to use when the
 * work never passes through an instrumented hand-over.</p>
 *
 * @since 1.13
 */
@AutoConfiguration
@ConditionalOnClass(ContextRegistry.class)
public class StormContextPropagationAutoConfiguration implements InitializingBean {

    /** Key the scope travels under in a captured context snapshot. */
    static final String SCOPE_KEY = "st.orm.sql.log";

    /** The registry is global, so registration happens once per JVM however many contexts start. */
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    @Override
    public void afterPropertiesSet() {
        if (REGISTERED.compareAndSet(false, true)) {
            ContextRegistry.getInstance().registerThreadLocalAccessor(SCOPE_KEY, SqlInterceptorManager.holder());
        }
    }
}
