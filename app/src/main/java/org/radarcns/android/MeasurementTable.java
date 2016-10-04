package org.radarcns.android;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.radarcns.collect.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MeasurementTable {
    private final static Logger logger = LoggerFactory.getLogger(MeasurementTable.class);
    private final MeasurementDBHelper dbHelper;
    private final Topic topic;

    public MeasurementTable(MeasurementDBHelper dbHelper, Topic topic) {
        this.dbHelper = dbHelper;
        this.topic = topic;


        for (Schema.Field f : this.getTopic().getValueSchema().getFields()) {
            switch (f.schema().getType()) {
                case RECORD:
                case ENUM:
                case ARRAY:
                case MAP:
                case UNION:
                case FIXED:
                    throw new IllegalArgumentException("Cannot handle type " + f.schema().getType());
                default:
                    // nothing
            }
        }
    }

    /**
     * Add a measurement to the table.
     *
     * @param values values of the measurement. These must match the fieldTypes passed to the
     *               constructor.
     * @return offset of the measurement in the database
     */
    public long addMeasurement(String deviceId, Object[] values) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues content = new ContentValues();
        content.put("deviceId", deviceId);
        List<Schema.Field> fields = this.getTopic().getValueSchema().getFields();
        for (int i = 0; i < values.length; i++) {
            Schema.Field f = fields.get(i);
            try {
                switch (f.schema().getType()) {
                    case STRING:
                        content.put(f.name(), (String) values[i]);
                        break;
                    case BYTES:
                        content.put(f.name(), (byte[]) values[i]);
                        break;
                    case LONG:
                        content.put(f.name(), (Long) values[i]);
                        break;
                    case INT:
                        content.put(f.name(), (Integer) values[i]);
                        break;
                    case BOOLEAN:
                        content.put(f.name(), (Boolean) values[i]);
                        break;
                    case FLOAT:
                        content.put(f.name(), (Float) values[i]);
                        break;
                    case DOUBLE:
                        content.put(f.name(), (Double) values[i]);
                        break;
                    case NULL:
                        content.putNull(f.name());
                        break;
                    default:
                        throw new IllegalStateException("Cannot handle type " + f.schema().getType());
                }
            } catch (ClassCastException ex) {
                logger.error("Cannot cast value {} of field {} to {}", values[i], f.name(), f.schema().getType());
                throw ex;
            }
        }
        return db.insert(getTopic().getName(), null, content);
    }

    /**
     * Remove all sent measurements before a given offset.
     * @param timestamp time before which to remove.
     * @return number of rows removed
     */
    public int removeBeforeTimestamp(double timestamp) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int result = db.delete(getTopic().getName(), "timeReceived <= " + timestamp + " AND sent = 1", null);
        db.close();
        db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + this.getTopic().getName() + " WHERE timeReceived <= " + timestamp + " AND sent = 0", null)) {
            cursor.moveToNext();
            int unsent = cursor.getInt(0);
            if (unsent > 1000) {
                logger.warn("Accumulating unsent results, currently {} unsent values.", unsent);
            }
        }
        return result;
    }

    /**
     * Remove all measurements before a given offset.
     * @param offset offset (inclusive) to remove.
     * @return number of rows removed
     */
    public int markSent(long offset) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues content = new ContentValues();
        content.put("sent", Boolean.TRUE);
        return db.update(getTopic().getName(), content, "offset <= " + offset, null);
    }

    /**
     * Return all unsent measurements in a database cursor.
     *
     * Use in a try-with-resources statement.
     * @return Iterator with column-name to value map.
     */
    public MeasurementIterator getUnsentMeasurements() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        final Cursor cursor = db.rawQuery("SELECT * FROM " + getTopic().getName() + " WHERE sent = 0 ORDER BY offset ASC", null);
        return new MeasurementIterator(cursor, this);
    }

    /**
     * Return a given number of measurements.
     *
     * Use in a try-with-resources statement.
     * @return Iterator with column-name to value map.
     */
    public MeasurementIterator getMeasurements(int limit) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        final Cursor cursor = db.rawQuery("SELECT * FROM " + getTopic().getName() + " ORDER BY offset DESC LIMIT " + limit, null);
        return new MeasurementIterator(cursor, this);
    }

    public static class Measurement {
        public final long offset;
        public final String key;
        public final GenericRecord value;
        Measurement(long offset, String key, GenericRecord value) {
            this.offset = offset;
            this.key = key;
            this.value = value;
        }
    }

    Measurement rowToRecord(Cursor cursor) {
        List<Schema.Field> fields = getTopic().getValueSchema().getFields();

        GenericRecord avroRecord = new GenericData.Record(this.getTopic().getValueSchema());

        for (int i = 0; i < fields.size(); i++) {
            switch (fields.get(i).schema().getType()) {
                case STRING:
                    avroRecord.put(fields.get(i).name(), cursor.getString(i + 3));
                    break;
                case BYTES:
                    avroRecord.put(fields.get(i).name(), cursor.getBlob(i + 3));
                    break;
                case LONG:
                    avroRecord.put(fields.get(i).name(), cursor.getLong(i + 3));
                    break;
                case INT:
                    avroRecord.put(fields.get(i).name(), cursor.getInt(i + 3));
                    break;
                case BOOLEAN:
                    avroRecord.put(fields.get(i).name(), cursor.getInt(i + 3) > 0);
                    break;
                case FLOAT:
                    avroRecord.put(fields.get(i).name(), cursor.getFloat(i + 3));
                    break;
                case DOUBLE:
                    avroRecord.put(fields.get(i).name(), cursor.getDouble(i + 3));
                    break;
                case NULL:
                    avroRecord.put(fields.get(i).name(), null);
                    break;
                default:
                    throw new IllegalStateException("Cannot handle type " + fields.get(i).schema().getType());
            }
        }

        return new Measurement(cursor.getLong(0), cursor.getString(1), avroRecord);
    }

    public Topic getTopic() {
        return topic;
    }
}
