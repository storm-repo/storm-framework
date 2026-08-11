package st.orm.serialization.model

import kotlinx.serialization.Serializable

@Serializable
data class VetSpecialtyPK(
    val vetId: Int,
    val specialtyId: Int,
)
