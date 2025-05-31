package st.orm.kotlin.template

import st.orm.kotlin.template.KORMTemplate
import st.orm.template.PreparedStatementTemplate

/**
 * Extension property to convert a [PreparedStatementTemplate] to a [KORMTemplate].
 */
val PreparedStatementTemplate.orm: KORMTemplate
    get() = KORMTemplate.from(this.toORM())