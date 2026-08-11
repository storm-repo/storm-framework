package st.orm.spring

import org.springframework.context.annotation.Configuration
import st.orm.spring.kotlin.RepositoryBeanFactoryPostProcessor

@Configuration
internal open class TestRepositoryBeanFactoryPostProcessor : RepositoryBeanFactoryPostProcessor() {
    override fun getOrmTemplateBeanName(): String = "ormTemplate"

    // Make platform wide repositories available as well in the context of the dataORMTemplate.
    override fun getRepositoryBasePackages(): Array<String> = arrayOf("st.orm.spring.repository")
}
