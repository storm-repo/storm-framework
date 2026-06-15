package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.core.model.Owner;
import st.orm.core.model.Tenant;
import st.orm.core.template.ORMTemplate;

/**
 * Regression tests for SELECT-target alias resolution when the SELECT type differs from the
 * repository's root entity type.
 *
 * <p>{@link Tenant} creates a diamond join graph: {@code tenant -> owner -> address.city} and
 * {@code tenant -> city} both reach {@code City}. Selecting {@link Owner} from a Tenant
 * repository expands Owner's nested column tree (which includes {@code address.city}) whose
 * metamodels are rooted at Owner rather than at the repository root. Before the fix, alias
 * resolution looked up paths rooted at Owner against the alias map that stores Tenant-rooted
 * paths, fell through to a permissive null-path lookup, and failed with
 * "Multiple aliases found for: City".</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class SelectTargetAliasResolutionIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void selectJoinedEntityWithDiamondPath() {
        var tenants = ORMTemplate.of(dataSource).entity(Tenant.class);
        // Owner sits one level deep; City is reachable via two paths (tenant.owner.address.city
        // and tenant.city). Compilation must pick the alias on the Owner branch without falling
        // back to a null-path lookup.
        List<Owner> owners = assertDoesNotThrow(() -> tenants.select(Owner.class).getResultList());
        assertNotNull(owners);
        assertEquals(2, owners.size());
    }

    @Test
    public void selectJoinedEntityResolvesNestedColumns() {
        var tenants = ORMTemplate.of(dataSource).entity(Tenant.class);
        List<Owner> owners = tenants.select(Owner.class).getResultList();
        // Verify the nested city column on the Owner branch resolves correctly (rather than
        // collapsing onto the tenant.city alias).
        assertEquals(1, owners.get(0).address().city().id());
        assertEquals(2, owners.get(1).address().city().id());
    }
}
