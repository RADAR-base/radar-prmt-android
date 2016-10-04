package org.radarcns.android;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Pair;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.radarcns.collect.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MeasurementTable {
    private final static String DATABASE_NAME = "Measurements.db";
    private final static int DATABASE_VERSION = 1;
    private final static Logger logger = LoggerFactory.getLogger(MeasurementTable.class);
    private final MeasurementDBHelper dbHelper;
    private final Topic topic;

    public MeasurementTable(Context context, Topic topic) {
        this.dbHelper = new MeasurementDBHelper(context);
        this.topic = topic;


        for (Schema.Field f : this.topic.getValueSchema().getFields()) {
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
     * @param offset offset that the system gave to the measurement
     * @param values values of the measurement. These must match the fieldTypes passed to the
     *               constructor.
     */
    public void addMeasurement(long offset, String deviceId, Object... values) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues content = new ContentValues();
        content.put("offset", offset);
        content.put("deviceId", deviceId);
        List<Schema.Field> fields = this.topic.getValueSchema().getFields();
        for (int i = 0; i < values.length; i++) {
            Schema.Field f = fields.get(i);
            switch (f.schema().getType()) {
                case STRING:
                    content.put(f.name(), (String)values[i]);
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
                    content.put(f.name(), (Boolean)values[i]);
                    break;
                case FLOAT:
                    content.put(f.name(), (Float)values[i]);
                    break;
                case DOUBLE:
                    content.put(f.name(), (Double)values[i]);
                    break;
                case NULL:
                    content.putNull(f.name());
                    break;
                default:
                    throw new IllegalStateException("Cannot handle type " + f.schema().getType());
            }
        }
        db.insert(topic.getName(), null, content);
    }

    /**
     * Remove all sent measurements before a given offset.
     * @param timestamp time before which to remove.
     * @return number of rows removed
     */
    public int removeBeforeTimestamp(double timestamp) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int result = db.delete(topic.getName(), "timeReceived <= " + timestamp + " AND sent = 1", null);
        db.close();
        db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + this.topic.getName() + " WHERE timeReceived <= " + timestamp + " AND sent = 0", null)) {
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
        return db.update(topic.getName(), content, "offset <= " + offset, null);
    }

    /**
     * Return all unsent measurements in a database cursor.
     *
     * Use in a try-with-resources statement.
     * @return Iterator with column-name to value map.
     */
    public MeasurementIterator getUnsentMeasurements() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        final Cursor cursor = db.rawQuery("SELECT * FROM " + topic.getName() + " WHERE sent = 0 ORDER BY offset ASC", null);
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
        final Cursor cursor = db.rawQuery("SELECT * FROM " + topic.getName() + " ORDER BY offset DESC LIMIT " + limit, null);
        return new MeasurementIterator(cursor, this);
    }

    private class MeasurementDBHelper extends SQLiteOpenHelper {
        MeasurementDBHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            String query = "CREATE TABLE " + topic.getName() + " (offset INTEGER PRIMARY KEY, deviceId TEXT, sent INTEGER DEFAULT 0, time REAL, timeReceived REAL";
            for (Schema.Field f : topic.getValueSchema().getFields()) {
                query += ", " + f.name() + " ";
                switch (f.schema().getType()) {
                    case STRING:
                        query += "TEXT";
                        break;
                    case BYTES:
                        query += "BLOB";
                        break;
                    case LONG:
                    case INT:
                    case BOOLEAN:
                        query += "INTEGER";
                        break;
                    case FLOAT:
                    case DOUBLE:
                        query += "REAL";
                        break;
                    case NULL:
                        query += "NULL";
                        break;
                    default:
                        throw new IllegalStateException("Cannot handle type " + f.schema().getType());
                }
            }
            query += ")";
            db.execSQL(query);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + topic.getName());
            onCreate(db);
        }
    }

    Pair<String, GenericRecord> rowToRecord(Cursor cursor) {
        List<Schema.Field> fields = topic.getValueSchema().getFields();

        GenericRecord avroRecord = new GenericData.Record(this.topic.getValueSchema());

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

        return new Pair<>(cursor.getString(1), avroRecord);
    }
}
