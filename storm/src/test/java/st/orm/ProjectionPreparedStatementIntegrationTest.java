package st.orm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.model.Owner;
import st.orm.model.OwnerView;
import st.orm.model.VetView;
import st.orm.model.VisitView;
import st.orm.model.VisitView_;
import javax.sql.DataSource;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static st.orm.Templates.ORM;
import static st.orm.template.Operator.EQUALS;
import static st.orm.template.Operator.GREATER_THAN;
import static st.orm.template.Operator.GREATER_THAN_OR_EQUAL;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class ProjectionPreparedStatementIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testSelect() {
        assertEquals(10, ORM(dataSource).projection(OwnerView.class).selectCount().getSingleResult());
    }

    @Test
    public void testCount() {
        assertEquals(10, ORM(dataSource).projection(OwnerView.class).count());
    }

    @Test
    public void testResultCount() {
        assertEquals(10, ORM(dataSource).projection(OwnerView.class).select().getResultCount());
    }

    @Test
    public void testSelectByPk() {
        assertEquals(1, ORM(dataSource).projection(OwnerView.class).getById(1).id());
    }

    @Test
    public void testSelectByFkNested() {
        assertEquals(2, ORM(dataSource).projection(VisitView.class).select().where(VisitView_.pet.owner, Owner.builder().id(1).build()).getResultCount());
    }

    @Test
    public void testSelectByColumn() {
        assertEquals(1, ORM(dataSource).projection(VisitView.class).select().where(VisitView_.visitDate, EQUALS, LocalDate.of(2023, 1, 1)).getResultCount());
    }

    @Test
    public void testSelectByColumnGreaterThan() {
        assertEquals(13, ORM(dataSource).projection(VisitView.class).select().where(VisitView_.visitDate, GREATER_THAN, LocalDate.of(2023, 1, 1)).getResultCount());
    }

    @Test
    public void testSelectByColumnGreaterThanOrEqual() {
        assertEquals(14, ORM(dataSource).projection(VisitView.class).select().where(VisitView_.visitDate, GREATER_THAN_OR_EQUAL, LocalDate.of(2023, 1, 1)).getResultCount());
    }

    @Test
    public void testProjectionQuery() {
        assertEquals(6, ORM(dataSource).projection(VetView.class).selectCount().getSingleResult());
    }

    @Test
    public void testProjectionQueryWithoutPk() {
        assertEquals(14, ORM(dataSource).projection(VisitView.class).select().getResultList().size());
    }
}