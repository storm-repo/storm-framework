package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Type;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import st.orm.Entity;
import st.orm.core.repository.EntityRepository;

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
}
