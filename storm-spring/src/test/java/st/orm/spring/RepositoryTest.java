package st.orm.spring;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.jdbc.Sql;
import st.orm.spring.repository.OwnerRepository;
import st.orm.spring.repository.VisitRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.context.TestConstructor.AutowireMode.ALL;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@ContextConfiguration(classes = IntegrationConfig.class)
@Import(TestRepositoryBeanFactoryPostProcessor.class)
@TestConstructor(autowireMode = ALL)
@SpringBootTest
@Sql("/data.sql")
public class RepositoryTest {

    private final VisitRepository visitRepository;
    private final OwnerRepository ownerRepository;

    public RepositoryTest(VisitRepository visitRepository, @Autowired(required = false) OwnerRepository ownerRepository) {
        this.visitRepository = visitRepository;
        this.ownerRepository = ownerRepository;
    }

    @Test
    public void testVisitRepository() {
        var visits = visitRepository.findAll();
        assertEquals(14, visits.size());
    }

    @Test
    public void testOwnerRepository() {
        assertNull(ownerRepository);
    }
}
