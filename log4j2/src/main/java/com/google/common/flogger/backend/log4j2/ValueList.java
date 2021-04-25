package com.google.common.flogger.backend.log4j2;

import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.context.Tags;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static com.google.common.base.Preconditions.checkNotNull;

/*
 * A simple linked list designed to store multiple metadata values efficiently in a StringMap.
 *
 * <p>Metadata values in Flogger always have unique keys, but those keys can have the same label.
 * Because Log4j2 uses a {@code String} keyed map, we would risk clashing of values if we just used
 * the label to store each value directly. This class lets us store a list of values for a single label while
 * being memory efficient in the common case where each label really does only have one value.
 */
final class ValueList implements Iterable<Object> {

    private final Object value;
    private final ValueList next; // Nullable

    private static ValueList EMPTY = new ValueList(null, null);

    ValueList(Object value, ValueList existingList) {
        // caller ensures this is non null.
        this.value = value;
        // to ensure the iterator works
        this.next = existingList == null ? EMPTY : existingList;
    }

    static Object maybeWrap(Object value, @NullableDecl Object existingValue) {
        checkNotNull(value);
        if (existingValue == null) {
            return value;
        } else {
            // This should only rarely happen, so a few small allocations seems acceptable.
            ValueList existingList = existingValue instanceof ValueList ? (ValueList) existingValue
                    : new ValueList(existingValue, null);
            return new ValueList(value, existingList);
        }
    }

    @Override
    public Iterator<Object> iterator() {
        return new LinkedIterator(this);
    }

    static void appendValues(String label, Object valueOrList, MetadataKey.KeyValueHandler kvh) {
        if (valueOrList instanceof ValueList) {
            for (Object value : (ValueList) valueOrList) {
                emit(label, value, kvh);
            }
        } else {
            emit(label, valueOrList, kvh);
        }
    }

    /**
     * Tags are key-value mappings which cannot be modified or replaced. If you add
     * the tag mapping "foo" -> true and later add the mapping "foo" -> false, you
     * get the value "foo" mapped to both true and false. This is *very* deliberate
     * since the key space for tags is global and the risk of two bits of code
     * accidentally using the same tag name is real (e.g. you get "id=abcd" but you
     * added "id=xyz" so you think this isn't your log entry, but someone else added
     * "id=abcd" in a context you weren't aware of).
     *
     * Given three tag mappings: "baz" -> , "foo" -> true and "foo" -> false the
     * Value list is going to flatten the tags in order to store them as tags=[baz,
     * foo=false, foo=true].
     * 
     * Reusing the label 'tags' is intentional as this allows us to store the
     * flatten tags in Log4j2's ContextMap.
     */
    static void emit(String label, Object value, MetadataKey.KeyValueHandler kvh) {
        if (value instanceof Tags) {
            // Flatten tags to treat them as keys or key/value pairs, e.g. tags=[foo,
            // baz=bar2, baz=bar]
            ((Tags) value).asMap().forEach((k, v) -> {
                if (v.isEmpty()) {
                    kvh.handle(label, k);
                } else {
                    for (Object obj : v) {
                        kvh.handle(label, k + "=" + obj.toString());
                    }
                }
            });
        } else {
            kvh.handle(label, value);
        }
    }

    /**
     * Returns a string representation of the contents of the specified value list.
     * If the list contains other lists as elements, they are converted to strings.
     *
     * <p>
     * If value list is empty, this method returns {@code "null"}.
     * <p>
     * If value list contains a single element {@code a}, this method returns
     * {@code "a"}.
     * <p>
     * Otherwise the content of teh list is returned surrounded by brackets "[", "].
     */
    @Override
    public String toString() {
        if (value == null) {
            return "null";
        }

        if (next == null || next.value == null) {
            return value.toString();
        }

        StringBuilder out = new StringBuilder();
        out.append("[");
        Iterator<Object> it = iterator();
        while (it.hasNext()) {
            out.append(it.next());
            if (it.hasNext())
                out.append(", ");
        }
        out.append(']');
        return out.toString();
    }

    private static class LinkedIterator implements Iterator<Object> {
        private Object _value;
        private ValueList _next;

        private LinkedIterator(ValueList valueList) {
            _value = valueList.value;
            _next = valueList.next;
        }

        public boolean hasNext() {
            return _next != null;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

        public Object next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            Object item = _value;
            _value = _next.value;
            _next = _next.next;
            return item;
        }
    }
}
