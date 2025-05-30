package st.orm.kotline.template

import st.orm.kotlin.template.KORMTemplate
import st.orm.template.JpaTemplate

/**
 * Extension property to convert a [JpaTemplate] to a [KORMTemplate].
 */
val JpaTemplate.orm: KORMTemplate
    get() = KORMTemplate.from(this.toORM())