package st.orm.spring.boot.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.transaction.TestTransaction;
import st.orm.spring.boot.test.domain.UnrelatedService;
import st.orm.spring.boot.test.domain.Visit;
import st.orm.spring.boot.test.domain.VisitRepository;
import st.orm.template.ORMTemplate;

/**
 * Verifies the {@code @DataStormTest} slice end to end: repositories are scanned, injectable and carry the
 * same AOP proxy as in the running application, regular components stay out, each test runs in a rollback
 * transaction, the Spring-integrated template behaviors (exception translation) are active, and database
 * state can be verified outside the ORM through {@code JdbcTemplate} and {@code JdbcClient}.
 */
@TestMethodOrder(OrderAnnotation.class)
@DataStormTest
class DataStormTestIntegrationTest {

    @Autowired
    private VisitRepository visitRepository;

    @Autowired
    private ORMTemplate orm;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @Order(1)
    void repositoriesAreScannedAndInjectable() {
        assertThat(visitRepository.count()).isEqualTo(3);
    }

    @Test
    @Order(2)
    void regularComponentsStayOutOfTheSlice() {
        assertThrows(NoSuchBeanDefinitionException.class,
                () -> applicationContext.getBean(UnrelatedService.class));
    }

    @Test
    @Order(3)
    void testsRunInATransaction() {
        assertThat(TestTransaction.isActive()).isTrue();
        visitRepository.insert(new Visit(null, "written inside the test transaction"));
        assertThat(visitRepository.count()).isEqualTo(4);
    }

    @Test
    @Order(4)
    void previousTestsWritesWereRolledBack() {
        assertThat(visitRepository.count()).isEqualTo(3);
    }

    @Test
    @Order(5)
    void exceptionTranslationIsActiveInTheSlice() {
        assertThrows(DuplicateKeyException.class,
                () -> orm.query("INSERT INTO visit (id, description) VALUES (1, 'duplicate')").executeUpdate());
    }

    @Test
    @Order(6)
    void repositoriesCarryTheProductionAopProxy() {
        assertThat(AopUtils.isAopProxy(visitRepository)).isTrue();
    }

    @Test
    @Order(7)
    void jdbcTemplateVerifiesDatabaseStateOutsideTheOrm() {
        visitRepository.insert(new Visit(null, "verified through plain JDBC"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM visit", Long.class)).isEqualTo(4);
    }

    @Test
    @Order(8)
    void jdbcClientVerifiesDatabaseStateOutsideTheOrm() {
        visitRepository.insert(new Visit(null, "verified through the JDBC client"));
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM visit").query(Long.class).single()).isEqualTo(4L);
    }
}
