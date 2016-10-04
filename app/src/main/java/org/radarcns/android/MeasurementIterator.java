package org.radarcns.android;

import android.database.Cursor;
import android.util.Pair;

import org.apache.avro.generic.GenericRecord;

import java.io.Closeable;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class MeasurementIterator implements Iterable<Pair<String, GenericRecord>>, Iterator<Pair<String, GenericRecord>>, Closeable {
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
        cursor.close();
    }

    @Override
    public boolean hasNext() {
        if (hasMoved) {
            return true;
        }
        if (cursor.moveToNext()) {
            hasMoved = true;
            return true;
        }
        return false;
    }

    @Override
    public Pair<String, GenericRecord> next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        hasMoved = false;
        return table.rowToRecord(cursor);
    }

    @Override
    public Iterator<Pair<String, GenericRecord>> iterator() {
        return this;
    }
}
