package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.PersistenceException;
import st.orm.core.model.City;
import st.orm.core.model.Owner;
import st.orm.core.model.Pet;
import st.orm.core.model.Pet_;
import st.orm.core.model.Visit;
import st.orm.core.model.Visit_;
import st.orm.core.template.ORMTemplate;

@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class TypedRootProbeTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void joinedQueryReferencesTheJoinedEntityWithoutAnyVariants() {
        var orm = ORMTemplate.of(dataSource);
        // A joined column named through plain where(), no whereAny needed.
        var pets = orm.entity(Pet.class).select()
                .innerJoin(Visit.class).on(Pet.class)
                .where(Visit_.pet.id, st.orm.Operator.EQUALS, 7)
                .getResultList();
        assertFalse(pets.isEmpty());
    }

    @Test
    public void typedRootRejectsATypeThisQueryIsNotRootedAt() {
        var orm = ORMTemplate.of(dataSource);
        var exception = assertThrows(PersistenceException.class, () -> orm.entity(Pet.class).select()
                .innerJoin(Visit.class).on(Pet.class)
                .typedRoot(City.class));
        assertTrue(exception.getMessage().contains("Root type mismatch"), exception.getMessage());
    }

    @Test
    public void typedRootRecoversGroupingAfterAJoin() {
        var orm = ORMTemplate.of(dataSource);
        var grouped = orm.entity(Pet.class).select()
                .innerJoin(Visit.class).on(Pet.class)
                .typedRoot(Pet.class)
                .getResultGroupedBy(Pet_.owner);
        assertFalse(grouped.isEmpty());
        for (Owner owner : grouped.keySet()) {
            assertFalse(grouped.get(owner).isEmpty());
        }
    }
}
