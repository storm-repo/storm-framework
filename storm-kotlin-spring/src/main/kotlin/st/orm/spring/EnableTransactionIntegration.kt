package st.orm.spring

import org.springframework.context.annotation.Import
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Enables Spring transaction integration for Storm.
 *
 * Use this annotation to use Storm's programmatic transactions in combination with Spring's transaction management.
 * Note that suspend transactions are not supported.
 */
@Target(CLASS)
@Retention(RUNTIME)
@Import(SpringTransactionConfiguration::class)
annotation class EnableTransactionIntegration