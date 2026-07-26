package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import st.orm.Navigable;
import st.orm.TypedMetamodel;
import st.orm.core.model.SelfRefNode_;

/**
 * A self-referential {@code Ref} (SelfRefNode.parent is a Ref&lt;SelfRefNode&gt;) gets the regular reference metamodel:
 * the reference itself is selectable as the foreign key column, and it exposes navigation children that address the
 * columns of the referenced row. Navigating past it joins the table to itself, with a distinct alias for every hop.
 */
public class CyclicRefMetamodelTest {

    @Test
    public void selfReferentialRefMetamodelIsValueExtractable() {
        assertNotNull(SelfRefNode_.parent);
        assertInstanceOf(TypedMetamodel.class, SelfRefNode_.parent);
        assertEquals("parent", SelfRefNode_.parent.fieldPath());
    }

    @Test
    public void selfReferentialRefMetamodelHasNavigationChildren() {
        assertNotNull(SelfRefNode_.parent.id);
        assertNotNull(SelfRefNode_.parent.name);
        assertInstanceOf(Navigable.class, SelfRefNode_.parent.id);
        assertInstanceOf(Navigable.class, SelfRefNode_.parent.name);
        assertEquals("parent.id", SelfRefNode_.parent.id.fieldPath());
        assertEquals("parent.name", SelfRefNode_.parent.name.fieldPath());
    }

    @Test
    public void selfReferentialRefMetamodelNavigatesAcrossMultipleHops() {
        assertEquals("parent.parent.id", SelfRefNode_.parent.parent.id.fieldPath());
        assertEquals("parent.parent.name", SelfRefNode_.parent.parent.name.fieldPath());
    }
}
