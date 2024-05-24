package st.orm.kotlin.repository

import st.orm.kotlin.model.KotlinPet

interface KotlinPetRepository : KEntityRepository<KotlinPet, Int> {

    fun findAll(): List<KotlinPet> = template().template {
        with(it) {
            """
            SELECT ${arg(KotlinPet::class)}
            FROM ${arg(KotlinPet::class)}
            """.trimIndent()
        }
    }.getResultList(KotlinPet::class)
}