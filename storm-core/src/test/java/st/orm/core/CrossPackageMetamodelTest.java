package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import st.orm.Metamodel;
import st.orm.Navigable;
import st.orm.core.model.CrossPackageHolder_;

/**
 * A record declared outside the package of the entity that embeds it is referred to by its qualified name in the
 * generated metamodels. Navigating into such a record, and past a reference into one, therefore exercises how the
 * generated class names are qualified.
 */
public class CrossPackageMetamodelTest {

    @Test
    public void navigatesIntoAnInlineRecordFromAnotherPackage() {
        assertEquals("details.label", CrossPackageHolder_.details.label.fieldPath());
        assertEquals("details.score", CrossPackageHolder_.details.score.fieldPath());
    }

    @Test
    public void navigatesPastAReferenceIntoAnInlineRecordFromAnotherPackage() {
        // Holding the node as a Navigable is the contract; what remains to check is that it is not a value metamodel.
        Navigable<?, String> label = CrossPackageHolder_.owner.details.label;
        assertEquals("owner.details.label", label.fieldPath());
        assertFalse(label instanceof Metamodel);
    }
}
