package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import st.orm.Element;
import st.orm.core.template.SqlTemplateException;

/**
 * Process an element of a template.
 *
 * @param <T> the type of the element.
 */
interface ElementProcessor<T extends Element> {

    /**
     * Process an element of a template.
     *
     * @param element the element to process.
     * @return the result of processing the element.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    ElementResult process(@Nonnull T element) throws SqlTemplateException;
}
