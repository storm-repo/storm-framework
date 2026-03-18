/*
 * Copyright 2024 - 2026 the original author or authors.
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
package st.orm.core.spi;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.UUID;

/**
 * Factory for cursor serialization and deserialization. This class is called reflectively from
 * {@code st.orm.CursorHelper} in storm-foundation.
 *
 * <p>The registry is built once from the built-in codecs plus any {@link CursorCodecProvider} implementations
 * discovered via {@link ServiceLoader}.</p>
 */
public final class CursorFactory {

    private static final int CURSOR_VERSION = 1;

    private static final byte TYPE_NULL = 0;

    private record Entry(byte tag, @Nonnull Class<?> type, @Nonnull CursorCodec<?> codec) {}

    private static final Map<Class<?>, Entry> BY_CLASS;
    private static final Map<Byte, Entry> BY_TAG;
    private static final int REGISTRY_FINGERPRINT;

    static {
        Map<Class<?>, Entry> byClass = new LinkedHashMap<>();
        Map<Byte, Entry> byTag = new LinkedHashMap<>();

        // Built-in codecs (tags 1-63 reserved).
        register(byClass, byTag, (byte) 1, String.class, new CursorCodec<>() {
            @Override public void write(@Nonnull DataOutputStream out, @Nonnull String v) throws IOException { writeString(out, v); }
            @Override public String read(@Nonnull DataInputStream in) throws IOException { return readString(in); }
        });
        register(byClass, byTag, (byte) 2, Integer.class, new CursorCodec<>() {
            @Override public void write(@Nonnull DataOutputStream out, @Nonnull Integer v) throws IOException { out.writeInt(v); }
            @Override public Integer read(@Nonnull DataInputStream in) throws IOException { return in.readInt(); }
        });
        register(byClass, byTag, (byte) 3, Long.class, new CursorCodec<>() {
            @Override public void write(@Nonnull DataOutputStream out, @Nonnull Long v) throws IOException { out.writeLong(v); }
            @Override public Long read(@Nonnull DataInputStream in) throws IOException { return in.readLong(); }
        });
        register(byClass, byTag, (byte) 4, Boolean.class, new CursorCodec<>() {
            @Override public void write(@Nonnull DataOutputStream out, @Nonnull Boolean v) throws IOException { out.writeBoolean(v); }
            @Override public Boolean read(@Nonnull DataInputStream in) throws IOException { return in.readBoolean(); }
        });
        register(byClass, byTag, (byte) 5, UUID.class, new CursorCodec<>() {
            @Override public void write(@Nonnull DataOutputStream out, @Nonnull UUID v) throws IOException {
                out.writeLong(v.getMostSignificantBits()); out.writeLong(v.getLeastSignificantBits());
            }
            @Override public UUID read(@Nonnull DataInputStream in) throws IOException {
                return new UUID(in.readLong(), in.readLong());
            }
        });
        register(byClass, byTag, (byte) 6, Instant.class, new CursorCodec<>() {
            @Override public void write(@Nonnull DataOutputStream out, @Nonnull Instant v) throws IOException {
                out.writeLong(v.getEpochSecond()); out.writeInt(v.getNano());
            }
            @Override public Instant read(@Nonnull DataInputStream in) throws IOException {
                return Instant.ofEpochSecond(in.readLong(), in.readInt());
            }
        });
        register(byClass, byTag, (byte) 7, LocalDate.class, new CursorCodec<>() {
            @Override public void write(@Nonnull DataOutputStream out, @Nonnull LocalDate v) throws IOException {
                out.writeInt(v.getYear()); out.writeByte(v.getMonthValue()); out.writeByte(v.getDayOfMonth());
            }
            @Override public LocalDate read(@Nonnull DataInputStream in) throws IOException {
                return LocalDate.of(in.readInt(), in.readUnsignedByte(), in.readUnsignedByte());
            }
        });
        register(byClass, byTag, (byte) 8, LocalDateTime.class, new CursorCodec<>() {
            @Override public void write(@Nonnull DataOutputStream out, @Nonnull LocalDateTime v) throws IOException {
                out.writeInt(v.getYear()); out.writeByte(v.getMonthValue()); out.writeByte(v.getDayOfMonth());
                out.writeByte(v.getHour()); out.writeByte(v.getMinute()); out.writeByte(v.getSecond());
                out.writeInt(v.getNano());
            }
            @Override public LocalDateTime read(@Nonnull DataInputStream in) throws IOException {
                return LocalDateTime.of(in.readInt(), in.readUnsignedByte(), in.readUnsignedByte(),
                        in.readUnsignedByte(), in.readUnsignedByte(), in.readUnsignedByte(), in.readInt());
            }
        });
        register(byClass, byTag, (byte) 9, BigDecimal.class, new CursorCodec<>() {
            @Override public void write(@Nonnull DataOutputStream out, @Nonnull BigDecimal v) throws IOException {
                writeString(out, v.toPlainString());
            }
            @Override public BigDecimal read(@Nonnull DataInputStream in) throws IOException {
                return new BigDecimal(readString(in));
            }
        });
        register(byClass, byTag, (byte) 10, Short.class, new CursorCodec<>() {
            @Override public void write(@Nonnull DataOutputStream out, @Nonnull Short v) throws IOException { out.writeShort(v); }
            @Override public Short read(@Nonnull DataInputStream in) throws IOException { return in.readShort(); }
        });
        register(byClass, byTag, (byte) 11, Byte.class, new CursorCodec<>() {
            @Override public void write(@Nonnull DataOutputStream out, @Nonnull Byte v) throws IOException { out.writeByte(v); }
            @Override public Byte read(@Nonnull DataInputStream in) throws IOException { return in.readByte(); }
        });
        register(byClass, byTag, (byte) 12, OffsetDateTime.class, new CursorCodec<>() {
            @Override public void write(@Nonnull DataOutputStream out, @Nonnull OffsetDateTime v) throws IOException {
                LocalDateTime l = v.toLocalDateTime();
                out.writeInt(l.getYear()); out.writeByte(l.getMonthValue()); out.writeByte(l.getDayOfMonth());
                out.writeByte(l.getHour()); out.writeByte(l.getMinute()); out.writeByte(l.getSecond());
                out.writeInt(l.getNano()); out.writeInt(v.getOffset().getTotalSeconds());
            }
            @Override public OffsetDateTime read(@Nonnull DataInputStream in) throws IOException {
                return OffsetDateTime.of(LocalDateTime.of(in.readInt(), in.readUnsignedByte(), in.readUnsignedByte(),
                        in.readUnsignedByte(), in.readUnsignedByte(), in.readUnsignedByte(), in.readInt()),
                        ZoneOffset.ofTotalSeconds(in.readInt()));
            }
        });

        // SPI-discovered codecs.
        for (CursorCodecProvider provider : ServiceLoader.load(CursorCodecProvider.class)) {
            for (CursorCodecEntry<?> entry : provider.codecs()) {
                int tag = entry.tag();
                if (tag < 64 || tag > 255) {
                    throw new IllegalArgumentException(
                            "Custom codec tags must be in range [64, 255], got: " + tag + ".");
                }
                byte byteTag = (byte) tag;
                if (byTag.containsKey(byteTag)) {
                    throw new IllegalArgumentException("Cursor codec tag " + tag + " is already registered.");
                }
                if (byClass.containsKey(entry.type())) {
                    throw new IllegalArgumentException(
                            "Cursor codec for type " + entry.type().getName() + " is already registered.");
                }
                registerEntry(byClass, byTag, byteTag, entry);
            }
        }

        BY_CLASS = Map.copyOf(byClass);
        BY_TAG = Map.copyOf(byTag);
        REGISTRY_FINGERPRINT = computeFingerprint(BY_TAG);
    }

    private CursorFactory() {}

    /**
     * Serializes cursor values into a Base64 URL-safe string.
     *
     * @param metamodelFingerprint the metamodel fingerprint (key/sort paths).
     * @param isForward the scroll direction.
     * @param size the page size.
     * @param keyCursor the key cursor value, or null.
     * @param sortCursor the sort cursor value, or null.
     * @return the encoded cursor string.
     */
    public static String toCursor(int metamodelFingerprint, boolean isForward, int size,
                                   @Nullable Object keyCursor, @Nullable Object sortCursor) {
        try (var byteStream = new ByteArrayOutputStream();
             var dataStream = new DataOutputStream(byteStream)) {
            dataStream.writeByte(CURSOR_VERSION);
            dataStream.writeInt(metamodelFingerprint);
            dataStream.writeInt(REGISTRY_FINGERPRINT);
            dataStream.writeBoolean(isForward);
            dataStream.writeInt(size);
            writeValue(dataStream, keyCursor);
            writeValue(dataStream, sortCursor);
            dataStream.flush();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(byteStream.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize cursor.", e);
        }
    }

    /**
     * Deserializes a cursor string. Returns an Object array: {isForward, size, keyCursor, sortCursor}.
     *
     * @param metamodelFingerprint the expected metamodel fingerprint.
     * @param cursor the cursor string.
     * @param keyFieldType the expected key field type (for validation), or null to skip.
     * @param sortFieldType the expected sort field type (for validation), or null to skip.
     * @return Object[] {Boolean isForward, Integer size, Object keyCursor, Object sortCursor}.
     */
    public static Object[] fromCursor(int metamodelFingerprint, @Nonnull String cursor,
                                       @Nullable Class<?> keyFieldType, @Nullable Class<?> sortFieldType) {
        try (var byteStream = new ByteArrayInputStream(Base64.getUrlDecoder().decode(cursor));
             var dataStream = new DataInputStream(byteStream)) {
            int version = dataStream.readUnsignedByte();
            if (version != CURSOR_VERSION) {
                throw new IllegalArgumentException("Unsupported cursor version: " + version + ".");
            }
            int actualMetamodelFingerprint = dataStream.readInt();
            if (metamodelFingerprint != actualMetamodelFingerprint) {
                throw new IllegalArgumentException("Cursor does not match the requested key/sort definition.");
            }
            int actualRegistryFingerprint = dataStream.readInt();
            if (REGISTRY_FINGERPRINT != actualRegistryFingerprint) {
                throw new IllegalArgumentException(
                        "Cursor was produced with a different codec registry configuration.");
            }
            boolean isForward = dataStream.readBoolean();
            int size = dataStream.readInt();
            Object keyCursor = readValue(dataStream);
            Object sortCursor = readValue(dataStream);
            if (dataStream.read() != -1) {
                throw new IllegalArgumentException("Invalid cursor: trailing bytes found.");
            }
            // Validate decoded value types against metamodel.
            if (keyCursor != null && keyFieldType != null) {
                validateType("keyCursor", keyCursor, keyFieldType);
            }
            if (sortCursor != null && sortFieldType != null) {
                validateType("sortCursor", sortCursor, sortFieldType);
            }
            return new Object[] { isForward, size, keyCursor, sortCursor };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Invalid cursor.", e);
        }
    }

    private static void validateType(@Nonnull String label, @Nonnull Object value, @Nonnull Class<?> expectedType) {
        Class<?> boxed = box(expectedType);
        // Object.class means "accept any type" (used in tests or untyped metamodels).
        if (boxed != Object.class && !boxed.isInstance(value)) {
            throw new IllegalArgumentException(
                    "Invalid cursor: " + label + " has type " + value.getClass().getName()
                            + " but metamodel expects " + boxed.getName() + ".");
        }
    }

    private static Class<?> box(@Nonnull Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(@Nonnull DataOutputStream out, @Nullable Object value) throws IOException {
        if (value == null) {
            out.writeByte(TYPE_NULL);
            return;
        }
        Entry entry = BY_CLASS.get(value.getClass());
        if (entry == null) {
            throw new IllegalStateException(
                    "Unsupported cursor value type: " + value.getClass().getName()
                            + ". Register a CursorCodec via CursorCodecProvider SPI.");
        }
        out.writeByte(entry.tag);
        ((CursorCodec<Object>) entry.codec).write(out, value);
    }

    @Nullable
    private static Object readValue(@Nonnull DataInputStream in) throws IOException {
        int tag = in.readUnsignedByte();
        if (tag == TYPE_NULL) {
            return null;
        }
        Entry entry = BY_TAG.get((byte) tag);
        if (entry == null) {
            throw new IOException("Unknown cursor value type tag: " + tag + ".");
        }
        return entry.codec.read(in);
    }

    private static <T> void register(@Nonnull Map<Class<?>, Entry> byClass, @Nonnull Map<Byte, Entry> byTag,
                                      byte tag, @Nonnull Class<T> type, @Nonnull CursorCodec<T> codec) {
        Entry entry = new Entry(tag, type, codec);
        byClass.put(type, entry);
        byTag.put(tag, entry);
    }

    @SuppressWarnings("unchecked")
    private static <T> void registerEntry(Map<Class<?>, Entry> byClass, Map<Byte, Entry> byTag,
                                           byte tag, CursorCodecEntry<?> codecEntry) {
        register(byClass, byTag, tag, (Class<T>) codecEntry.type(), (CursorCodec<T>) codecEntry.codec());
    }

    @SuppressWarnings("SameParameterValue")
    private static int computeFingerprint(@Nonnull Map<Byte, Entry> byTag) {
        int hash = 0;
        boolean first = true;
        for (var entry : byTag.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            if (!first) {
                hash = 31 * hash + ';';
            }
            first = false;
            // Hash the tag number, colon, and fully qualified type name as a contiguous character sequence,
            // matching the output of String.hashCode().
            for (char c : Integer.toString(Byte.toUnsignedInt(entry.getKey())).toCharArray()) {
                hash = 31 * hash + c;
            }
            hash = 31 * hash + ':';
            for (char c : entry.getValue().type.getName().toCharArray()) {
                hash = 31 * hash + c;
            }
        }
        return hash;
    }

    static void writeString(@Nonnull DataOutputStream out, @Nonnull String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    static String readString(@Nonnull DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0) {
            throw new IOException("Negative string length.");
        }
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Unexpected end of cursor.");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
