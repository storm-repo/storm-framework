package st.orm.core.template;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

/**
 * Provides support for Java-style string templates without preview features.
 *
 * <p>Use {@code it.insert(x)} to inject values. Delimiters are real NULLs ({@code '\0'}), and
 * if you write {@code "\\0"} in your literal Java string, it becomes a genuine NULL in the output.
 */
@FunctionalInterface
public interface TemplateBuilder {

    /**
     * Builds a {@link TemplateString} by running the template function.
     *
     * <p>Interpolation insertions become NULLs. Any literal {@code "\\0"} in your code
     * is parsed as a real NULL, not as a delimiter.</p>
     *
     * @param builder your template-building lambda; call {@code context.insert(x)} to register parameters.
     * @return a {@link TemplateString} with fragments and values.
     */
    static TemplateString create(@Nonnull TemplateBuilder builder) {
        List<Object> values = new ArrayList<>();
        String raw = builder.interpolate(o -> {
            values.add(o);
            return "\0";
        });
        List<String> fragments = parseFragments(raw);
        return new TemplateString(fragments, values);
    }

    /**
     * Builds a {@link TemplateString} from a raw template and values.
     *
     * <p>Use {@code "\\0"} here to get a genuine NULL in your fragments.</p>
     *
     * @param template the literal template containing (possibly escaped) NUL sequences.
     * @param values   parameters to inject.
     * @return a {@link TemplateString} with fragments and values.
     */
    static TemplateString create(@Nonnull String template, @Nonnull Object... values) {
        return new TemplateString(parseFragments(template), asList(values));
    }

    /**
     * Parses the given string into template fragments, splitting on unescaped NULs (\0)
     * and turning "\\0" into a real NUL within fragments.
     *
     * @param raw the raw string with '\0' delimiters and '\\0' escapes.
     * @return a List of fragments between each NUL delimiter.
     */
    private static List<String> parseFragments(String raw) {
        List<String> fragments = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length() && raw.charAt(i + 1) == '0') {
                // Escaped null sequence.
                cur.append('\0');
                i++;
            } else if (c == '\0') {
                // Delimiter: end fragment.
                fragments.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fragments.add(cur.toString());
        return fragments;
    }

    /**
     * Context for injecting arguments into the template.
     */
    @FunctionalInterface
    interface Context {
        /**
         * Insert an object: returns the intermediary string (always replaced by '\0') after registering.
         * @param o the object to inject
         * @return a single-character string containing '\0'
         */
        String insert(@Nonnull Object o);
    }

    /**
     * Produces the raw string (with '\0' placeholders) from your template lambda.
     *
     * @param context callback used within your lambda to insert values.
     * @return intermediary string containing zero or more '\0' delimiters.
     */
    String interpolate(@Nonnull Context context);
}
