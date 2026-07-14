package st.orm.spring;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;
import org.springframework.aot.generate.GeneratedFiles.Kind;
import org.springframework.aot.test.generate.TestGenerationContext;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.context.support.GenericApplicationContext;

/**
 * Verifies that the repository bean definitions registered by the scanning post-processor can be processed
 * by Spring AOT into generated code. Instance-supplier definitions fail this processing outright, so these
 * tests guard the FactoryBean-based registration that native images depend on.
 */
public class RepositoryScanningAotTest {

    private static GenericApplicationContext contextWithScanner(String repositoryPrefix) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBeanDefinition("repositoryScanner", BeanDefinitionBuilder
                .genericBeanDefinition(RepositoryBeanFactoryPostProcessor.class)
                .addConstructorArgValue(new String[] { "st.orm.spring.repository" })
                .addConstructorArgValue(null)
                .addConstructorArgValue(repositoryPrefix)
                .getBeanDefinition());
        return context;
    }

    private static String processAheadOfTime(GenericApplicationContext context) {
        TestGenerationContext generationContext = new TestGenerationContext();
        new ApplicationContextAotGenerator().processAheadOfTime(context, generationContext);
        generationContext.writeGeneratedContent();
        StringBuilder allSources = new StringBuilder();
        generationContext.getGeneratedFiles().getGeneratedFiles(Kind.SOURCE).forEach((path, content) -> {
            try (InputStream in = content.getInputStream()) {
                allSources.append(new String(in.readAllBytes(), UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return allSources.toString();
    }

    @Test
    public void testAotProcessingGeneratesRepositoryRegistrations() {
        String generatedSources = processAheadOfTime(contextWithScanner(""));
        assertTrue(generatedSources.contains("VisitRepository"),
                "The scanned repositories must be present in the AOT-generated registrations");
        assertTrue(generatedSources.contains("RepositoryFactoryBean"),
                "The repositories must be produced through the FactoryBean in generated code");
        assertFalse(generatedSources.contains("OwnerRepository"),
                "@NoRepositoryBean interfaces must stay excluded from the AOT-generated registrations");
    }

    @Test
    public void testRepositoryQualifierSurvivesAotProcessing() {
        String generatedSources = processAheadOfTime(contextWithScanner("acme"));
        assertTrue(generatedSources.contains("AutowireCandidateQualifier"),
                "The repository qualifier must be carried into the AOT-generated definitions");
        assertTrue(generatedSources.contains("\"acme\""),
                "The configured qualifier value must be carried into the AOT-generated definitions");
    }
}
