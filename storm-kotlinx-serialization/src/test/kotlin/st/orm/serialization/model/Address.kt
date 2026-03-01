package st.orm.serialization.model

import kotlinx.serialization.Serializable

/**
 * Simple business object representing an address.
 */
@Serializable
data class Address(
    val address: String? = null,
    val city: String? = null,
)
