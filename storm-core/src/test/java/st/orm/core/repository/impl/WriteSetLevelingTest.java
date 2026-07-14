package st.orm.core.repository.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import st.orm.PersistenceException;
import st.orm.core.repository.impl.WriteSetImpl.Node;

/**
 * Unit tests for the dependency leveling of {@link WriteSetImpl}.
 *
 * <p>The cycle guard cannot be reached through the integration schema: unsaved record graphs are acyclic by
 * construction, and keyed cycles require mutually referencing rows, which the test schema does not model. The
 * leveling logic is therefore exercised directly.</p>
 */
public class WriteSetLevelingTest {

    private static Node node(Object entity) {
        return new Node(entity, true);
    }

    @Test
    public void testLevelsFollowLongestDependencyChain() {
        Node owner = node("owner");
        Node pet = node("pet");
        Node visit = node("visit");
        pet.orderingDependencies.add(owner);
        visit.orderingDependencies.add(pet);
        visit.orderingDependencies.add(owner);
        IdentityHashMap<Object, Node> nodes = new IdentityHashMap<>();
        for (Node current : List.of(owner, pet, visit)) {
            nodes.put(current.entity, current);
        }
        WriteSetImpl.assignLevels(nodes, List.of(visit, pet, owner));
        assertEquals(0, owner.level);
        assertEquals(1, pet.level);
        assertEquals(2, visit.level);
    }

    @Test
    public void testCycleFailsWithDescriptiveError() {
        Node first = node("first");
        Node second = node("second");
        first.orderingDependencies.add(second);
        second.orderingDependencies.add(first);
        IdentityHashMap<Object, Node> nodes = new IdentityHashMap<>();
        nodes.put(first.entity, first);
        nodes.put(second.entity, second);
        var exception = assertThrows(PersistenceException.class,
                () -> WriteSetImpl.assignLevels(nodes, List.of(first, second)));
        assertTrue(exception.getMessage().contains("cycle"), exception.getMessage());
    }
}
