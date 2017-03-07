package org.radarcns.util;

/**
 * A pointer to an element. It contains a starting position and length of the data that the element
 * contains.
 */
public class QueueFileElement {
    /** Length of element header in bytes. */
    public static final int HEADER_LENGTH = 5;

    /** Position in file. */
    private long position;

    /** The length of the data. */
    private int length;

    /**
     * An element with given position and data length.
     *
     * @param position within file
     * @param length of data
     */
    public QueueFileElement(long position, int length) {
        this.position = position;
        this.length = length;
    }

    /**
     * An empty element. Without setting position and length, this cannot be used for reading or
     * writing.
     */
    public QueueFileElement() {
        this(0L, 0);
    }

    /** Update element values to the given element. */
    public void update(QueueFileElement element) {
        this.position = element.position;
        this.length = element.length;
    }

    /** Position that the data begins. */
    public long dataPosition() {
        if (isEmpty()) {
            throw new IllegalStateException("Cannot get data position of empty element");
        }
        return position + QueueFileElement.HEADER_LENGTH;
    }

    /** Position of the next element. */
    public long nextPosition() {
        if (isEmpty()) {
            return QueueFileHeader.HEADER_LENGTH;
        } else {
            return position + QueueFileElement.HEADER_LENGTH + length;
        }
    }

    /** Sets the element to empty. */
    public void reset() {
        position = 0;
        length = 0;
    }

    /** The element does not have valid data. */
    public boolean isEmpty() {
        return length == 0;
    }

    /**
     * Update the current position if it was moved. This is deduced from the previous element's
     * position and length and from the file length.
     */
    public void updateIfMoved(QueueFileElement previousElement, QueueFileHeader header) {
        if (position < previousElement.position && previousElement.nextPosition() < header.getLength()) {
            position = previousElement.nextPosition();
        }
    }

    /** Get the start position. */
    public long getPosition() {
        return position;
    }

    /**
     * Set the start position.
     * @throws IllegalArgumentException if {@code position < 0}
     */
    public void setPosition(long position) {
        if (position < 0) {
            throw new IllegalArgumentException("position < 0");
        }
        this.position = position;
    }

    /**
     * Get the length in bytes, excluding the header. If this is 0, the element is not valid
     * for reading or writing.
     */
    public int getLength() {
        return length;
    }

    /**
     * Set the length in bytes, excluding the header. If this is 0, the element is not valid
     * for reading or writing.
     * @throws IllegalArgumentException if {@code length < 0}
     */
    public void setLength(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length < 0");
        }
        this.length = length;
    }

    /** Single byte checksum of the length. */
    public byte crc() {
        return crc(length);
    }

    /** Single byte checksum of given value. */
    public static byte crc(int value) {
        byte result = 17;
        result = (byte)(31 * result + (value >> 24) & 0xFF);
        result = (byte)(31 * result + ((value >> 16) & 0xFF));
        result = (byte)(31 * result + ((value >> 8) & 0xFF));
        result = (byte)(31 * result + (value & 0xFF));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (other == null || getClass() != other.getClass()) return false;

        QueueFileElement otherElement = (QueueFileElement)other;
        return position == otherElement.position && length == otherElement.length;
    }

    @Override
    public int hashCode() {
        return 31 * (int)((position >> 32) ^ position)+ length;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "[position=" + position
                + ", length=" + length
                + "]";
    }
}
