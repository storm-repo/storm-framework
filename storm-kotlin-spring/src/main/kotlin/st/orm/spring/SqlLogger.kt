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
package st.orm.spring

import org.slf4j.event.Level
import org.slf4j.event.Level.INFO
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION

/**
 * Annotation for capturing and logging SQL statements and associated database table usage.
 *
 * Applying this annotation at either the function/method or class level will intercept execution via an aspect,
 * capturing, analyzing, and logging SQL statements and optionally the database tables involved.
 *
 * When applied at the class level, the annotation affects all methods within the class.
 *
 * Please note: If your repository interface has default-method implementations, you may need to compile with the Kotlin
 * compiler option `-Xjvm-default=all`. Without it, depending on the Kotlin version, Kotlin will emit those methods as
 * static helpers (not true JVM defaults), and the SQL-logger aspect can’t intercept them.
 *
 * @param inlineParameters If true, SQL parameters are inlined into the logged SQL statements for better readability.
 * @param level Defines the logging level used when outputting captured SQL. Defaults to INFO.
 * @param name Optional logger name. If empty, the default logger of the intercepted class is used.
 */
@Target(FUNCTION, CLASS)
@Retention(RUNTIME)
annotation class SqlLogger(
    val inlineParameters: Boolean = false,
    val level: Level = INFO,
    val name: String = "",
)
