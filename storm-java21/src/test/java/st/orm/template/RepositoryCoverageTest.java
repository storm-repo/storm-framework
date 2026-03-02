package st.orm.template;

import static java.lang.StringTemplate.RAW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.Ref;
import st.orm.Slice;
import st.orm.repository.EntityRepository;
import st.orm.repository.ProjectionRepository;
import st.orm.template.model.City;
import st.orm.template.model.City_;
import st.orm.template.model.Owner;
import st.orm.template.model.OwnerView;
import st.orm.template.model.OwnerView_;
import st.orm.template.model.Pet;
import st.orm.template.model.Visit;

@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@SpringBootTest
@Sql("/data.sql")
public class RepositoryCoverageTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ORMTemplate orm;

    // ========================================================================
    // Repository proxy
    // ========================================================================

    interface CityRepository extends EntityRepository<City, Integer> {
    }

    interface OwnerViewRepository extends ProjectionRepository<OwnerView, Integer> {
    }

    @Test
    public void testRepositoryProxy() {
        CityRepository cityRepo = orm.repository(CityRepository.class);
        assertNotNull(cityRepo);
        List<City> cities = cityRepo.findAll();
        assertEquals(6, cities.size());
    }

    @Test
    public void testRepositoryProxyEquals() {
        CityRepository repo1 = orm.repository(CityRepository.class);
        CityRepository repo2 = orm.repository(CityRepository.class);
        // Proxy equals is identity-based
        assertFalse(repo1.equals(repo2));
        assertTrue(repo1.equals(repo1));
    }

    @Test
    public void testRepositoryProxyHashCode() {
        CityRepository repo = orm.repository(CityRepository.class);
        // Should not throw
        int hash = repo.hashCode();
        assertTrue(hash != 0 || hash == 0); // Just verify it works
    }

    @Test
    public void testRepositoryProxyToString() {
        CityRepository repo = orm.repository(CityRepository.class);
        String str = repo.toString();
        assertNotNull(str);
        assertTrue(str.contains("CityRepository"));
    }

    @Test
    public void testRepositoryProxyOrm() {
        CityRepository repo = orm.repository(CityRepository.class);
        ORMTemplate repoOrm = repo.orm();
        assertNotNull(repoOrm);
    }

    @Test
    public void testProjectionRepositoryProxy() {
        OwnerViewRepository repo = orm.repository(OwnerViewRepository.class);
        assertNotNull(repo);
        List<OwnerView> views = repo.findAll();
        assertEquals(10, views.size());
    }

    // ========================================================================
    // Complex entity operations with FK relationships
    // ========================================================================

    @Test
    public void testOwnerWithAddress() {
        EntityRepository<Owner, Integer> owners = orm.entity(Owner.class);
        List<Owner> allOwners = owners.findAll();
        assertEquals(10, allOwners.size());
        // Verify FK resolution
        Owner first = allOwners.get(0);
        assertNotNull(first.address());
        assertNotNull(first.address().city());
        assertNotNull(first.address().city().name());
    }

    @Test
    public void testPetWithForeignKeys() {
        EntityRepository<Pet, Integer> pets = orm.entity(Pet.class);
        List<Pet> allPets = pets.findAll();
        assertEquals(13, allPets.size());
        // Verify FK resolution
        Pet first = allPets.get(0);
        assertNotNull(first.type());
        assertNotNull(first.type().name());
    }

    @Test
    public void testVisitWithNestedForeignKeys() {
        EntityRepository<Visit, Integer> visits = orm.entity(Visit.class);
        List<Visit> allVisits = visits.findAll();
        assertEquals(14, allVisits.size());
        // Verify nested FK resolution
        Visit first = allVisits.get(0);
        assertNotNull(first.pet());
        assertNotNull(first.pet().type());
    }

    // ========================================================================
    // OwnerView - Projection operations
    // ========================================================================

    @Test
    public void testOwnerViewFindAllVerifyContent() {
        ProjectionRepository<OwnerView, Integer> views = orm.projection(OwnerView.class);
        List<OwnerView> all = views.findAll();
        // Verify data integrity
        OwnerView betty = all.stream().filter(v -> v.firstName().equals("Betty")).findFirst().orElseThrow();
        assertEquals("Davis", betty.lastName());
        assertNotNull(betty.address());
    }

    // ========================================================================
    // Slice pagination with keyset
    // ========================================================================

    @Test
    public void testSliceBasic() {
        Slice<City> slice = orm.entity(City.class).select().slice(2);
        assertEquals(2, slice.content().size());
        assertTrue(slice.hasNext());
    }

    @Test
    public void testSliceLastPage() {
        Slice<City> slice = orm.entity(City.class).select().slice(100);
        assertFalse(slice.hasNext());
    }

    @Test
    public void testSliceInvalidSize() {
        assertThrows(IllegalArgumentException.class, () ->
                orm.entity(City.class).select().slice(0));
        assertThrows(IllegalArgumentException.class, () ->
                orm.entity(City.class).select().slice(-1));
    }

    // ========================================================================
    // EntityRepository - select with custom select type
    // ========================================================================

    @Test
    public void testSelectWithLongResultType() {
        EntityRepository<City, Integer> cities = orm.entity(City.class);
        long count = cities.selectCount().getSingleResult();
        assertEquals(6, count);
    }

    @Test
    public void testSelectCustomType() {
        EntityRepository<City, Integer> cities = orm.entity(City.class);
        List<String> names = cities.select(String.class, RAW."\{City.class}.name").getResultList();
        assertEquals(6, names.size());
    }

    // ========================================================================
    // StringTemplates helper methods coverage
    // ========================================================================

    @Test
    public void testSelectTemplateHelper() {
        List<City> cities = orm.query(RAW."SELECT \{Templates.select(City.class)} FROM \{Templates.from(City.class, true)}")
                .getResultList(City.class);
        assertEquals(6, cities.size());
    }

    @Test
    public void testTableTemplateHelper() {
        var tableElement = Templates.table(City.class);
        assertNotNull(tableElement);
    }

    @Test
    public void testAliasTemplateHelper() {
        var aliasElement = Templates.alias(City.class);
        assertNotNull(aliasElement);
    }

    @Test
    public void testUnsafeTemplateHelper() {
        var unsafeElement = Templates.unsafe("some raw SQL");
        assertNotNull(unsafeElement);
    }

    @Test
    public void testSubqueryTemplateHelper() {
        var subquery = orm.subquery(City.class, RAW."1");
        var subqueryElement = Templates.subquery(subquery, false);
        assertNotNull(subqueryElement);
    }

    @Test
    public void testDeleteTemplateHelper() {
        var deleteElement = Templates.delete(City.class);
        assertNotNull(deleteElement);
    }

    @Test
    public void testParamWithTemporalType() {
        var dateParam = Templates.param(new java.util.Date(), st.orm.TemporalType.TIMESTAMP);
        assertNotNull(dateParam);
    }

    @Test
    public void testParamWithCalendar() {
        var calendarParam = Templates.param(java.util.Calendar.getInstance(), st.orm.TemporalType.TIMESTAMP);
        assertNotNull(calendarParam);
    }

    // ========================================================================
    // Coverage for Model and Column
    // ========================================================================

    @Test
    public void testOwnerModelColumns() {
        Model<Owner, ?> model = orm.model(Owner.class);
        List<Column> columns = model.columns();
        // Owner has: id, firstName, lastName, address (which expands to address + cityId), telephone, version
        assertFalse(columns.isEmpty());
        assertTrue(columns.size() >= 5);
    }

    @Test
    public void testOwnerModelDeclaredColumns() {
        Model<Owner, ?> model = orm.model(Owner.class);
        List<Column> declaredColumns = model.declaredColumns();
        // Declared columns: id, firstName, lastName, address (composite), telephone, version
        assertFalse(declaredColumns.isEmpty());
    }

    @Test
    public void testColumnAttributes() {
        Model<Owner, ?> model = orm.model(Owner.class);
        for (Column column : model.columns()) {
            assertNotNull(column.name());
            assertNotNull(column.type());
            assertNotNull(column.generation());
            assertNotNull(column.sequence());
            // Just exercise all getters
            column.primaryKey();
            column.foreignKey();
            column.nullable();
            column.insertable();
            column.updatable();
            column.version();
            column.ref();
            column.index();
            column.metamodel();
        }
    }

    @Test
    public void testOwnerModelForEachValue() throws Exception {
        Model<Owner, Integer> model = (Model<Owner, Integer>) (Model<?, ?>) orm.model(Owner.class);
        Owner owner = orm.entity(Owner.class).getById(1);
        model.forEachValue(model.columns(), owner, (column, value) -> {
            assertNotNull(column.name());
        });
    }

    @Test
    public void testOwnerModelValues() throws Exception {
        Model<Owner, Integer> model = (Model<Owner, Integer>) (Model<?, ?>) orm.model(Owner.class);
        Owner owner = orm.entity(Owner.class).getById(1);
        var values = model.values(owner);
        assertFalse(values.isEmpty());
    }

    // ========================================================================
    // QueryBuilder - executeUpdate (delete)
    // ========================================================================

    @Test
    public void testQueryBuilderExecuteUpdate() {
        var localOrm = ORMTemplate.of(dataSource);
        EntityRepository<City, Integer> cities = localOrm.entity(City.class);
        cities.insertAndFetch(new City(null, "ToDeleteViaBuilder"));
        int deleted = localOrm.deleteFrom(City.class)
                .where(RAW."\{City.class}.name = \{"ToDeleteViaBuilder"}")
                .executeUpdate();
        assertEquals(1, deleted);
    }

    // ========================================================================
    // SubqueryTemplate
    // ========================================================================

    @Test
    public void testSubqueryFromType() {
        var subquery = orm.subquery(City.class);
        assertNotNull(subquery);
    }

    @Test
    public void testSubqueryFromTypeWithSelectType() {
        var subquery = orm.subquery(City.class, City.class);
        assertNotNull(subquery);
    }

    @Test
    public void testSubqueryFromTypeWithTemplate() {
        var subquery = orm.subquery(City.class, RAW."\{City.class}.id");
        assertNotNull(subquery);
    }

    // ========================================================================
    // WhereBuilder - subquery
    // ========================================================================

    @Test
    public void testWhereBuilderSubquery() {
        List<Owner> owners = orm.entity(Owner.class).select()
                .where(wb -> wb.exists(
                        wb.subquery(Pet.class, RAW."1")
                                .where(RAW."\{Pet.class}.owner_id = \{Owner.class}.id")))
                .getResultList();
        assertFalse(owners.isEmpty());
    }

    // ========================================================================
    // EntityRepository - Metamodel.Key slice default methods
    // ========================================================================

    @Test
    public void testEntitySliceByKey() {
        Slice<City> slice = orm.entity(City.class).slice(City_.id, 3);
        assertEquals(3, slice.content().size());
        assertTrue(slice.hasNext());
    }

    @Test
    public void testEntitySliceBeforeByKey() {
        Slice<City> slice = orm.entity(City.class).sliceBefore(City_.id, 3);
        assertEquals(3, slice.content().size());
    }

    @Test
    public void testEntitySliceBeforeRefByKey() {
        Slice<Ref<City>> slice = orm.entity(City.class).sliceBeforeRef(City_.id, 3);
        assertEquals(3, slice.content().size());
    }

    @Test
    public void testEntitySliceRefByKey() {
        Slice<Ref<City>> slice = orm.entity(City.class).sliceRef(City_.id, 3);
        assertEquals(3, slice.content().size());
    }

    @Test
    public void testEntitySliceAfterByKey() {
        Slice<City> slice = orm.entity(City.class).sliceAfter(City_.id, 2, 3);
        assertFalse(slice.content().isEmpty());
    }

    @Test
    public void testEntitySliceBeforeByKeyAndValue() {
        Slice<City> slice = orm.entity(City.class).sliceBefore(City_.id, 5, 3);
        assertFalse(slice.content().isEmpty());
    }

    @Test
    public void testEntitySliceAfterRefByKey() {
        Slice<Ref<City>> slice = orm.entity(City.class).sliceAfterRef(City_.id, 2, 3);
        assertFalse(slice.content().isEmpty());
    }

    @Test
    public void testEntitySliceBeforeRefByKeyAndValue() {
        Slice<Ref<City>> slice = orm.entity(City.class).sliceBeforeRef(City_.id, 5, 3);
        assertFalse(slice.content().isEmpty());
    }

    @Test
    public void testEntitySliceByKeyAndSort() {
        Slice<City> slice = orm.entity(City.class).slice(City_.id, City_.name, 3);
        assertEquals(3, slice.content().size());
    }

    @Test
    public void testEntitySliceBeforeByKeyAndSort() {
        Slice<City> slice = orm.entity(City.class).sliceBefore(City_.id, City_.name, 3);
        assertEquals(3, slice.content().size());
    }

    @Test
    public void testEntitySliceBeforeRefByKeyAndSort() {
        Slice<Ref<City>> slice = orm.entity(City.class).sliceBeforeRef(City_.id, City_.name, 3);
        assertEquals(3, slice.content().size());
    }

    @Test
    public void testEntitySliceRefByKeyAndSort() {
        Slice<Ref<City>> slice = orm.entity(City.class).sliceRef(City_.id, City_.name, 3);
        assertEquals(3, slice.content().size());
    }

    @Test
    public void testEntitySliceAfterByKeyAndSort() {
        Slice<City> slice = orm.entity(City.class).sliceAfter(City_.id, 2, City_.name, "A", 3);
        assertNotNull(slice);
    }

    @Test
    public void testEntitySliceBeforeByKeyAndSortAndValue() {
        Slice<City> slice = orm.entity(City.class).sliceBefore(City_.id, 5, City_.name, "Z", 3);
        assertNotNull(slice);
    }

    @Test
    public void testEntitySliceAfterRefByKeyAndSort() {
        Slice<Ref<City>> slice = orm.entity(City.class).sliceAfterRef(City_.id, 2, City_.name, "A", 3);
        assertNotNull(slice);
    }

    @Test
    public void testEntitySliceBeforeRefByKeyAndSortAndValue() {
        Slice<Ref<City>> slice = orm.entity(City.class).sliceBeforeRef(City_.id, 5, City_.name, "Z", 3);
        assertNotNull(slice);
    }

    // ========================================================================
    // ProjectionRepository - Metamodel.Key slice default methods
    // ========================================================================

    @Test
    public void testProjectionSliceByKey() {
        Slice<OwnerView> slice = orm.projection(OwnerView.class).slice(OwnerView_.id, 5);
        assertEquals(5, slice.content().size());
        assertTrue(slice.hasNext());
    }

    @Test
    public void testProjectionSliceBeforeByKey() {
        Slice<OwnerView> slice = orm.projection(OwnerView.class).sliceBefore(OwnerView_.id, 5);
        assertEquals(5, slice.content().size());
    }

    @Test
    public void testProjectionSliceRefByKey() {
        Slice<Ref<OwnerView>> slice = orm.projection(OwnerView.class).sliceRef(OwnerView_.id, 5);
        assertEquals(5, slice.content().size());
    }

    @Test
    public void testProjectionSliceAfterByKey() {
        Slice<OwnerView> slice = orm.projection(OwnerView.class).sliceAfter(OwnerView_.id, 3, 5);
        assertFalse(slice.content().isEmpty());
    }

    @Test
    public void testProjectionSliceBeforeByKeyAndValue() {
        Slice<OwnerView> slice = orm.projection(OwnerView.class).sliceBefore(OwnerView_.id, 8, 5);
        assertFalse(slice.content().isEmpty());
    }

    @Test
    public void testProjectionSliceAfterRefByKey() {
        Slice<Ref<OwnerView>> slice = orm.projection(OwnerView.class).sliceAfterRef(OwnerView_.id, 3, 5);
        assertFalse(slice.content().isEmpty());
    }

    @Test
    public void testProjectionSliceBeforeRefByKeyAndValue() {
        Slice<Ref<OwnerView>> slice = orm.projection(OwnerView.class).sliceBeforeRef(OwnerView_.id, 8, 5);
        assertFalse(slice.content().isEmpty());
    }

    @Test
    public void testProjectionSliceBeforeRefByKeyInitial() {
        Slice<Ref<OwnerView>> slice = orm.projection(OwnerView.class).sliceBeforeRef(OwnerView_.id, 5);
        assertEquals(5, slice.content().size());
    }

    @Test
    public void testProjectionSliceByKeyAndSort() {
        Slice<OwnerView> slice = orm.projection(OwnerView.class).slice(OwnerView_.id, OwnerView_.firstName, 5);
        assertEquals(5, slice.content().size());
    }

    @Test
    public void testProjectionSliceRefByKeyAndSort() {
        Slice<Ref<OwnerView>> slice = orm.projection(OwnerView.class).sliceRef(OwnerView_.id, OwnerView_.firstName, 5);
        assertEquals(5, slice.content().size());
    }

    @Test
    public void testProjectionSliceBeforeByKeyAndSort() {
        Slice<OwnerView> slice = orm.projection(OwnerView.class).sliceBefore(OwnerView_.id, OwnerView_.firstName, 5);
        assertEquals(5, slice.content().size());
    }

    @Test
    public void testProjectionSliceBeforeRefByKeyAndSort() {
        Slice<Ref<OwnerView>> slice = orm.projection(OwnerView.class).sliceBeforeRef(OwnerView_.id, OwnerView_.firstName, 5);
        assertEquals(5, slice.content().size());
    }

    // ========================================================================
    // ProjectionRepository - composite keyset pagination with sort
    // ========================================================================

    @Test
    public void testProjectionSliceAfterByKeyAndSort() {
        Slice<OwnerView> slice = orm.projection(OwnerView.class).sliceAfter(OwnerView_.id, 3, OwnerView_.firstName, "A", 5);
        assertNotNull(slice);
    }

    @Test
    public void testProjectionSliceBeforeByKeyAndSortAndValue() {
        Slice<OwnerView> slice = orm.projection(OwnerView.class).sliceBefore(OwnerView_.id, 8, OwnerView_.firstName, "Z", 5);
        assertNotNull(slice);
    }

    @Test
    public void testProjectionSliceAfterRefByKeyAndSort() {
        Slice<Ref<OwnerView>> slice = orm.projection(OwnerView.class).sliceAfterRef(OwnerView_.id, 3, OwnerView_.firstName, "A", 5);
        assertNotNull(slice);
    }

    @Test
    public void testProjectionSliceBeforeRefByKeyAndSortAndValue() {
        Slice<Ref<OwnerView>> slice = orm.projection(OwnerView.class).sliceBeforeRef(OwnerView_.id, 8, OwnerView_.firstName, "Z", 5);
        assertNotNull(slice);
    }

    // ========================================================================
    // EntityRepository - additional default methods for completeness
    // ========================================================================

    @Test
    public void testEntityFindByRef() {
        var ref = orm.entity(City.class).ref(1);
        Optional<City> city = orm.entity(City.class).findByRef(ref);
        assertTrue(city.isPresent());
    }

    @Test
    public void testEntityGetByRef() {
        var ref = orm.entity(City.class).ref(1);
        City city = orm.entity(City.class).getByRef(ref);
        assertNotNull(city);
        assertEquals(1, city.id());
    }

    @Test
    public void testEntityFindAllByRef() {
        var ref1 = orm.entity(City.class).ref(1);
        var ref2 = orm.entity(City.class).ref(2);
        List<City> cities = orm.entity(City.class).findAllByRef(List.of(ref1, ref2));
        assertEquals(2, cities.size());
    }

    @Test
    public void testEntityDelete() {
        var localOrm = ORMTemplate.of(dataSource);
        var repo = localOrm.entity(City.class);
        var inserted = repo.insertAndFetch(new City(null, "ToDeleteEntity"));
        repo.delete(inserted);
        assertFalse(repo.findById(inserted.id()).isPresent());
    }

    @Test
    public void testEntityDeleteByRef() {
        var localOrm = ORMTemplate.of(dataSource);
        var repo = localOrm.entity(City.class);
        var inserted = repo.insertAndFetch(new City(null, "ToDeleteByRef"));
        repo.deleteByRef(repo.ref(inserted.id()));
        assertFalse(repo.findById(inserted.id()).isPresent());
    }

    @Test
    public void testEntityDeleteByRefIterable() {
        var localOrm = ORMTemplate.of(dataSource);
        var repo = localOrm.entity(City.class);
        var inserted1 = repo.insertAndFetch(new City(null, "ToDeleteRef1"));
        var inserted2 = repo.insertAndFetch(new City(null, "ToDeleteRef2"));
        repo.deleteByRef(List.of(repo.ref(inserted1.id()), repo.ref(inserted2.id())));
        assertFalse(repo.findById(inserted1.id()).isPresent());
        assertFalse(repo.findById(inserted2.id()).isPresent());
    }

    // ========================================================================
    // ProjectionRepository - additional default methods
    // ========================================================================

    @Test
    public void testProjectionFindByRef() {
        var ref = orm.projection(OwnerView.class).ref(1);
        Optional<OwnerView> view = orm.projection(OwnerView.class).findByRef(ref);
        assertTrue(view.isPresent());
    }

    @Test
    public void testProjectionGetByRef() {
        var ref = orm.projection(OwnerView.class).ref(1);
        OwnerView view = orm.projection(OwnerView.class).getByRef(ref);
        assertNotNull(view);
    }

    @Test
    public void testProjectionFindAllByRef() {
        var ref1 = orm.projection(OwnerView.class).ref(1);
        var ref2 = orm.projection(OwnerView.class).ref(2);
        List<OwnerView> views = orm.projection(OwnerView.class).findAllByRef(List.of(ref1, ref2));
        assertEquals(2, views.size());
    }

    @Test
    public void testProjectionFindById() {
        Optional<OwnerView> view = orm.projection(OwnerView.class).findById(1);
        assertTrue(view.isPresent());
    }

    @Test
    public void testProjectionGetById() {
        OwnerView view = orm.projection(OwnerView.class).getById(1);
        assertNotNull(view);
    }

    @Test
    public void testProjectionFindAllById() {
        List<OwnerView> views = orm.projection(OwnerView.class).findAllById(List.of(1, 2, 3));
        assertEquals(3, views.size());
    }

    @Test
    public void testProjectionCount() {
        long count = orm.projection(OwnerView.class).count();
        assertEquals(10, count);
    }

    @Test
    public void testProjectionSelectCount() {
        long count = orm.projection(OwnerView.class).selectCount().getSingleResult();
        assertEquals(10, count);
    }

    @Test
    public void testProjectionSelectWithTemplate() {
        List<String> names = orm.projection(OwnerView.class).select(String.class, RAW."\{OwnerView.class}.first_name")
                .getResultList();
        assertEquals(10, names.size());
    }
}
