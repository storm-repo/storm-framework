package st.orm.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.core.model.Owner;
import st.orm.core.model.OwnerView;
import st.orm.core.model.VetView;
import st.orm.core.model.VisitView;
import st.orm.core.model.VisitView_;
import st.orm.core.template.ORMTemplate;

import javax.sql.DataSource;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static st.orm.Operator.EQUALS;
import static st.orm.Operator.GREATER_THAN;
import static st.orm.Operator.GREATER_THAN_OR_EQUAL;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class ProjectionPreparedStatementIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testSelect() {
        // data.sql inserts exactly 10 owners (ids 1-10).
        assertEquals(10, ORMTemplate.of(dataSource).projection(OwnerView.class).selectCount().getSingleResult());
    }

    @Test
    public void testCount() {
        // data.sql inserts exactly 10 owners (ids 1-10).
        assertEquals(10, ORMTemplate.of(dataSource).projection(OwnerView.class).count());
    }

    @Test
    public void testResultCount() {
        // data.sql inserts exactly 10 owners (ids 1-10).
        assertEquals(10, ORMTemplate.of(dataSource).projection(OwnerView.class).select().getResultCount());
    }

    @Test
    public void testSelectByPk() {
        // Owner id=1 exists in data.sql; getById should return the projection with matching id.
        assertEquals(1, ORMTemplate.of(dataSource).projection(OwnerView.class).getById(1).id());
    }

    @Test
    public void testSelectByFkNested() {
        // Owner 1 (Betty Davis) has 1 pet (Leo, id=1). Leo has 2 visits (ids 4, 8).
        // Filtering visits by pet.owner = owner 1 should return those 2 visits.
        assertEquals(2, ORMTemplate.of(dataSource).projection(VisitView.class).select().where(VisitView_.pet.owner, Owner.builder().id(1).build()).getResultCount());
    }

    @Test
    public void testSelectByColumn() {
        // Only visit id=1 has visit_date = 2023-01-01 (the earliest date in data.sql).
        assertEquals(1, ORMTemplate.of(dataSource).projection(VisitView.class).select().where(VisitView_.visitDate, EQUALS, LocalDate.of(2023, 1, 1)).getResultCount());
    }

    @Test
    public void testSelectByColumnGreaterThan() {
        // 14 visits total. Visit dates range from 2023-01-01 to 2023-01-14.
        // 13 visits have dates strictly after 2023-01-01.
        assertEquals(13, ORMTemplate.of(dataSource).projection(VisitView.class).select().where(VisitView_.visitDate, GREATER_THAN, LocalDate.of(2023, 1, 1)).getResultCount());
    }

    @Test
    public void testSelectByColumnGreaterThanOrEqual() {
        // All 14 visits have dates >= 2023-01-01 (the earliest visit date in data.sql).
        assertEquals(14, ORMTemplate.of(dataSource).projection(VisitView.class).select().where(VisitView_.visitDate, GREATER_THAN_OR_EQUAL, LocalDate.of(2023, 1, 1)).getResultCount());
    }

    @Test
    public void testProjectionQuery() {
        // data.sql inserts exactly 6 vets (ids 1-6).
        assertEquals(6, ORMTemplate.of(dataSource).projection(VetView.class).selectCount().getSingleResult());
    }

    @Test
    public void testProjectionQueryWithoutPk() {
        // data.sql inserts exactly 14 visits (ids 1-14).
        assertEquals(14, ORMTemplate.of(dataSource).projection(VisitView.class).select().getResultList().size());
    }
}