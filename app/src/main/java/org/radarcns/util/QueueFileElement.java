package org.radarcns.util;

/** A pointer to an element. */
public class QueueFileElement {
    /** Length of element header in bytes. */
    public static final int HEADER_LENGTH = 5;

    /** Position in file. */
    private long position;

    /** The length of the data. */
    private int length;

    /**
     * Constructs a new element.
     *
     * @param position within file
     * @param length of data
     */
    public QueueFileElement(long position, int length) {
        this.position = position;
        this.length = length;
    }

    public QueueFileElement() {
        this(0L, 0);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "[position=" + position
                + ", length=" + length
                + "]";
    }

    public void update(QueueFileElement element) {
        this.position = element.position;
        this.length = element.length;
    }

    public long dataPosition() {
        if (isEmpty()) {
            throw new IllegalStateException("Cannot get data position of empty element");
        }
        return position + QueueFileElement.HEADER_LENGTH;
    }

    public long nextPosition() {
        if (isEmpty()) {
            return QueueFileHeader.HEADER_LENGTH;
        } else {
            return position + QueueFileElement.HEADER_LENGTH + length;
        }
    }

    public void reset() {
        position = 0;
        length = 0;
    }

    public boolean isEmpty() {
        return length == 0;
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

    /**
     * Update the current position if it was transferred. This is deduced from the previous element
     * position and length and the file length.
     */
    public void updateIfTransferred(QueueFileElement previousElement, QueueFileHeader header) {
        if (position < previousElement.position && previousElement.nextPosition() < header.getLength()) {
            position = previousElement.nextPosition();
        }
    }

    public long getPosition() {
        return position;
    }

    public void setPosition(long position) {
        this.position = position;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public byte crc() {
        return crc(length);
    }

    public static byte crc(int value) {
        byte result = 17;
        result = (byte)(31 * result + (value >> 24) & 0xFF);
        result = (byte)(31 * result + ((value >> 16) & 0xFF));
        result = (byte)(31 * result + ((value >> 8) & 0xFF));
        result = (byte)(31 * result + (value & 0xFF));
        return result;
    }
}
