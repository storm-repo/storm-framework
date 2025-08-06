package st.orm.kt.spring

import jakarta.annotation.PostConstruct
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.transaction.PlatformTransactionManager

/**
 * A Spring configuration class that provides access to the transaction managers configured in the Spring context.
 *
 * @since 1.5
 */
@AutoConfiguration
open class SpringTransactionConfiguration(val transactionManagers: List<PlatformTransactionManager>) {

    @PostConstruct
    fun test() {
        configured = transactionManagers
    }

    companion object {
        @Volatile
        private var configured: List<PlatformTransactionManager>? = null


        val transactionManagers: List<PlatformTransactionManager>
            get() = configured ?: emptyList()
    }
}