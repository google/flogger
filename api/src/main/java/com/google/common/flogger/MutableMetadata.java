package com.google.common.flogger;

import com.google.common.flogger.backend.Metadata;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.util.Arrays;

import static com.google.common.flogger.util.Checks.checkNotNull;

/**
 * @Desctiption
 * @Author wallace
 * @Date 2020/12/26
 */
public class MutableMetadata extends Metadata {

	/**
	 * The default number of key/value pairs we initially allocate space for when someone adds
	 * metadata to this context.
	 * <p>
	 * Note: As of 10/12 the VM allocates small object arrays very linearly with respect to the
	 * number of elements (an array has a 12 byte header with 4 bytes/element for object
	 * references). The allocation size is always rounded up to the next 8 bytes which means we
	 * can just pick a small value for the initial size and grow from there without too much waste.
	 * <p>
	 * For 4 key/value pairs, we will need 8 elements in the array, which will take up 48 bytes
	 * {@code (12 + (8 * 4) = 44}, which when rounded up is 48.
	 */
	private static final int INITIAL_KEY_VALUE_CAPACITY = 4;

	/**
	 * The array of key/value pairs to hold any metadata the might be added by the logger or any of
	 * the fluent methods on our API. This is an array so it is as space efficient as possible.
	 */
	private Object[] keyValuePairs = new Object[2 * INITIAL_KEY_VALUE_CAPACITY];
	/** The number of key/value pairs currently stored in the array. */
	private int keyValueCount = 0;

	@Override
	public int size() {
		return keyValueCount;
	}

	@Override
	public MetadataKey<?> getKey(int n) {
		if (n >= keyValueCount) {
			throw new IndexOutOfBoundsException();
		}
		return (MetadataKey<?>) keyValuePairs[2 * n];
	}

	@Override
	public Object getValue(int n) {
		if (n >= keyValueCount) {
			throw new IndexOutOfBoundsException();
		}
		return keyValuePairs[(2 * n) + 1];
	}

	private int indexOf(MetadataKey<?> key) {
		for (int index = 0; index < keyValueCount; index++) {
			if (keyValuePairs[2 * index].equals(key)) {
				return index;
			}
		}
		return -1;
	}

	@Override
	@NullableDecl
	public <T> T findValue(MetadataKey<T> key) {
		int index = indexOf(key);
		return index != -1 ? key.cast(keyValuePairs[(2 * index) + 1]) : null;
	}

	/**
	 * Adds the key/value pair to the metadata (growing the internal array as necessary). If the
	 * key cannot be repeated, and there is already a value for the key in the metadata, then the
	 * existing value is replaced, otherwise the value is added at the end of the metadata.
	 */
	<T> void addValue(MetadataKey<T> key, T value) {
		if (!key.canRepeat()) {
			int index = indexOf(key);
			if (index != -1) {
				keyValuePairs[(2 * index) + 1] = checkNotNull(value, "metadata value");
				return;
			}
		}
		// Check that the array is big enough for one more element.
		if (2 * (keyValueCount + 1) > keyValuePairs.length) {
			// Use doubling here (this code should almost never be hit in normal usage and the total
			// number of items should always stay relatively small. If this resizing algorithm is ever
			// modified it is vital that the new value is always an even number.
			keyValuePairs = Arrays.copyOf(keyValuePairs, 2 * keyValuePairs.length);
		}
		keyValuePairs[2 * keyValueCount] = checkNotNull(key, "metadata key");
		keyValuePairs[(2 * keyValueCount) + 1] = checkNotNull(value, "metadata value");
		keyValueCount += 1;
	}

	/** Removes all key/value pairs for a given key. */
	void removeAllValues(MetadataKey<?> key) {
		int index = indexOf(key);
		if (index >= 0) {
			int dest = 2 * index;
			int src = dest + 2;
			while (src < (2 * keyValueCount)) {
				Object nextKey = keyValuePairs[src];
				if (!nextKey.equals(key)) {
					keyValuePairs[dest] = nextKey;
					keyValuePairs[dest + 1] = keyValuePairs[src + 1];
					dest += 2;
				}
				src += 2;
			}
			// We know src & dest are +ve and (src > dest), so shifting is safe here.
			keyValueCount -= (src - dest) >> 1;
			while (dest < src) {
				keyValuePairs[dest++] = null;
			}
		}
	}

	/** Strictly for debugging. */
	@Override
	public String toString() {
		StringBuilder out = new StringBuilder("Metadata{");
		for (int n = 0; n < size(); n++) {
			out.append(" '").append(getKey(n)).append("': ").append(getValue(n));
		}
		return out.append(" }").toString();
	}
}
