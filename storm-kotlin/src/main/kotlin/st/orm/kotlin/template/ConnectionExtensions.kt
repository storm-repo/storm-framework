package st.orm.kotlin.template

import st.orm.kotlin.KTemplates
import st.orm.kotlin.template.KORMTemplate
import java.sql.Connection

/**
 * Extension property to convert a [Connection] to a [KORMTemplate].
 */
val Connection.orm: KORMTemplate
    get() = KTemplates.ORM(this)