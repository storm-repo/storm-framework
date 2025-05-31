package st.orm.kotlin.template

import st.orm.kotlin.KTemplates
import st.orm.kotlin.template.KORMTemplate
import javax.sql.DataSource

/**
 * Extension property to convert a [DataSource] to a [KORMTemplate].
 */
val DataSource.orm: KORMTemplate
    get() = KTemplates.ORM(this)