package st.orm.serialization.model

import kotlinx.serialization.Serializable

@Serializable
internal data class VetSpecialtyPK(
    val vetId: Int,
    val specialtyId: Int,
)
