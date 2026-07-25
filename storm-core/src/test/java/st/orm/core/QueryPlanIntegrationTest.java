package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.core.template.SqlInterceptor.observe;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.PersistenceException;
import st.orm.core.model.Pet;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.QueryPlan;
import st.orm.core.template.Sql;
import st.orm.core.template.SqlInterceptor;
import st.orm.core.template.SqlTemplate.PositionalParameter;
import st.orm.core.template.TemplateString;
import st.orm.core.template.Templates;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class QueryPlanIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @DbTable("visit")
    public record PlanVisit(
            @PK Integer id,
            @Nonnull LocalDate visitDate,
            @Nullable String description,
            @Nonnull @FK Pet pet
    ) implements Entity<Integer> {}

    private ORMTemplate orm() {
        return ORMTemplate.of(dataSource);
    }

    private QueryPlan fullUpdatePlan(ORMTemplate orm) {
        var bindVars = orm.createBindVars();
        return orm.plan(TemplateString.raw("""
                UPDATE \0
                SET \0
                WHERE \0""", PlanVisit.class, Templates.set(bindVars), bindVars));
    }

    @Test
    void plan_bindsManyRecords_acrossExecutions() {
        var orm = orm();
        var repository = orm.entity(PlanVisit.class);
        var plan = fullUpdatePlan(orm);

        var first = repository.getById(1);
        assertEquals(1, plan.bind(new PlanVisit(first.id(), first.visitDate(), "plan-a", first.pet())).executeUpdate());
        assertEquals("plan-a", repository.getById(1).description());

        var second = repository.getById(2);
        assertEquals(1, plan.bind(new PlanVisit(second.id(), second.visitDate(), "plan-b", second.pet())).executeUpdate());
        assertEquals("plan-b", repository.getById(2).description());
    }

    @Test
    void plan_interceptorsObserveEveryBoundStatement() {
        var orm = orm();
        var repository = orm.entity(PlanVisit.class);
        var plan = fullUpdatePlan(orm);
        var first = repository.getById(1);
        var second = repository.getById(2);

        var observed = new ArrayList<Sql>();
        observe(observed::add, () -> {
            plan.bind(new PlanVisit(first.id(), first.visitDate(), "observed-a", first.pet())).executeUpdate();
            plan.bind(new PlanVisit(second.id(), second.visitDate(), "observed-b", second.pet())).executeUpdate();
        });
        assertEquals(2, observed.size());
        assertTrue(observed.getFirst().statement().startsWith("UPDATE"));
        assertTrue(observed.getFirst().parameters().stream()
                .anyMatch(parameter -> parameter instanceof PositionalParameter positional
                        && "observed-a".equals(positional.dbValue())));
        assertTrue(observed.getLast().parameters().stream()
                .anyMatch(parameter -> parameter instanceof PositionalParameter positional
                        && "observed-b".equals(positional.dbValue())));
    }

    @Test
    void plan_rejectsFixedParameterValues() {
        var orm = orm();
        var visit = orm.entity(PlanVisit.class).getById(1);
        var exception = assertThrows(PersistenceException.class, () -> orm.plan(TemplateString.raw("""
                UPDATE \0
                SET \0
                WHERE \0""", PlanVisit.class, Templates.set(visit), visit)));
        assertTrue(exception.getMessage().contains("fixed parameter values"));
    }

    @Test
    void constantPlan_executesRepeatedly() {
        var orm = orm();
        var plan = orm.selectFrom(PlanVisit.class).plan();
        var first = plan.query().getResultList(PlanVisit.class);
        var second = plan.query().getResultList(PlanVisit.class);
        assertFalse(first.isEmpty());
        assertEquals(first.size(), second.size());
    }

    @Test
    void constantPlan_andBoundPlan_rejectTheWrongExecutionStyle() {
        var orm = orm();
        var constant = orm.selectFrom(PlanVisit.class).plan();
        var visit = orm.entity(PlanVisit.class).getById(1);
        var bindOnConstant = assertThrows(PersistenceException.class, () -> constant.bind(visit));
        assertTrue(bindOnConstant.getMessage().contains("query()"));
        var bound = fullUpdatePlan(orm);
        var queryOnBound = assertThrows(PersistenceException.class, bound::query);
        assertTrue(queryOnBound.getMessage().contains("bind()"));
    }

    @Test
    void repositoryFindAllAndCount_useCachedConstantPlans() {
        var repository = orm().entity(PlanVisit.class);
        var count = repository.count();
        // The second calls bind against the plans cached by the first.
        assertEquals(count, repository.count());
        assertEquals(count, repository.findAll().size());
        assertEquals(count, repository.findAll().size());
    }

    @Test
    void plan_bindsById_acrossExecutions() {
        var orm = orm();
        var bindVars = orm.createBindVars();
        var plan = orm.plan(TemplateString.raw("""
                SELECT \0
                FROM \0
                WHERE \0""", PlanVisit.class, Templates.from(PlanVisit.class, true), bindVars));
        var first = plan.bindValue(1).getSingleResult(PlanVisit.class);
        assertEquals(1, first.id());
        var second = plan.bindValue(2).getSingleResult(PlanVisit.class);
        assertEquals(2, second.id());
    }

    @Test
    void bindValue_rejectsPlansThatAreNotSingleKeyBased() {
        var orm = orm();
        var boundByRecord = fullUpdatePlan(orm);
        var onUpdatePlan = assertThrows(PersistenceException.class, () -> boundByRecord.bindValue(1));
        assertTrue(onUpdatePlan.getMessage().contains("bind()"));
        var constant = orm.selectFrom(PlanVisit.class).plan();
        var onConstantPlan = assertThrows(PersistenceException.class, () -> constant.bindValue(1));
        assertTrue(onConstantPlan.getMessage().contains("query()"));
    }

    @Test
    void bindValue_rejectsPlansWithMultipleBindVariablesSegments() {
        var orm = orm();
        var bindVars = orm.createBindVars();
        var plan = orm.plan(TemplateString.raw("""
                SELECT \0
                FROM \0
                WHERE \0 AND \0""", PlanVisit.class, Templates.from(PlanVisit.class, true),
                Templates.where(bindVars), Templates.where(bindVars)));
        // A single value can feed exactly one segment; the same value bound through every segment would be silently
        // wrong for distinct keys, so the plan insists on a record.
        var exception = assertThrows(PersistenceException.class, () -> plan.bindValue(1));
        assertTrue(exception.getMessage().contains("single key-based WHERE"));
    }

    @Test
    void plan_bindIsThreadSafe() {
        var orm = orm();
        var repository = orm.entity(PlanVisit.class);
        var plan = fullUpdatePlan(orm);
        var visit = repository.getById(1);
        var updated = new PlanVisit(visit.id(), visit.visitDate(), "parallel", visit.pet());
        // Extraction runs concurrently against the shared plan; execution is intentionally left out since the
        // transactional test connection is single-threaded.
        java.util.stream.IntStream.range(0, 200).parallel().forEach(ignore -> {
            var query = plan.bind(updated);
            assertTrue(query != null);
        });
        assertEquals(1, plan.bind(updated).executeUpdate());
        assertEquals("parallel", repository.getById(1).description());
    }

    @Test
    void repositoryByRefLookups_useCachedByIdPlan() {
        var repository = orm().entity(PlanVisit.class);
        assertEquals(1, repository.getByRef(repository.ref(1)).id());
        // The second lookup binds against the plan cached by the first.
        assertTrue(repository.findByRef(repository.ref(2)).isPresent());
    }

    @Test
    void repositoryRemoveById_usesCachedPlan() {
        var repository = orm().entity(PlanVisit.class);
        var total = repository.count();
        repository.removeById(1);
        assertEquals(total - 1, repository.count());
        // The second call binds against the plan cached by the first.
        repository.removeById(2);
        assertEquals(total - 2, repository.count());
        assertTrue(repository.findById(1).isEmpty());
    }

    @Test
    void repositoryFindAllRefAndRemoveAll_useCachedConstantPlans() {
        var repository = orm().entity(PlanVisit.class);
        var refs = repository.findAllRef();
        assertEquals(repository.count(), refs.size());
        assertEquals(refs.size(), repository.findAllRef().size());
        repository.removeAll();
        assertEquals(0, repository.count());
        // The second call binds against the plan cached by the first.
        repository.removeAll();
        assertEquals(0, repository.count());
    }

    @Test
    void plan_appliesInterceptorRewritesExactlyOncePerBind() {
        var orm = orm();
        var repository = orm.entity(PlanVisit.class);
        var visit = repository.getById(1);
        var marker = " -- plan-rewrite";

        // Compiled inside a rewriting scope: the rewrite must not bake into the stored statement.
        var plan = SqlInterceptor.intercept(sql -> sql.statement(sql.statement() + marker), () -> fullUpdatePlan(orm));
        var observed = new ArrayList<Sql>();
        observe(observed::add, () ->
                plan.bind(new PlanVisit(visit.id(), visit.visitDate(), "clean", visit.pet())).executeUpdate());
        assertFalse(observed.getFirst().statement().contains(marker));

        // Executed inside a rewriting scope: the rewrite applies exactly once, so the interceptor never sees its
        // own marker on the incoming statement.
        var doubleApplied = new AtomicBoolean();
        SqlInterceptor.intercept(sql -> {
            if (sql.statement().contains(marker)) {
                doubleApplied.set(true);
            }
            return sql.statement(sql.statement() + marker);
        }, () -> plan.bind(new PlanVisit(visit.id(), visit.visitDate(), "rewritten", visit.pet())).executeUpdate());
        assertFalse(doubleApplied.get());
        assertEquals("rewritten", repository.getById(1).description());
    }

    @Test
    void repositoryUpdate_underScopedCustomizer_bypassesCachedPlan() {
        var repository = orm().entity(PlanVisit.class);
        var visit = repository.getById(1);
        // Populate the per-shape plan cache before the customizer scope starts.
        repository.update(new PlanVisit(visit.id(), visit.visitDate(), "primed", visit.pet()));

        var observed = new ArrayList<Sql>();
        observe(template -> template.withInlineParameters(true), observed::add, () ->
                repository.update(new PlanVisit(visit.id(), visit.visitDate(), "customized", visit.pet())));
        var update = observed.stream()
                .filter(sql -> sql.statement().startsWith("UPDATE"))
                .findFirst()
                .orElseThrow();
        // The customized template inlines parameters into the statement; a cached plan would emit placeholders.
        assertTrue(update.statement().contains("customized"));
    }

    @Test
    void plan_repeatedRepositoryUpdates_reuseCachedPlan() {
        // Exercises the repository's per-shape plan cache end to end: the second update binds against the plan
        // created for the first.
        var repository = orm().entity(PlanVisit.class);
        var visit = repository.getById(1);
        repository.update(new PlanVisit(visit.id(), visit.visitDate(), "first", visit.pet()));
        assertEquals("first", repository.getById(1).description());
        repository.update(new PlanVisit(visit.id(), visit.visitDate(), "second", visit.pet()));
        assertEquals("second", repository.getById(1).description());
    }
}
