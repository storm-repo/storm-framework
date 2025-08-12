package st.orm.template.model

import st.orm.FK

/**
 * Simple business object representing an address.
 */
@JvmRecord
data class Address(
    val address: String? = null,
    @FK val city: City? = null
)
