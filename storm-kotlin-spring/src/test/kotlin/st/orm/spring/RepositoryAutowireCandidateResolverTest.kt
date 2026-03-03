package st.orm.spring

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.config.DependencyDescriptor
import org.springframework.beans.factory.support.AutowireCandidateResolver

/**
 * Unit tests for [RepositoryBeanFactoryPostProcessor.RepositoryAutowireCandidateResolver]
 * delegation methods that are not exercised through integration tests.
 */
class RepositoryAutowireCandidateResolverTest {

    private val delegate = mock(AutowireCandidateResolver::class.java)
    private val resolver = RepositoryBeanFactoryPostProcessor.RepositoryAutowireCandidateResolver(delegate)

    @Test
    fun `hasQualifier should delegate to underlying resolver`() {
        val descriptor = mock(DependencyDescriptor::class.java)
        `when`(delegate.hasQualifier(descriptor)).thenReturn(true)
        resolver.hasQualifier(descriptor).shouldBeTrue()
        verify(delegate).hasQualifier(descriptor)
    }

    @Test
    fun `hasQualifier should return false when delegate returns false`() {
        val descriptor = mock(DependencyDescriptor::class.java)
        `when`(delegate.hasQualifier(descriptor)).thenReturn(false)
        resolver.hasQualifier(descriptor).shouldBeFalse()
    }

    @Test
    fun `cloneIfNecessary should delegate to underlying resolver`() {
        val clonedResolver = mock(AutowireCandidateResolver::class.java)
        `when`(delegate.cloneIfNecessary()).thenReturn(clonedResolver)
        resolver.cloneIfNecessary() shouldBe clonedResolver
        verify(delegate).cloneIfNecessary()
    }

    @Test
    fun `getLazyResolutionProxyClass should delegate to underlying resolver`() {
        val descriptor = mock(DependencyDescriptor::class.java)
        `when`(delegate.getLazyResolutionProxyClass(descriptor, "testBean")).thenReturn(null)
        resolver.getLazyResolutionProxyClass(descriptor, "testBean").shouldBeNull()
        verify(delegate).getLazyResolutionProxyClass(descriptor, "testBean")
    }
}
