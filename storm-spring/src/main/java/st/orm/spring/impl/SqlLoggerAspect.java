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
package st.orm.spring.impl;

import jakarta.annotation.Nonnull;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import st.orm.PersistenceException;
import st.orm.core.template.Sql;
import st.orm.core.template.SqlInterceptor;
import st.orm.core.template.SqlTemplate;
import st.orm.spring.SqlLogger;

import java.util.stream.Collectors;

@Aspect
@Component
public class SqlLoggerAspect {

    @Around("@within(sqlLogger)")
    public Object wrapType(ProceedingJoinPoint joinPoint, SqlLogger sqlLogger) throws Throwable {
        return wrap(joinPoint, sqlLogger);
    }

    @Around("@annotation(sqlLogger)")
    public Object wrapMethod(ProceedingJoinPoint joinPoint, SqlLogger sqlLogger) throws Throwable {
        return wrap(joinPoint, sqlLogger);
    }

    private Object wrap(ProceedingJoinPoint joinPoint, SqlLogger sqlLogger) throws Throwable {
        var logger = getLogger(sqlLogger, joinPoint);
        if (!logger.isEnabledForLevel(sqlLogger.level())) {
            return joinPoint.proceed();
        }

        return SqlInterceptor.interceptThrowing(
                (SqlTemplate template) -> template.withInlineParameters(sqlLogger.inlineParameters()),
                (Sql sql) -> {
                    // prefix every line with a tab, just like the Kotlin version
                    String indentedSql = sql.statement()
                            .lines()
                            .map(line -> "\t" + line)
                            .collect(Collectors.joining("\n"));
                    logger.atLevel(sqlLogger.level()).log("[SQL] (%s)\n%s"
                            .formatted(joinPoint.getSignature().toShortString(), indentedSql));
                    return sql;
                },
                () -> {
                    try {
                        return joinPoint.proceed();
                    } catch (Exception e) {
                        throw e;
                    } catch (Throwable e) {
                        throw new PersistenceException(e);
                    }
                }
        );
    }

    private Logger getLogger(@Nonnull SqlLogger logger, @Nonnull JoinPoint joinPoint) {
        if (!logger.name().isEmpty()) {
            return LoggerFactory.getLogger(logger.name());
        }
        return LoggerFactory.getLogger(joinPoint.getSignature().getDeclaringTypeName());
    }
}
