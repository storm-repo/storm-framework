package st.orm.template;

import static java.lang.StringTemplate.RAW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.SelectMode.DECLARED;
import static st.orm.SelectMode.PK;
import static st.orm.TemporalType.DATE;
import static st.orm.TemporalType.TIME;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.template.model.City;
import st.orm.template.model.Owner;

/**
 * Additional coverage tests for {@link Templates} static methods and
 * {@link st.orm.template.impl.QueryBuilderImpl} methods not exercised by existing tests.
 */
@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@SpringBootTest
@Sql("/data.sql")
public class TemplatesTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ORMTemplate orm;

    // Templates.select with SelectMode variants

    @Test
    public void testSelectWithFlatMode() {
        var selectElement = Templates.select(City.class, DECLARED);
        assertNotNull(selectElement);
    }

    @Test
    public void testSelectWithPkMode() {
        var selectElement = Templates.select(City.class, PK);
        assertNotNull(selectElement);
    }

    // Templates.from with alias and autoJoin false

    @Test
    public void testFromWithAliasAndAutoJoinFalse() {
        var fromElement = Templates.from(City.class, "c", false);
        assertNotNull(fromElement);
    }

    // Templates.insert with ignoreAutoGenerate

    @Test
    public void testInsertWithIgnoreAutoGenerate() {
        var insertElement = Templates.insert(City.class, true);
        assertNotNull(insertElement);
    }

    // Templates.values with ignoreAutoGenerate (single record)

    @Test
    public void testValuesWithIgnoreAutoGenerate() {
        var valuesElement = Templates.values(new City(999, "TestCity"), true);
        assertNotNull(valuesElement);
    }

    // Templates.values with iterable and ignoreAutoGenerate

    @Test
    public void testValuesWithIterableAndIgnoreAutoGenerate() {
        var valuesElement = Templates.values(List.of(new City(998, "City1"), new City(997, "City2")), true);
        assertNotNull(valuesElement);
    }

    // Templates.values with iterable (no ignoreAutoGenerate)

    @Test
    public void testValuesWithIterable() {
        var valuesElement = Templates.values(List.of(new City(null, "CityA"), new City(null, "CityB")));
        assertNotNull(valuesElement);
    }

    // Templates.values with BindVars and ignoreAutoGenerate

    @Test
    public void testValuesWithBindVarsAndIgnoreAutoGenerate() {
        var bindVars = orm.createBindVars();
        var valuesElement = Templates.values(bindVars, true);
        assertNotNull(valuesElement);
    }

    // Templates.set with record (no fields)

    @Test
    public void testSetWithRecord() {
        var setElement = Templates.set(new City(1, "Updated"));
        assertNotNull(setElement);
    }

    // Templates.update with alias

    @Test
    public void testUpdateWithAlias() {
        var updateElement = Templates.update(City.class, "c");
        assertNotNull(updateElement);
    }

    // Templates.where with single object

    @Test
    public void testWhereWithSingleObject() {
        var whereElement = Templates.where(1);
        assertNotNull(whereElement);
    }

    // Templates.table with alias

    @Test
    public void testTableWithAlias() {
        var tableElement = Templates.table(City.class, "c");
        assertNotNull(tableElement);
    }

    // Templates.param with name and value

    @Test
    public void testParamWithNameAndValue() {
        var paramElement = Templates.param("myParam", "test");
        assertNotNull(paramElement);
    }

    // Templates.param with null value

    @Test
    public void testParamWithNullValue() {
        var paramElement = Templates.param(null);
        assertNotNull(paramElement);
    }

    // Templates.param with Date and DATE temporal type

    @Test
    public void testParamWithDateAndDateType() {
        var paramElement = Templates.param(new Date(), DATE);
        assertNotNull(paramElement);
    }

    // Templates.param with Date and TIME temporal type

    @Test
    public void testParamWithDateAndTimeType() {
        var paramElement = Templates.param(new Date(), TIME);
        assertNotNull(paramElement);
    }

    // Templates.param with named Date and temporal type

    @Test
    public void testParamWithNamedDateAndTemporalType() {
        var paramElement = Templates.param("myDate", new Date(), DATE);
        assertNotNull(paramElement);
    }

    // Templates.param with Calendar and DATE temporal type

    @Test
    public void testParamWithCalendarAndDateType() {
        var paramElement = Templates.param(Calendar.getInstance(), DATE);
        assertNotNull(paramElement);
    }

    // Templates.param with Calendar and TIME temporal type

    @Test
    public void testParamWithCalendarAndTimeType() {
        var paramElement = Templates.param(Calendar.getInstance(), TIME);
        assertNotNull(paramElement);
    }

    // Templates.param with named Calendar and temporal type

    @Test
    public void testParamWithNamedCalendarAndTemporalType() {
        var paramElement = Templates.param("myCal", Calendar.getInstance(), DATE);
        assertNotNull(paramElement);
    }

    // QueryBuilderImpl - join with subquery (QueryBuilder)

    @Test
    public void testJoinWithSubqueryBuilder() {
        var subquery = orm.subquery(City.class, RAW."\{City.class}.id, \{City.class}.name");
        List<Owner> owners = orm.entity(Owner.class).select()
                .join(st.orm.JoinType.inner(), subquery, "sub")
                .on(RAW."sub.id = \{Owner.class}.city_id")
                .getResultList();
        assertFalse(owners.isEmpty());
    }

    // QueryBuilderImpl - rightJoin with relation

    @Test
    public void testRightJoinWithRelation() {
        List<City> cities = orm.entity(City.class).select()
                .rightJoin(Owner.class)
                .on(RAW."\{Owner.class}.city_id = \{City.class}.id")
                .getResultList();
        assertFalse(cities.isEmpty());
    }

    // QueryBuilderImpl - rightJoin with template

    @Test
    public void testRightJoinWithTemplate() {
        List<City> cities = orm.entity(City.class).select()
                .rightJoin(RAW."owner", "o")
                .on(RAW."o.city_id = \{City.class}.id")
                .getResultList();
        assertFalse(cities.isEmpty());
    }

    // QueryBuilderImpl - leftJoin with template

    @Test
    public void testLeftJoinWithTemplate() {
        List<City> cities = orm.entity(City.class).select()
                .leftJoin(RAW."owner", "o")
                .on(RAW."o.city_id = \{City.class}.id")
                .getResultList();
        assertFalse(cities.isEmpty());
    }

    // QueryBuilderImpl - innerJoin with template

    @Test
    public void testInnerJoinWithTemplate() {
        List<City> cities = orm.entity(City.class).select()
                .innerJoin(RAW."owner", "o")
                .on(RAW."o.city_id = \{City.class}.id")
                .getResultList();
        assertFalse(cities.isEmpty());
    }

    // QueryBuilderImpl - crossJoin with template

    @Test
    public void testCrossJoinWithTemplate() {
        List<City> cities = orm.entity(City.class).select()
                .crossJoin(RAW."owner")
                .limit(5)
                .getResultList();
        assertEquals(5, cities.size());
    }

    // QueryBuilderImpl.append

    @Test
    public void testQueryBuilderAppend() {
        List<City> cities = orm.entity(City.class).select()
                .where(wb -> wb.where(RAW."1 = 1"))
                .append(RAW." ORDER BY \{City.class}.name ASC")
                .getResultList();
        assertFalse(cities.isEmpty());
        // Verify ordering
        for (int i = 1; i < cities.size(); i++) {
            assertTrue(cities.get(i - 1).name().compareTo(cities.get(i).name()) <= 0);
        }
    }

    // QueryBuilderImpl.build

    @Test
    public void testQueryBuilderBuild() {
        Query query = orm.entity(City.class).select().build();
        assertNotNull(query);
    }

    // QueryTemplateImpl - query(String)

    @Test
    public void testQueryWithRawString() {
        long count = orm.query("SELECT COUNT(*) FROM city").getSingleResult(long.class);
        assertEquals(6, count);
    }

    // QueryTemplateImpl - dialect()

    @Test
    public void testDialect() {
        var dialect = orm.dialect();
        assertNotNull(dialect);
    }

    // QueryTemplateImpl - model without requiring primary key

    @Test
    public void testModelWithoutRequiringPrimaryKey() {
        Model<City, ?> model = orm.model(City.class, false);
        assertNotNull(model);
        assertFalse(model.columns().isEmpty());
    }

    // PredicateBuilder - and with StringTemplate

    @Test
    public void testPredicateBuilderAndWithTemplate() {
        List<City> cities = orm.entity(City.class).select()
                .where(wb -> wb.where(RAW."\{City.class}.id > \{0}")
                        .and(RAW."\{City.class}.id < \{4}"))
                .getResultList();
        assertEquals(3, cities.size());
    }

    // PredicateBuilder - or with StringTemplate

    @Test
    public void testPredicateBuilderOrWithTemplate() {
        List<City> cities = orm.entity(City.class).select()
                .where(wb -> wb.where(RAW."\{City.class}.id = \{1}")
                        .or(RAW."\{City.class}.id = \{2}"))
                .getResultList();
        assertEquals(2, cities.size());
    }
}
