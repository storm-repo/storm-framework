package st.orm.spring

import org.springframework.context.annotation.Configuration

@Configuration
open class TestRepositoryBeanFactoryPostProcessor : RepositoryBeanFactoryPostProcessor() {
    override val ormTemplateBeanName: String get() = "ormTemplate"

    // Make platform wide repositories available as well in the context of the dataORMTemplate.
    override val repositoryBasePackages: Array<String> get() = arrayOf("st.orm.spring.repository")
}
