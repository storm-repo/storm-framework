package st.orm.kotlin.repository

import st.orm.kotlin.model.KotlinPet

interface KotlinPetRepository : KEntityRepository<KotlinPet, Int> {

    fun findAll(): List<KotlinPet> = template().query {
        """
        SELECT ${it(KotlinPet::class)}
        FROM ${it(KotlinPet::class)}
        """.trimIndent()
    }.getResultList(KotlinPet::class)
}