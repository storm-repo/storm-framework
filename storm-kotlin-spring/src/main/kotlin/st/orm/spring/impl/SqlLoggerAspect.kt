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
package st.orm.spring.impl

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.springframework.stereotype.Component
import st.orm.core.template.SqlInterceptor.intercept
import st.orm.spring.SqlLogger
import java.util.function.Supplier

@Aspect
@Component
class SqlLoggerAspect {

    @Around("@within(sqlLogger)")
    fun wrapType(joinPoint: ProceedingJoinPoint, sqlLogger: SqlLogger): Any? {
        return wrap(joinPoint, sqlLogger)
    }

    @Around("@annotation(sqlLogger)")
    fun wrapMethod(joinPoint: ProceedingJoinPoint, sqlLogger: SqlLogger): Any? {
        return wrap(joinPoint, sqlLogger)
    }

    private fun wrap(joinPoint: ProceedingJoinPoint, sqlLogger: SqlLogger): Any? {
        val logger = sqlLogger.getLogger(joinPoint)
        if (!logger.isEnabledForLevel(sqlLogger.level)) {
            return joinPoint.proceed()
        }
        return intercept( { it.withInlineParameters(sqlLogger.inlineParameters) }, {
            val indentedSql = it.statement()
                .lines()
                .joinToString(separator = "\n") { "\t$it" }
            logger.atLevel(sqlLogger.level).log("[SQL] (${joinPoint.signature.toShortString()})\n${indentedSql}")
            it
        }, Supplier {
            joinPoint.proceed()
        })
    }

    private fun SqlLogger.getLogger(joinPoint: JoinPoint): Logger = if (this.name.isNotEmpty()) {
            getLogger(this.name)
        } else {
            getLogger(joinPoint.signature.declaringTypeName)
        }
}