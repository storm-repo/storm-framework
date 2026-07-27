package st.orm.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.Operator.EQUALS;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.PersistenceException;
import st.orm.template.model.Owner;
import st.orm.template.model.Pet;
import st.orm.template.model.PetOwnerRef;
import st.orm.template.model.PetOwnerRef_;
import st.orm.template.model.Pet_;

/**
 * Verifies the Java 21 surface of resolving a reference as part of the query. {@link PetOwnerRef} maps the pet table
 * with the owner as a reference; {@link Pet} maps the same table with the owner as an entity, giving an entity-graph
 * baseline to compare a resolved reference against.
 */
@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@SpringBootTest
@Sql("/data.sql")
public class RefFetchTest {

    @Autowired
    private ORMTemplate orm;

    @Test
    public void testFetchLoadsTheReference() {
        List<PetOwnerRef> pets = orm.selectFrom(PetOwnerRef.class)
                .fetch(PetOwnerRef_.owner)
                .getResultList();
        assertFalse(pets.isEmpty());
        PetOwnerRef withOwner = pets.stream().filter(pet -> pet.owner() != null).findFirst().orElseThrow();
        assertTrue(withOwner.owner().isLoaded());
        assertNotNull(withOwner.owner().fetch());
    }

    @Test
    public void testResolvedReferenceMatchesEntityGraph() {
        List<Owner> viaEntity = orm.selectFrom(Pet.class)
                .where(Pet_.name, EQUALS, "Leo")
                .getResultList().stream().map(Pet::owner).toList();
        List<Owner> viaRef = orm.selectFrom(PetOwnerRef.class)
                .fetch(PetOwnerRef_.owner)
                .where(PetOwnerRef_.name, EQUALS, "Leo")
                .getResultList().stream().map(pet -> pet.owner().fetch()).toList();
        assertFalse(viaEntity.isEmpty());
        assertEquals(viaEntity, viaRef);
    }

    @Test
    public void testWithoutFetchTheReferenceStaysUnloaded() {
        List<PetOwnerRef> pets = orm.selectFrom(PetOwnerRef.class).getResultList();
        PetOwnerRef withOwner = pets.stream().filter(pet -> pet.owner() != null).findFirst().orElseThrow();
        assertFalse(withOwner.owner().isLoaded());
    }

    @Test
    public void testNullableReferenceYieldsNull() {
        List<PetOwnerRef> pets = orm.selectFrom(PetOwnerRef.class)
                .fetch(PetOwnerRef_.owner)
                .where(PetOwnerRef_.name, EQUALS, "Sly")
                .getResultList();
        assertEquals(1, pets.size());
        assertNull(pets.getFirst().owner());
    }

    @Test
    public void testFetchRejectsPathThatCrossesNoReference() {
        var exception = assertThrows(PersistenceException.class,
                () -> orm.selectFrom(PetOwnerRef.class).fetch(PetOwnerRef_.type));
        assertTrue(exception.getMessage().contains("crosses no reference"), exception.getMessage());
    }
}
