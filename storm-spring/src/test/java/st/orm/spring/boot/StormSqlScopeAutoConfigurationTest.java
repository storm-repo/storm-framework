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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import st.orm.core.template.ORMTemplate;

/**
 * Verifies that the SQL scope covers every way work enters the application: the servlet filter covers requests,
 * and the entry-point post-processor covers the boundaries a filter cannot see, such as scheduled tasks and
 * message listeners.
 */
public class StormSqlScopeAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(StormSqlScopeAutoConfiguration.class));

    /** An entry point of the application's own, standing in for a listener annotation Spring does not ship. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface CustomListener {
    }

    @Configuration(proxyBeanMethods = false)
    static class JobConfiguration {

        @Bean
        DataSource dataSource() {
            return DataSourceBuilder.create()
                    .url("jdbc:h2:mem:sql-scope-entry-points;DB_CLOSE_DELAY=-1")
                    .build();
        }

        @Bean
        ReportJob reportJob(DataSource dataSource) {
            return new ReportJob(ORMTemplate.of(dataSource));
        }

        @Bean
        PlainService plainService() {
            return new PlainService();
        }
    }

    static class ReportJob {
        private final ORMTemplate orm;

        ReportJob(ORMTemplate orm) {
            this.orm = orm;
        }

        @Scheduled(fixedDelay = Long.MAX_VALUE)
        public void nightly() {
            orm.query("SELECT 1").getSingleResult();
        }

        @CustomListener
        public void onMessage() {
            orm.query("SELECT 1").getSingleResult();
            orm.query("SELECT 2").getSingleResult();
        }

        public void plain() {
            orm.query("SELECT 1").getSingleResult();
        }
    }

    static class PlainService {
        public void doWork() {
        }
    }

    private void withScopeLogger(Consumer<List<ILoggingEvent>> test) {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("st.orm.sql.scope");
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        var level = logger.getLevel();
        logger.setLevel(Level.INFO);
        try {
            test.accept(appender.list);
        } finally {
            logger.setLevel(level);
            logger.detachAppender(appender);
        }
    }

    @Test
    public void testAScheduledMethodReportsAScopeNamedAfterIt() {
        contextRunner
                .withUserConfiguration(JobConfiguration.class)
                .withPropertyValues("storm.sql-scope.enabled=true")
                .run(context -> withScopeLogger(events -> {
                    var job = context.getBean(ReportJob.class);
                    assertTrue(AopUtils.isCglibProxy(job));
                    job.nightly();
                    assertEquals(1, events.size());
                    assertTrue(events.getFirst().getFormattedMessage().startsWith("SQL (ReportJob.nightly):"),
                            events.getFirst().getFormattedMessage());
                }));
    }

    @Test
    public void testAMethodWithoutAnEntryPointAnnotationIsNotWrapped() {
        contextRunner
                .withUserConfiguration(JobConfiguration.class)
                .withPropertyValues("storm.sql-scope.enabled=true")
                .run(context -> withScopeLogger(events -> {
                    context.getBean(ReportJob.class).plain();
                    assertTrue(events.isEmpty());
                    // A bean with no entry points at all is not proxied to begin with.
                    assertFalse(AopUtils.isAopProxy(context.getBean(PlainService.class)));
                }));
    }

    @Test
    public void testConfiguredEntryPointsReplaceTheDefaults() {
        contextRunner
                .withUserConfiguration(JobConfiguration.class)
                .withPropertyValues("storm.sql-scope.enabled=true",
                        "storm.sql-scope.entry-points=" + CustomListener.class.getName())
                .run(context -> withScopeLogger(events -> {
                    var job = context.getBean(ReportJob.class);
                    job.nightly();
                    assertTrue(events.isEmpty());
                    job.onMessage();
                    assertEquals(1, events.size());
                    assertTrue(events.getFirst().getFormattedMessage().startsWith("SQL (ReportJob.onMessage):"),
                            events.getFirst().getFormattedMessage());
                }));
    }

    @Test
    public void testAThresholdReportsOnlyTheInvocationsExceedingIt() {
        contextRunner
                .withUserConfiguration(JobConfiguration.class)
                .withPropertyValues("storm.sql-scope.enabled=true",
                        "storm.sql-scope.entry-points=" + CustomListener.class.getName(),
                        "storm.sql-scope.threshold.statements=2")
                .run(context -> withScopeLogger(events -> {
                    var job = context.getBean(ReportJob.class);
                    job.onMessage();
                    assertEquals(1, events.size());
                    assertEquals(Level.WARN, events.getFirst().getLevel());
                }));
    }

    @Test
    public void testAThresholdKeepsAnInvocationBelowItQuiet() {
        contextRunner
                .withUserConfiguration(JobConfiguration.class)
                .withPropertyValues("storm.sql-scope.enabled=true",
                        "storm.sql-scope.threshold.statements=2")
                .run(context -> withScopeLogger(events -> {
                    context.getBean(ReportJob.class).nightly();
                    assertTrue(events.isEmpty());
                }));
    }

    @Test
    public void testTheScopeBacksOffWithoutTheProperty() {
        contextRunner
                .withUserConfiguration(JobConfiguration.class)
                .run(context -> {
                    assertFalse(context.containsBean("stormSqlScopeEntryPointPostProcessor"));
                    assertFalse(AopUtils.isAopProxy(context.getBean(ReportJob.class)));
                });
    }

    @Test
    public void testTheFilterRequiresAServletWebApplication() {
        // The runner is not a web application, so only the entry-point post-processor registers.
        contextRunner
                .withUserConfiguration(JobConfiguration.class)
                .withPropertyValues("storm.sql-scope.enabled=true")
                .run(context -> {
                    assertTrue(context.containsBean("stormSqlScopeEntryPointPostProcessor"));
                    assertFalse(context.containsBean("stormSqlScopeFilter"));
                });
    }
}
