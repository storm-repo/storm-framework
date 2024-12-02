package st.orm.spring;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

@Component
public class ResolverRegistration {

    private final ConfigurableListableBeanFactory beanFactory;

    public ResolverRegistration(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }
}
