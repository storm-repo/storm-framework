package st.orm.core.model.polymorphic;

import st.orm.Data;

/**
 * Polymorphic FK.
 * Sealed Data interface (NOT Entity). Subtypes are independent entities.
 */
public sealed interface Commentable extends Data permits Post, Photo {
}
