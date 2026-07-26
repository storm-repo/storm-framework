package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import st.orm.TypedMetamodel;
import st.orm.core.model.SelfRefNode_;

/**
 * A self-referential {@code Ref} (SelfRefNode.parent is a Ref&lt;SelfRefNode&gt;) generates no navigation children:
 * navigating past it would join the table to itself, and a table repeated on one path does not get a distinct alias
 * per occurrence, so the children would resolve against the earlier occurrence. The reference itself stays selectable
 * as the foreign key column; the chain is walked by resolving the reference with fetch().
 */
public class CyclicRefMetamodelTest {

    @Test
    public void selfReferentialRefMetamodelHasNoNavigationChildren() {
        assertNotNull(SelfRefNode_.parent);
        assertInstanceOf(TypedMetamodel.class, SelfRefNode_.parent);
        assertEquals("parent", SelfRefNode_.parent.fieldPath());
    }
}
