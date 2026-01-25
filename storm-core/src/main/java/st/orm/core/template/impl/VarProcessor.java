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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.impl.Elements.BindVar;

/**
 * A processor for a var element of a template.
 */
final class VarProcessor implements ElementProcessor<BindVar> {

    private static final Object CACHE_KEY = new Object();

    /**
     * Returns a key that represents the compiled shape of the given element.
     *
     * <p>The compilation key is used for caching compiled results. It must include all fields that can affect the
     * compilation output (SQL text, emitted fragments, placeholder shape, etc.). The key is compared using
     * value-based equality, so it should be immutable and implement stable {@code equals}/{@code hashCode}.</p>
     *
     * <p>If this method returns {@code null} for any element in a template, the compiled result is considered
     * non-cacheable and the template must be recompiled each time it is requested.</p>
     *
     * @param bindVar the element to compute a key for.
     * @return an immutable key for caching, or {@code null} if the element (or its compilation) cannot be cached.
     */
    @Override
    public Object getCompilationKey(@Nonnull BindVar bindVar) {
        // Always cache.
        return CACHE_KEY;
    }

    /**
     * Compiles the given element into an {@link CompiledElement}.
     *
     * <p>This method is responsible for producing the compile-time representation of the element. It must not perform
     * runtime binding. Any binding should be deferred to {@link #bind(BindVar, TemplateBinder, BindHint)}.</p>
     *
     * @param bindVar the element to compile.
     * @param compiler the active compiler context.
     * @return the compiled result for this element.
     * @throws SqlTemplateException if compilation fails.
     */
    @Override
    public CompiledElement compile(@Nonnull BindVar bindVar, @Nonnull TemplateCompiler compiler)
            throws SqlTemplateException {
        if (bindVar.bindVars() instanceof BindVarsImpl) {
            compiler.mapBindVars(1);
            return new CompiledElement("?");
        }
        throw new SqlTemplateException("Unsupported BindVars type.");
    }

    /**
     * Performs post-processing after compilation, typically binding runtime values for the element.
     *
     * <p>This method is called after the element has been compiled. Typical responsibilities include binding
     * parameters, registering bind variables, or applying runtime-only adjustments that must not affect the compiled
     * SQL shape.</p>
     *
     * @param bindVar the element that was compiled.
     * @param binder the binder used to bind runtime values.
     * @param bindHint the bind hint for the element, providing additional context for binding.
     */
    @Override
    public void bind(@Nonnull BindVar bindVar, @Nonnull TemplateBinder binder, @Nonnull BindHint bindHint) {
        if (bindVar.bindVars() instanceof BindVarsImpl vars) {
            var parameterFactory = binder.setBindVars(vars);
            vars.addParameterExtractor(record -> {
                parameterFactory.bind(bindVar.extractor().apply(record));
                return parameterFactory.getParameters();
            });
        }
    }
}
