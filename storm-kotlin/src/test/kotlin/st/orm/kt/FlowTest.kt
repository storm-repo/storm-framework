package st.orm.kt

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.kt.model.Visit
import st.orm.kt.repository.selectAll
import st.orm.kt.template.ORMTemplate
import st.orm.kt.template.suspendTransaction

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@Sql("/data.sql")
open class FlowTest(
    @Autowired val orm: ORMTemplate
) {

    @Test
    fun `selectAll returns all visits`(): Unit = runBlocking {
        orm.selectAll<Visit>().count() shouldBe 14
    }

    @Test
    fun `selectByRef returns all visits when given all refs`(): Unit = runBlocking {
        val repository = orm.entity(Visit::class)
        val refs = repository.selectAllRef()
        repository.selectByRef(refs).count() shouldBe 14
    }

    @Test
    fun `delete removes all visits`(): Unit = runBlocking {
        val repository = orm.entity(Visit::class)
        val entities = repository.selectAll()
        repository.delete(entities)
        repository.count() shouldBe 0
    }

    @Test
    fun `selectAll with suspend transaction returns all visits`(): Unit = runBlocking {
        suspendTransaction {
            orm.selectAll<Visit>().count() shouldBe 14
        }
    }

    @Test
    fun `selectByRef with suspend transaction returns all visits when given all refs`(): Unit = runBlocking {
        suspendTransaction {
            val repository = orm.entity(Visit::class)
            val refs = repository.selectAllRef()
            repository.selectByRef(refs).count() shouldBe 14
        }
    }

    @Test
    fun `delete with suspend transaction removes all visits`(): Unit = runBlocking {
        suspendTransaction {
            val repository = orm.entity(Visit::class)
            val entities = repository.selectAll()
            repository.delete(entities)
            repository.count() shouldBe 0
        }
    }
}