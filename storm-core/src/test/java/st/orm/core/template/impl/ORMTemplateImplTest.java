package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Type;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import st.orm.Entity;
import st.orm.Projection;
import st.orm.core.repository.EntityRepository;
import st.orm.core.repository.ProjectionRepository;

/**
 * Tests for {@link ORMTemplateImpl} utility methods.
 */
public class ORMTemplateImplTest {

    interface TestEntityRepository extends EntityRepository<TestEntity, Integer> {}
    record TestEntity(Integer id) implements Entity<Integer> {}

    interface SubRepository extends TestEntityRepository {}

    interface EmptyRepository {}

    @Test
    public void testFindGenericTypeDirectInterface() {
        Optional<Type> result = ORMTemplateImpl.findGenericType(
                TestEntityRepository.class, EntityRepository.class, 0);
        assertTrue(result.isPresent());
    }

    @Test
    public void testFindGenericTypeSubInterface() {
        Optional<Type> result = ORMTemplateImpl.findGenericType(
                SubRepository.class, EntityRepository.class, 0);
        assertTrue(result.isPresent());
    }

    @Test
    public void testFindGenericTypeNotFound() {
        Optional<Type> result = ORMTemplateImpl.findGenericType(
                EmptyRepository.class, EntityRepository.class, 0);
        assertFalse(result.isPresent());
    }

    @Test
    public void testFindGenericTypeInvalidIndex() {
        Optional<Type> result = ORMTemplateImpl.findGenericType(
                TestEntityRepository.class, EntityRepository.class, 99);
        assertFalse(result.isPresent());
    }

    record TestProjection(Integer id, String name) implements Projection<Integer> {}

    interface DirectEntityRepository extends EntityRepository<TestEntity, Integer> {}

    interface DirectProjectionRepository extends ProjectionRepository<TestProjection, Integer> {}

    interface MidLevelEntityRepository extends DirectEntityRepository {}

    interface DeepEntityRepository extends MidLevelEntityRepository {}

    interface UnrelatedRepository {}

    @Test
    public void testFindGenericTypeDirectEntityRepository() {
        Optional<Type> result = ORMTemplateImpl.findGenericType(
                DirectEntityRepository.class, EntityRepository.class, 0);
        assertTrue(result.isPresent());
        assertEquals(TestEntity.class, result.get());
    }

    @Test
    public void testFindGenericTypeDirectProjectionRepository() {
        Optional<Type> result = ORMTemplateImpl.findGenericType(
                DirectProjectionRepository.class, ProjectionRepository.class, 0);
        assertTrue(result.isPresent());
        assertEquals(TestProjection.class, result.get());
    }

    @Test
    public void testFindGenericTypeDeepHierarchy() {
        Optional<Type> result = ORMTemplateImpl.findGenericType(
                DeepEntityRepository.class, EntityRepository.class, 0);
        assertTrue(result.isPresent());
        assertEquals(TestEntity.class, result.get());
    }

    @Test
    public void testFindGenericTypeUnrelatedRepository() {
        Optional<Type> result = ORMTemplateImpl.findGenericType(
                UnrelatedRepository.class, EntityRepository.class, 0);
        assertFalse(result.isPresent());
    }

    @Test
    public void testFindGenericTypeIdIndex() {
        // Index 1 should return the ID type (Integer) for EntityRepository<TestEntity, Integer>.
        Optional<Type> result = ORMTemplateImpl.findGenericType(
                DirectEntityRepository.class, EntityRepository.class, 1);
        assertTrue(result.isPresent());
        assertEquals(Integer.class, result.get());
    }

    @Test
    public void testFindGenericTypeOutOfBoundsIndex() {
        Optional<Type> result = ORMTemplateImpl.findGenericType(
                DirectEntityRepository.class, EntityRepository.class, 5);
        assertFalse(result.isPresent());
    }

    @Test
    public void testFindGenericTypeNegativeIndex() {
        Optional<Type> result = ORMTemplateImpl.findGenericType(
                DirectEntityRepository.class, EntityRepository.class, -1);
        assertFalse(result.isPresent());
    }

    interface TopLevelInterface {}

    interface MidLevelInterface extends TopLevelInterface, DirectEntityRepository {}

    @Test
    public void testFindGenericTypeThroughIntermediateInterface() {
        Optional<Type> result = ORMTemplateImpl.findGenericType(
                MidLevelInterface.class, EntityRepository.class, 0);
        assertTrue(result.isPresent());
        assertEquals(TestEntity.class, result.get());
    }

    interface Marker<X> {}

    interface MarkedRepository extends Marker<String>, DirectEntityRepository {}

    @Test
    public void testFindGenericTypeSkipsUnrelatedParameterizedInterface() {
        // Only arguments of the target interface qualify; the Marker<String> listed first must not shadow them.
        Optional<Type> result = ORMTemplateImpl.findGenericType(
                MarkedRepository.class, EntityRepository.class, 0);
        assertTrue(result.isPresent());
        assertEquals(TestEntity.class, result.get());
    }

    interface SwappedRepository<A, B extends Entity<A>> extends EntityRepository<B, A> {}

    interface ConcreteSwappedRepository extends SwappedRepository<Integer, TestEntity> {}

    @Test
    public void testFindGenericTypeSubstitutesThroughGenericIntermediate() {
        // The intermediate interface reorders its parameters; resolution follows the substitution, not the
        // intermediate's own argument positions.
        Optional<Type> entityResult = ORMTemplateImpl.findGenericType(
                ConcreteSwappedRepository.class, EntityRepository.class, 0);
        assertTrue(entityResult.isPresent());
        assertEquals(TestEntity.class, entityResult.get());
        Optional<Type> idResult = ORMTemplateImpl.findGenericType(
                ConcreteSwappedRepository.class, EntityRepository.class, 1);
        assertTrue(idResult.isPresent());
        assertEquals(Integer.class, idResult.get());
    }
}
