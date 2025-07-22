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
package st.orm.kt.spring

import org.springframework.aop.framework.ProxyFactory
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.stereotype.Component
import st.orm.kt.repository.Repository
import java.util.*
import java.util.Set


@Component
class RepositoryProxyingPostProcessor : BeanPostProcessor {
    private val repositoryInterfaces: MutableSet<Class<*>?> = Set.of<Class<*>?>(Repository::class.java)

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any? {
        if (Arrays.stream<Class<*>>(bean.javaClass.getInterfaces())
                .anyMatch { o: Class<*>? -> repositoryInterfaces.contains(o) }
        ) {
            val factory = ProxyFactory(bean)
            factory.setProxyTargetClass(true)
            return factory.getProxy()
        }
        return bean
    }
}