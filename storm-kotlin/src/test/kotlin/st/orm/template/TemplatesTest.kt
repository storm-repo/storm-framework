package st.orm.template

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.GenerationStrategy
import st.orm.Metamodel
import st.orm.Operator
import st.orm.ResolveScope
import st.orm.SelectMode
import st.orm.StormConfig
import st.orm.TemporalType
import st.orm.core.template.impl.Elements
import st.orm.template.model.*
import java.util.*
import javax.sql.DataSource

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@Sql("/data.sql")
open class TemplatesTest(
    @Autowired val orm: ORMTemplate,
    @Autowired val dataSource: DataSource,
) {

    // ======================================================================
    // Templates.select tests
    // ======================================================================

    @Test
    fun `select with table should return Select element`() {
        val element = Templates.select(City::class)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Select>()
    }

    @Test
    fun `select with table should default to NESTED mode`() {
        val element = Templates.select(City::class) as Elements.Select
        element.mode() shouldBe SelectMode.NESTED
    }

    @Test
    fun `select with NESTED mode should return Select element with NESTED`() {
        val element = Templates.select(City::class, SelectMode.NESTED) as Elements.Select
        element.mode() shouldBe SelectMode.NESTED
    }

    @Test
    fun `select with DECLARED mode should return Select element with DECLARED`() {
        val element = Templates.select(City::class, SelectMode.DECLARED) as Elements.Select
        element.mode() shouldBe SelectMode.DECLARED
    }

    @Test
    fun `select with PK mode should return Select element with PK`() {
        val element = Templates.select(City::class, SelectMode.PK) as Elements.Select
        element.mode() shouldBe SelectMode.PK
    }

    // ======================================================================
    // Templates.from tests
    // ======================================================================

    @Test
    fun `from with table and autoJoin false should return From element`() {
        val element = Templates.from(City::class, false)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.From>()
    }

    @Test
    fun `from with table and autoJoin true should set autoJoin`() {
        val element = Templates.from(City::class, true) as Elements.From
        element.autoJoin() shouldBe true
    }

    @Test
    fun `from with table alias and autoJoin should set both`() {
        val element = Templates.from(City::class, "c", true) as Elements.From
        element.alias() shouldBe "c"
        element.autoJoin() shouldBe true
    }

    @Test
    fun `from with template builder and alias should return From element`() {
        val element = Templates.from({ "SELECT 1" }, "sub")
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.From>()
        (element as Elements.From).alias() shouldBe "sub"
    }

    // ======================================================================
    // Templates.insert tests
    // ======================================================================

    @Test
    fun `insert with table should return Insert element`() {
        val element = Templates.insert(City::class)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Insert>()
    }

    @Test
    fun `insert with ignoreAutoGenerate true should return Insert element`() {
        val element = Templates.insert(City::class, true) as Elements.Insert
        element.ignoreAutoGenerate() shouldBe true
    }

    @Test
    fun `insert with ignoreAutoGenerate false should return Insert element`() {
        val element = Templates.insert(City::class, false) as Elements.Insert
        element.ignoreAutoGenerate() shouldBe false
    }

    // ======================================================================
    // Templates.values tests
    // ======================================================================

    @Test
    fun `values with single record should return Values element`() {
        val city = City(name = "TestCity")
        val element = Templates.values(city)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Values>()
    }

    @Test
    fun `values with single record and ignoreAutoGenerate should set flag`() {
        val city = City(id = 100, name = "TestCity")
        val element = Templates.values(city, true) as Elements.Values
        element.ignoreAutoGenerate() shouldBe true
    }

    @Test
    fun `values with iterable should return Values element`() {
        val cities = listOf(City(name = "A"), City(name = "B"))
        val element = Templates.values(cities)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Values>()
    }

    @Test
    fun `values with iterable and ignoreAutoGenerate should set flag`() {
        val cities = listOf(City(id = 100, name = "A"), City(id = 101, name = "B"))
        val element = Templates.values(cities, true) as Elements.Values
        element.ignoreAutoGenerate() shouldBe true
    }

    @Test
    fun `values with bindVars should return Values element`() {
        val bindVars = orm.createBindVars()
        val element = Templates.values(bindVars)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Values>()
    }

    @Test
    fun `values with bindVars and ignoreAutoGenerate should set flag`() {
        val bindVars = orm.createBindVars()
        val element = Templates.values(bindVars, true) as Elements.Values
        element.ignoreAutoGenerate() shouldBe true
    }

    // ======================================================================
    // Templates.update tests
    // ======================================================================

    @Test
    fun `update with table should return Update element`() {
        val element = Templates.update(City::class)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Update>()
    }

    @Test
    fun `update with table and alias should set alias`() {
        val element = Templates.update(City::class, "c") as Elements.Update
        element.alias() shouldBe "c"
    }

    // ======================================================================
    // Templates.set tests
    // ======================================================================

    @Test
    fun `set with record should return Set element`() {
        val city = City(id = 1, name = "Updated")
        val element = Templates.set(city)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Set>()
    }

    @Test
    fun `set with bindVars should return Set element`() {
        val bindVars = orm.createBindVars()
        val element = Templates.set(bindVars)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Set>()
    }

    // ======================================================================
    // Templates.where tests
    // ======================================================================

    @Test
    fun `where with iterable should return Where element`() {
        val ids = listOf(1, 2, 3)
        val element = Templates.where(ids)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Where>()
    }

    @Test
    fun `where with single object should return Where element`() {
        val element = Templates.where(1 as Any)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Where>()
    }

    @Test
    fun `where with entity instance should return Where element`() {
        val city = City(id = 1, name = "Sun Paririe")
        val element = Templates.where(city as Any)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Where>()
    }

    @Test
    fun `where with path operator and iterable should return Where element`() {
        val metamodel = Metamodel.of<City, Int>(City::class.java, "id")
        val element = Templates.where(metamodel, Operator.IN, listOf(1, 2, 3))
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Where>()
    }

    @Test
    fun `where with path operator and vararg should return Where element`() {
        val metamodel = Metamodel.of<City, Int>(City::class.java, "id")
        val element = Templates.where(metamodel, Operator.BETWEEN, 1, 5)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Where>()
    }

    @Test
    fun `where with bindVars should return Where element`() {
        val bindVars = orm.createBindVars()
        val element = Templates.where(bindVars)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Where>()
    }

    // ======================================================================
    // Templates.delete tests
    // ======================================================================

    @Test
    fun `delete with table should return Delete element`() {
        val element = Templates.delete(City::class)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Delete>()
    }

    @Test
    fun `delete with table and alias should set alias`() {
        val element = Templates.delete(City::class, "c") as Elements.Delete
        element.alias() shouldBe "c"
    }

    // ======================================================================
    // Templates.table tests
    // ======================================================================

    @Test
    fun `table with class should return Table element`() {
        val element = Templates.table(City::class)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Table>()
    }

    @Test
    fun `table with class and alias should set alias`() {
        val element = Templates.table(City::class, "c") as Elements.Table
        element.alias() shouldBe "c"
    }

    // ======================================================================
    // Templates.alias tests
    // ======================================================================

    @Test
    fun `alias with table should return Alias element with CASCADE scope`() {
        val element = Templates.alias(City::class) as Elements.Alias
        element.scope() shouldBe ResolveScope.CASCADE
    }

    @Test
    fun `alias with INNER scope should return Alias element`() {
        val element = Templates.alias(City::class, ResolveScope.INNER) as Elements.Alias
        element.scope() shouldBe ResolveScope.INNER
    }

    @Test
    fun `alias with OUTER scope should return Alias element`() {
        val element = Templates.alias(City::class, ResolveScope.OUTER) as Elements.Alias
        element.scope() shouldBe ResolveScope.OUTER
    }

    // ======================================================================
    // Templates.column tests
    // ======================================================================

    @Test
    fun `column with metamodel should return Column element with CASCADE scope`() {
        val metamodel = Metamodel.of<City, String>(City::class.java, "name")
        val element = Templates.column(metamodel) as Elements.Column
        element.scope() shouldBe ResolveScope.CASCADE
    }

    @Test
    fun `column with metamodel and INNER scope should return Column element`() {
        val metamodel = Metamodel.of<City, String>(City::class.java, "name")
        val element = Templates.column(metamodel, ResolveScope.INNER) as Elements.Column
        element.scope() shouldBe ResolveScope.INNER
    }

    // ======================================================================
    // Templates.param tests
    // ======================================================================

    @Test
    fun `param with value should return Param element`() {
        val element = Templates.param(42)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Param>()
    }

    @Test
    fun `param with null value should return Param element`() {
        val element = Templates.param(null)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Param>()
    }

    @Test
    fun `param with name and value should set name`() {
        val element = Templates.param("status", 1) as Elements.Param
        element.name() shouldBe "status"
    }

    @Test
    fun `param with converter should return Param element`() {
        val element = Templates.param(42) { it * 2 }
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Param>()
    }

    @Test
    fun `param with name and converter should set name`() {
        val element = Templates.param("doubled", 42) { it * 2 } as Elements.Param
        element.name() shouldBe "doubled"
    }

    @Test
    fun `param with Date and DATE temporal type should return Param element`() {
        val date = Date()
        val element = Templates.param(date, TemporalType.DATE)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Param>()
    }

    @Test
    fun `param with Date and TIME temporal type should return Param element`() {
        val date = Date()
        val element = Templates.param(date, TemporalType.TIME)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Param>()
    }

    @Test
    fun `param with Date and TIMESTAMP temporal type should return Param element`() {
        val date = Date()
        val element = Templates.param(date, TemporalType.TIMESTAMP)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Param>()
    }

    @Test
    fun `param with Calendar and DATE temporal type should return Param element`() {
        val cal = Calendar.getInstance()
        val element = Templates.param(cal, TemporalType.DATE)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Param>()
    }

    @Test
    fun `param with Calendar and TIME temporal type should return Param element`() {
        val cal = Calendar.getInstance()
        val element = Templates.param(cal, TemporalType.TIME)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Param>()
    }

    @Test
    fun `param with Calendar and TIMESTAMP temporal type should return Param element`() {
        val cal = Calendar.getInstance()
        val element = Templates.param(cal, TemporalType.TIMESTAMP)
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Param>()
    }

    // ======================================================================
    // Templates.unsafe tests
    // ======================================================================

    @Test
    fun `unsafe should return Unsafe element`() {
        val element = Templates.unsafe("1 = 1")
        element.shouldNotBe(null)
        element.shouldBeInstanceOf<Elements.Unsafe>()
    }

    @Test
    fun `unsafe should preserve sql content`() {
        val sql = "city = 'Sunnyvale'"
        val element = Templates.unsafe(sql) as Elements.Unsafe
        element.sql() shouldBe sql
    }

    // ======================================================================
    // Templates integration: query execution using template builder
    // ======================================================================

    @Test
    fun `select and from elements should produce valid query`() {
        // data.sql inserts exactly 6 cities (ids 1-6).
        val result = orm.query {
            "SELECT ${t(Templates.select(City::class))} FROM ${t(Templates.from(City::class, false))}"
        }.getResultList(City::class)
        result.size shouldBe 6
    }

    // ======================================================================
    // ORMTemplate.of factory methods
    // ======================================================================

    @Test
    fun `ORMTemplate of dataSource should create valid template`() {
        // data.sql inserts 6 cities; query verifies the template can execute queries.
        val template = ORMTemplate.of(dataSource)
        template.shouldNotBe(null)
        template.shouldBeInstanceOf<ORMTemplate>()
        template.entity(City::class).findAll().size shouldBe 6
    }

    @Test
    fun `ORMTemplate of connection should create valid template`() {
        // data.sql inserts 6 cities; query verifies the template can execute queries.
        dataSource.connection.use { conn ->
            val template = ORMTemplate.of(conn)
            template.shouldNotBe(null)
            template.shouldBeInstanceOf<ORMTemplate>()
            template.entity(City::class).findAll().size shouldBe 6
        }
    }

    @Test
    fun `ORMTemplate of dataSource with decorator should create valid template`() {
        // data.sql inserts 6 cities; identity decorator should not alter behavior.
        val template = ORMTemplate.of(dataSource) { it }
        template.shouldNotBe(null)
        template.shouldBeInstanceOf<ORMTemplate>()
        template.entity(City::class).findAll().size shouldBe 6
    }

    @Test
    fun `ORMTemplate of connection with decorator should create valid template`() {
        // data.sql inserts 6 cities; identity decorator should not alter behavior.
        dataSource.connection.use { conn ->
            val template = ORMTemplate.of(conn) { it }
            template.shouldNotBe(null)
            template.shouldBeInstanceOf<ORMTemplate>()
            template.entity(City::class).findAll().size shouldBe 6
        }
    }

    @Test
    fun `ORMTemplate of dataSource with StormConfig should create valid template`() {
        // data.sql inserts 6 cities; default config should not alter behavior.
        val config = StormConfig.defaults()
        val template = ORMTemplate.of(dataSource, config)
        template.shouldNotBe(null)
        template.shouldBeInstanceOf<ORMTemplate>()
        template.entity(City::class).findAll().size shouldBe 6
    }

    // ======================================================================
    // Extension properties
    // ======================================================================

    @Test
    fun `DataSource orm extension should create valid template`() {
        // data.sql inserts 6 cities; verifies extension property produces a working template.
        val template = dataSource.orm
        template.shouldNotBe(null)
        template.shouldBeInstanceOf<ORMTemplate>()
        template.entity(City::class).findAll().size shouldBe 6
    }

    @Test
    fun `Connection orm extension should create valid template`() {
        // data.sql inserts 6 cities; verifies extension property produces a working template.
        dataSource.connection.use { conn ->
            val template = conn.orm
            template.shouldNotBe(null)
            template.shouldBeInstanceOf<ORMTemplate>()
            template.entity(City::class).findAll().size shouldBe 6
        }
    }

    @Test
    fun `DataSource orm extension with decorator should create valid template`() {
        // data.sql inserts 6 cities; identity decorator should not alter behavior.
        val template = dataSource.orm { it }
        template.shouldNotBe(null)
        template.entity(City::class).findAll().size shouldBe 6
    }

    @Test
    fun `DataSource orm extension with StormConfig should create valid template`() {
        // data.sql inserts 6 cities; default config should not alter behavior.
        val config = StormConfig.defaults()
        val template = dataSource.orm(config)
        template.shouldNotBe(null)
        template.entity(City::class).findAll().size shouldBe 6
    }

    @Test
    fun `Connection orm extension with decorator should create valid template`() {
        // data.sql inserts 6 cities; identity decorator should not alter behavior.
        dataSource.connection.use { conn ->
            val template = conn.orm { it }
            template.shouldNotBe(null)
            template.entity(City::class).findAll().size shouldBe 6
        }
    }

    @Test
    fun `Connection orm extension with StormConfig should create valid template`() {
        // data.sql inserts 6 cities; default config should not alter behavior.
        dataSource.connection.use { conn ->
            val config = StormConfig.defaults()
            val template = conn.orm(config)
            template.shouldNotBe(null)
            template.entity(City::class).findAll().size shouldBe 6
        }
    }

    // ======================================================================
    // ORMTemplate.withEntityCallback
    // ======================================================================

    @Test
    fun `withEntityCallback should return new ORMTemplate`() {
        val callback = object : st.orm.EntityCallback<City> {
            override fun beforeInsert(entity: City): City = entity
        }
        val template = orm.withEntityCallback(callback)
        template.shouldNotBe(null)
        template.shouldBeInstanceOf<ORMTemplate>()
    }

    @Test
    fun `withEntityCallbacks should return new ORMTemplate`() {
        val callback = object : st.orm.EntityCallback<City> {
            override fun beforeInsert(entity: City): City = entity
        }
        val template = orm.withEntityCallbacks(listOf(callback))
        template.shouldNotBe(null)
        template.shouldBeInstanceOf<ORMTemplate>()
    }

    // ======================================================================
    // PredicateBuilderFactory tests
    // ======================================================================

    @Test
    fun `create predicate builder should return PredicateBuilder`() {
        val metamodel = Metamodel.of<City, Int>(City::class.java, "id")
        val predicate = st.orm.template.impl.create<City, City, Int>(
            metamodel,
            Operator.IN,
            listOf(1, 2, 3),
        )
        predicate.shouldNotBe(null)
        predicate.shouldBeInstanceOf<PredicateBuilder<*, *, *>>()
    }

    @Test
    fun `createWithId predicate builder should return PredicateBuilder`() {
        val metamodel = Metamodel.of<City, Int>(City::class.java, "id")
        val predicate = st.orm.template.impl.createWithId<City, City, Int, Int>(
            metamodel,
            Operator.IN,
            listOf(1, 2, 3),
        )
        predicate.shouldNotBe(null)
        predicate.shouldBeInstanceOf<PredicateBuilder<*, *, *>>()
    }

    @Test
    fun `predicate builder or composition should return PredicateBuilder`() {
        val metamodel = Metamodel.of<City, Int>(City::class.java, "id")
        val p1 = st.orm.template.impl.create<City, City, Int>(
            metamodel,
            Operator.IN,
            listOf(1, 2),
        )
        val p2 = st.orm.template.impl.create<City, City, Int>(
            metamodel,
            Operator.IN,
            listOf(3, 4),
        )
        val combined = p1 or p2
        combined.shouldNotBe(null)
        combined.shouldBeInstanceOf<PredicateBuilder<*, *, *>>()
    }

    @Test
    fun `predicate builder and composition should return PredicateBuilder`() {
        val metamodel = Metamodel.of<City, Int>(City::class.java, "id")
        val p1 = st.orm.template.impl.create<City, City, Int>(
            metamodel,
            Operator.EQUALS,
            listOf(1),
        )
        val p2 = st.orm.template.impl.create<City, City, Int>(
            metamodel,
            Operator.EQUALS,
            listOf(2),
        )
        val combined = p1 and p2
        combined.shouldNotBe(null)
        combined.shouldBeInstanceOf<PredicateBuilder<*, *, *>>()
    }

    // ======================================================================
    // ColumnImpl tests via Model API
    // ======================================================================

    @Test
    fun `City model columns should not be empty`() {
        val model = orm.entity(City::class).model
        model.columns.shouldNotBeEmpty()
    }

    @Test
    fun `City id column should have correct properties`() {
        // City.id is annotated with @PK, is a non-nullable Int, and has no FK, @Version, or @Ref.
        val model = orm.entity(City::class).model
        val idColumn = model.columns.first { it.name == "id" }
        idColumn.primaryKey shouldBe true
        idColumn.foreignKey shouldBe false
        idColumn.nullable shouldBe false
        idColumn.version shouldBe false
        idColumn.ref shouldBe false
        idColumn.insertable shouldBe true
        idColumn.type shouldBe Int::class
    }

    @Test
    fun `City id column index should be positive`() {
        val model = orm.entity(City::class).model
        val idColumn = model.columns.first { it.name == "id" }
        (idColumn.index >= 1) shouldBe true
    }

    @Test
    fun `City id column generation should be IDENTITY`() {
        // City table uses auto_increment, so generation strategy should be IDENTITY.
        val model = orm.entity(City::class).model
        val idColumn = model.columns.first { it.name == "id" }
        idColumn.generation shouldBe GenerationStrategy.IDENTITY
    }

    @Test
    fun `City id column sequence should be null`() {
        val model = orm.entity(City::class).model
        val idColumn = model.columns.first { it.name == "id" }
        idColumn.sequence shouldBe null
    }

    @Test
    fun `City name column should not be primary key`() {
        val model = orm.entity(City::class).model
        val nameColumn = model.columns.first { it.name == "name" }
        nameColumn.primaryKey shouldBe false
        nameColumn.foreignKey shouldBe false
        nameColumn.version shouldBe false
    }

    @Test
    fun `City column metamodel should not be null`() {
        val model = orm.entity(City::class).model
        val idColumn = model.columns.first { it.name == "id" }
        idColumn.metamodel.shouldNotBe(null)
    }

    @Test
    fun `Owner model should have version column`() {
        // Owner.version is annotated with @Version, so it should be detected as a version column.
        val model = orm.entity(Owner::class).model
        val versionColumn = model.columns.first { it.name == "version" }
        versionColumn.version shouldBe true
        versionColumn.primaryKey shouldBe false
    }

    @Test
    fun `Owner model should have foreign key column for city`() {
        // Owner.address.city is annotated with @FK and is nullable (City? = null).
        val model = orm.entity(Owner::class).model
        val cityIdColumn = model.columns.first { it.name == "city_id" }
        cityIdColumn.foreignKey shouldBe true
        cityIdColumn.nullable shouldBe true
    }

    @Test
    fun `Pet model should have non-updatable birthDate column`() {
        // Pet.birthDate is annotated with @Persist(updatable = false).
        val model = orm.entity(Pet::class).model
        val birthDateColumn = model.columns.first { it.name == "birth_date" }
        birthDateColumn.updatable shouldBe false
    }

    @Test
    fun `Pet model should have non-updatable type column`() {
        // Pet.type is annotated with @FK @Persist(updatable = false).
        val model = orm.entity(Pet::class).model
        val typeColumn = model.columns.first { it.name == "type_id" }
        typeColumn.updatable shouldBe false
        typeColumn.foreignKey shouldBe true
    }

    @Test
    fun `Owner model columns count should include expanded FK columns`() {
        val model = orm.entity(Owner::class).model
        // Owner has: id, first_name, last_name, address, city_id, city_name (expanded FK), telephone, version
        model.columns.size shouldBe 8
    }

    @Test
    fun `Model name for City should be city`() {
        val model = orm.entity(City::class).model
        model.name shouldBe "city"
    }

    @Test
    fun `Model type for City should be City`() {
        val model = orm.entity(City::class).model
        model.type shouldBe City::class
    }

    @Test
    fun `Model primaryKeyType for City should be Int`() {
        val model = orm.entity(City::class).model
        model.primaryKeyType shouldBe Int::class
    }

    @Test
    fun `Model isDefaultPrimaryKey should return true for zero`() {
        val model = orm.entity(City::class).model
        model.isDefaultPrimaryKey(0) shouldBe true
    }

    @Test
    fun `Model isDefaultPrimaryKey should return false for non-zero`() {
        val model = orm.entity(City::class).model
        model.isDefaultPrimaryKey(1) shouldBe false
    }

    @Test
    fun `Model declaredColumns should not include expanded FK columns`() {
        val model = orm.entity(Owner::class).model
        // Declared: id, firstName, lastName, address (inline), city (FK via address), telephone, version
        model.declaredColumns.size shouldBe 7
    }

    @Test
    fun `Model forEachValue should extract column values`() {
        val model = orm.entity(City::class).model
        val city = City(id = 1, name = "Test")
        val values = mutableMapOf<String, Any?>()
        model.forEachValue(model.columns, city) { column, value ->
            values[column.name] = value
        }
        values["id"] shouldBe 1
        values["name"] shouldBe "Test"
    }

    @Test
    fun `Model values should return map of column values`() {
        val model = orm.entity(City::class).model
        val city = City(id = 1, name = "Test")
        val values = model.values(city)
        values.size shouldBe 2
    }

    // ======================================================================
    // ORMTemplateImpl proxy / repository tests
    // ======================================================================

    @Test
    fun `entity repository should return EntityRepository for City`() {
        // data.sql inserts 6 cities (ids 1-6).
        val repo = orm.entity(City::class)
        repo.shouldNotBe(null)
        repo.findAll().size shouldBe 6
    }

    @Test
    fun `entity repository model should provide correct metadata`() {
        val repo = orm.entity(City::class)
        repo.model.name shouldBe "city"
        repo.model.type shouldBe City::class
    }

    @Test
    fun `repository proxy should support custom EntityRepository interface`() {
        // data.sql inserts 6 cities; verifies proxy dispatches to the correct implementation.
        val repo = orm.repository(CityRepository::class)
        repo.shouldNotBe(null)
        repo.findAll().size shouldBe 6
    }

    @Test
    fun `repository proxy toString should include type name`() {
        val repo = orm.repository(CityRepository::class)
        repo.toString() shouldBe "RepositoryProxy(CityRepository)"
    }

    @Test
    fun `repository proxy hashCode should not throw`() {
        val repo = orm.repository(CityRepository::class)
        val hash = repo.hashCode()
        hash.shouldNotBe(null)
    }

    @Test
    fun `repository proxy equals should use identity`() {
        val repo = orm.repository(CityRepository::class)
        (repo == repo) shouldBe true
    }

    @Test
    fun `repository proxy equals should return false for different proxy`() {
        val repo1 = orm.repository(CityRepository::class)
        val repo2 = orm.repository(CityRepository::class)
        (repo1 === repo2) shouldBe false
    }

    @Test
    fun `repository proxy orm property should return ORMTemplate`() {
        val repo = orm.repository(CityRepository::class)
        repo.orm.shouldNotBe(null)
        repo.orm.shouldBeInstanceOf<ORMTemplate>()
    }

    @Test
    fun `withEntityCallback should produce functional template`() {
        var intercepted = false
        val callback = object : st.orm.EntityCallback<City> {
            override fun beforeInsert(entity: City): City {
                intercepted = true
                return entity
            }
        }
        val template = orm.withEntityCallback(callback)
        val inserted = template.entity(City::class).insert(City(name = "CallbackCity"))
        inserted.shouldNotBe(null)
        intercepted shouldBe true
    }

    @Test
    fun `withEntityCallbacks with empty list should produce functional template`() {
        // data.sql inserts 6 cities; empty callback list should not alter behavior.
        val template = orm.withEntityCallbacks(emptyList())
        template.shouldNotBe(null)
        template.entity(City::class).findAll().size shouldBe 6
    }

    // ======================================================================
    // TransactionDispatchers tests
    // ======================================================================

    @Test
    fun `TransactionDispatchers Default should return a dispatcher`() {
        val dispatcher = TransactionDispatchers.Default
        dispatcher.shouldNotBe(null)
    }

    @Test
    fun `TransactionDispatchers Virtual should return VirtualThreadDispatcher`() {
        val dispatcher = TransactionDispatchers.Virtual
        dispatcher.shouldNotBe(null)
        dispatcher.shouldBeInstanceOf<st.orm.template.impl.VirtualThreadDispatcher>()
    }

    @Test
    fun `TransactionDispatchers Default assignment should override default`() {
        val original = TransactionDispatchers.Default
        try {
            val custom = kotlinx.coroutines.Dispatchers.Unconfined
            TransactionDispatchers.Default = custom
            TransactionDispatchers.Default shouldBe custom
        } finally {
            // Restore to trigger resolveDefault again.
            TransactionDispatchers.Default = original
        }
    }

    @Test
    fun `TransactionDispatchers shutdown should not throw`() {
        // Calling shutdown is safe even if Virtual was already created.
        TransactionDispatchers.shutdown()
    }
}

/**
 * Custom repository interface for testing ORMTemplateImpl proxy creation.
 */
interface CityRepository : st.orm.repository.EntityRepository<City, Int>
