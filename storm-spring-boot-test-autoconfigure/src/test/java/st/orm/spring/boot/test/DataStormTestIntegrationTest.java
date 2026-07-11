package st.orm.spring.boot.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.transaction.TestTransaction;
import st.orm.spring.boot.test.domain.UnrelatedService;
import st.orm.spring.boot.test.domain.Visit;
import st.orm.spring.boot.test.domain.VisitRepository;
import st.orm.template.ORMTemplate;

/**
 * Verifies the {@code @DataStormTest} slice end to end: repositories are scanned and injectable, regular
 * components stay out, each test runs in a rollback transaction, and the Spring-integrated template
 * behaviors (exception translation) are active.
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
}
