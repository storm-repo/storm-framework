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
package st.orm.template

/**
 * Marks machinery that Storm's modules share with each other and that is public only because they compile
 * separately. Kotlin's `internal` stops at the artifact boundary, so a declaration one Storm module provides for
 * another has to be public in the bytecode; this marker keeps the source boundary in place, failing compilation
 * wherever application code reaches for it.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This is machinery Storm's modules share with each other, not part of the public API. " +
        "It can change or disappear in any release.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class InternalStormApi
