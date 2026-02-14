package st.orm.spring;

import org.springframework.context.annotation.Configuration;

/**
 * BeanFactoryPostProcessor that scans base packages for Repository interfaces and registers them as beans.
 */
@Configuration
public class TestRepositoryBeanFactoryPostProcessor extends RepositoryBeanFactoryPostProcessor {
    @Override
    public String getOrmTemplateBeanName() {
        return "ormTemplate";
    }

    @Override
    public String[] getRepositoryBasePackages() {
        return new String[] { "st.orm.spring.repository" };
    }
}
