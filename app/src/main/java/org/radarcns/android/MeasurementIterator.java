package org.radarcns.android;

import android.database.Cursor;
import android.util.Pair;

import org.apache.avro.generic.GenericRecord;

import java.io.Closeable;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class MeasurementIterator implements Iterable<MeasurementTable.Measurement>, Iterator<MeasurementTable.Measurement>, Closeable {
    private final MeasurementTable table;
    private boolean hasMoved = false;
    private final Cursor cursor;

    MeasurementIterator(Cursor cursor, MeasurementTable table) {
        this.cursor = cursor;
        this.table = table;
        this.hasMoved = false;
    }

    @Override
    public void close() {
        if (!cursor.isClosed())
            cursor.close();
    }

    @Override
    public boolean hasNext() {
        if (hasMoved) {
            return true;
        }
        if (cursor.isClosed()) {
            return false;
        }
        if (cursor.moveToNext()) {
            hasMoved = true;
            return true;
        } else {
            cursor.close();
            return false;
        }
    }

    @Override
    public MeasurementTable.Measurement next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        hasMoved = false;
        return table.rowToRecord(cursor);
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<MeasurementTable.Measurement> iterator() {
        return this;
    }
}
