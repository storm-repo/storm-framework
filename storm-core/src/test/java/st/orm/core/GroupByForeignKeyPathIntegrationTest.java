package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.core.template.TemplateString.raw;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.PersistenceException;
import st.orm.core.model.Owner;
import st.orm.core.model.Pet;
import st.orm.core.model.Pet_;
import st.orm.core.model.Visit;
import st.orm.core.model.Visit_;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.SqlInterceptor;

/**
 * A path naming the key of a table reached through a foreign key resolves to the foreign key column on the
 * referencing table, which spares a join. In GROUP BY that column is the wrong one to name whenever the referenced
 * table also contributes columns to the SELECT list: the grouping determines exactly one row, but functional
 * dependency is resolved syntactically and per table, so every dialect that enforces the rule rejects the statement.
 *
 * <p>These tests pin which column each clause names. The dialect modules cover the statements against the databases
 * that enforce the rule.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@JdbcTest
public class GroupByForeignKeyPathIntegrationTest {

    @Autowired
    private DataSource dataSource;

    private record OwnerPetCount(Owner owner, int petCount) {}

    private record PetVisitCount(Pet pet, int visitCount) {}

    private String captureSql(Runnable action) {
        List<String> statements = new ArrayList<>();
        SqlInterceptor.intercept(sql -> {
            statements.add(sql.statement());
            return sql;
        }, action);
        assertFalse(statements.isEmpty(), "Expected a statement to be captured.");
        return statements.getFirst();
    }

    /**
     * The referenced table is joined because the SELECT projects its columns, so the grouping names its key.
     */
    @Test
    public void groupByKeyThroughForeignKeyNamesTheReferencedKeyWhenTheTableIsProjected() {
        var results = new ArrayList<OwnerPetCount>();
        String sql = captureSql(() -> results.addAll(ORMTemplate.of(dataSource)
                .selectFrom(Pet.class, OwnerPetCount.class, raw("\0, COUNT(*)", Owner.class))
                .groupBy(Pet_.owner.id)
                .getResultList()));
        String groupBy = sql.substring(sql.toUpperCase().indexOf("GROUP BY"));
        assertFalse(groupBy.contains("owner_id"),
                "GROUP BY must not name the foreign key column when the referenced table is projected: " + sql);
        assertTrue(groupBy.matches("(?i)GROUP BY \\w+\\.id\\b.*"),
                "GROUP BY must name the referenced table's key: " + sql);
        assertFalse(results.isEmpty());
    }

    /**
     * One owner has many pets, so a grouping on the owner fixes no pet. The statement has no answer on any database:
     * the strict products refuse it and the permissive ones return an arbitrary pet, so it is refused here instead.
     */
    @Test
    public void groupingByAnOwnerWhileSelectingPetsIsRejected() {
        record OwnerPets(Pet pet, int count) {}
        var exception = assertThrows(PersistenceException.class, () -> ORMTemplate.of(dataSource)
                .selectFrom(Pet.class, OwnerPets.class, raw("\0, COUNT(*)", Pet.class))
                .groupBy(Pet_.owner)
                .getResultList());
        String message = exception.getMessage() + String.valueOf(exception.getCause());
        assertTrue(message.contains("Grouping does not determine Pet"), message);
        assertTrue(message.contains("Pet_.id"), "The message must name the path to add: " + message);
        assertTrue(message.contains("Pet_.owner"), "The message must state what is grouped: " + message);
    }

    /**
     * The foreign key field and the key beyond it name the same relationship, so they resolve alike.
     */
    @Test
    public void groupByTheForeignKeyFieldResolvesLikeTheKeyBeyondIt() {
        var results = new ArrayList<OwnerPetCount>();
        String sql = captureSql(() -> results.addAll(ORMTemplate.of(dataSource)
                .selectFrom(Pet.class, OwnerPetCount.class, raw("\0, COUNT(*)", Owner.class))
                .groupBy(Pet_.owner)
                .getResultList()));
        String groupBy = sql.substring(sql.toUpperCase().indexOf("GROUP BY"));
        assertFalse(groupBy.contains("owner_id"),
                "groupBy(Pet_.owner) must resolve like groupBy(Pet_.owner.id): " + sql);
        assertTrue(groupBy.matches("(?i)GROUP BY \\w+\\.id\\b.*"),
                "GROUP BY must name the referenced table's key: " + sql);
        assertFalse(results.isEmpty());
    }

    /**
     * The same spelling keeps naming the foreign key column when nothing selects the referenced table.
     */
    @Test
    public void groupByTheForeignKeyFieldKeepsTheForeignKeyColumnWhenTheTableIsNotSelected() {
        record OwnerIdCount(@Nullable Integer ownerId, int petCount) {}
        var results = new ArrayList<OwnerIdCount>();
        String sql = captureSql(() -> results.addAll(ORMTemplate.of(dataSource)
                .selectFrom(Pet.class, OwnerIdCount.class, raw("\0, COUNT(*)", Pet_.owner))
                .groupBy(Pet_.owner)
                .getResultList()));
        String groupBy = sql.substring(sql.toUpperCase().indexOf("GROUP BY"));
        assertTrue(groupBy.contains("owner_id"), "GROUP BY should name the foreign key column: " + sql);
        assertFalse(results.isEmpty());
    }

    /**
     * Nothing projects the referenced table, so the foreign key column is named and no join is paid for.
     */
    @Test
    public void groupByKeyThroughForeignKeyNamesTheForeignKeyColumnWhenTheTableIsNotProjected() {
        // Pet.owner is nullable, so the projected foreign key column is too.
        record OwnerIdCount(@Nullable Integer ownerId, int petCount) {}
        var results = new ArrayList<OwnerIdCount>();
        String sql = captureSql(() -> results.addAll(ORMTemplate.of(dataSource)
                .selectFrom(Pet.class, OwnerIdCount.class, raw("\0, COUNT(*)", Pet_.owner.id))
                .groupBy(Pet_.owner.id)
                .getResultList()));
        String groupBy = sql.substring(sql.toUpperCase().indexOf("GROUP BY"));
        assertTrue(groupBy.contains("owner_id"),
                "GROUP BY should name the foreign key column when the referenced table is not projected: " + sql);
        assertFalse(results.isEmpty());
    }

    /**
     * A reference states a to-one relationship like an entity foreign key does, so grouping by one is the same
     * identity and has to name the referenced table's key when the select list carries that table.
     *
     * <p>The key beyond a reference is rewritten onto the reference itself as the metamodel is built, so the path
     * from the query root cannot express that key; the grouping has to reach it another way.</p>
     */
    @Test
    public void groupByAReferenceFieldNamesTheReferencedKeyWhenTheTableIsSelected() {
        var results = new ArrayList<PetVisitCount>();
        String sql = captureSql(() -> results.addAll(ORMTemplate.of(dataSource)
                .selectFrom(Visit.class, PetVisitCount.class, raw("\0, COUNT(*)", Pet.class))
                .groupBy(Visit_.pet)
                .getResultList()));
        String groupBy = sql.substring(sql.toUpperCase().indexOf("GROUP BY"));
        assertFalse(groupBy.contains("pet_id"),
                "GROUP BY must name the pet's key, not the reference column, when Pet is selected: " + sql);
        assertTrue(groupBy.matches("(?i)GROUP BY \\w+\\.id\\b.*"),
                "GROUP BY must name the referenced table's key: " + sql);
        assertEquals(8, results.size());
    }

    /**
     * Nothing selects the referenced table, so the reference keeps naming its own column and stays unresolved.
     */
    @Test
    public void groupByAReferenceFieldKeepsTheReferenceColumnWhenTheTableIsNotSelected() {
        record PetIdCount(@Nullable Integer petId, int visitCount) {}
        var results = new ArrayList<PetIdCount>();
        String sql = captureSql(() -> results.addAll(ORMTemplate.of(dataSource)
                .selectFrom(Visit.class, PetIdCount.class, raw("\0, COUNT(*)", Visit_.pet))
                .groupBy(Visit_.pet)
                .getResultList()));
        String groupBy = sql.substring(sql.toUpperCase().indexOf("GROUP BY"));
        assertTrue(groupBy.contains("pet_id"), "GROUP BY should name the reference column: " + sql);
        assertFalse(results.isEmpty());
    }

    /**
     * ORDER BY places no requirement on which of the two equal columns is named, so it keeps the foreign key column.
     */
    @Test
    public void orderByKeyThroughForeignKeyKeepsNamingTheForeignKeyColumn() {
        String sql = captureSql(() -> ORMTemplate.of(dataSource)
                .selectFrom(Pet.class)
                .orderBy(Pet_.owner.id)
                .getResultList());
        String orderBy = sql.substring(sql.toUpperCase().indexOf("ORDER BY"));
        assertTrue(orderBy.contains("owner_id"), "ORDER BY should keep naming the foreign key column: " + sql);
    }

    /**
     * Direction now travels with the clause rather than a separate flag, so descending ordering still renders DESC.
     */
    @Test
    public void orderByDescendingThroughForeignKeyStillRendersDesc() {
        String sql = captureSql(() -> ORMTemplate.of(dataSource)
                .selectFrom(Pet.class)
                .orderByDescending(Pet_.owner.id)
                .getResultList());
        String orderBy = sql.substring(sql.toUpperCase().indexOf("ORDER BY"));
        assertTrue(orderBy.contains("owner_id"), "ORDER BY should keep naming the foreign key column: " + sql);
        assertTrue(orderBy.toUpperCase().contains("DESC"), "Descending ordering must render DESC: " + sql);
    }

    /**
     * A reference is not joined unless a query element navigates beyond it, and grouping by the key alone does not,
     * so the foreign key column is named and the reference stays unresolved.
     */
    @Test
    public void groupByKeyThroughReferenceNamesTheForeignKeyColumn() {
        var results = new ArrayList<PetVisitCount>();
        String sql = captureSql(() -> results.addAll(ORMTemplate.of(dataSource)
                .selectFrom(Visit.class, PetVisitCount.class, raw("\0, COUNT(*)", Pet.class))
                .groupBy(Visit_.pet.id)
                .getResultList()));
        String groupBy = sql.substring(sql.toUpperCase().indexOf("GROUP BY"));
        assertTrue(groupBy.contains("pet_id") || groupBy.matches("(?i)GROUP BY \\w+\\.id\\b.*"),
                "Unexpected GROUP BY shape: " + sql);
        assertEquals(8, results.size());
    }
}
