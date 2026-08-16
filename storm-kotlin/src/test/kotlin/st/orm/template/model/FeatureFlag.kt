package st.orm.template.model

import st.orm.Entity
import st.orm.PK

internal data class FeatureFlag(
    @PK val id: Int = 0,
    val name: String,
    val enabled: Boolean?,
) : Entity<Int>
