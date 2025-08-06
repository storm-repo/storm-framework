/*
 * Copyright 2024 - 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package st.orm.spring.impl;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

/**
 * Registers a resolver bean in the Spring application context.
 */
@Component
public class ResolverRegistration {

    private final ConfigurableListableBeanFactory beanFactory;

    /**
     * Creates a new {@link ResolverRegistration} instance.
     *
     * @param beanFactory the bean factory.
     */
    public ResolverRegistration(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }
}
