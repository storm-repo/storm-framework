package st.orm.kotlin.template

import jakarta.persistence.EntityManager
import st.orm.kotlin.KTemplates
import st.orm.kotlin.template.KORMTemplate

/**
 * Extension property to convert an [EntityManager] to a [KORMTemplate].
 */
val EntityManager.orm: KORMTemplate
    get() = KTemplates.ORM(this)