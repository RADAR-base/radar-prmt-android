package org.radarcns.android;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.radarcns.collect.Topic;
import org.radarcns.util.RollingTimeAverage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sqlite table for measurements.
 *
 * Measurements are grouped into transactions before being committed in a separate Thread
 */
public class MeasurementTable {
    private final static Logger logger = LoggerFactory.getLogger(MeasurementTable.class);
    private final MeasurementDBHelper dbHelper;
    private final Topic topic;
    private final long window;
    private SubmitThread submitThread;

    public MeasurementTable(Context context, Topic topic, long timeWindowMillis) {
        if (timeWindowMillis > System.currentTimeMillis()) {
            throw new IllegalArgumentException("Time window must be smaller than current absolute time");
        }
        this.dbHelper = new MeasurementDBHelper(this, context, topic.getName() + ".db");
        this.topic = topic;
        this.window = timeWindowMillis;
        this.submitThread = null;

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

    private void start() {
        if (this.submitThread != null) {
            throw new IllegalStateException("Submit thread already started");
        }
        this.submitThread = new SubmitThread();
    }

    /** Submits new values to the database */
    private class SubmitThread {
        private final ScheduledExecutorService executor;
        private final List<Object[][]> queue;
        private final RollingTimeAverage average;
        private boolean hasFuture;

        SubmitThread() {
            this.queue = new ArrayList<>();
            this.executor = Executors.newSingleThreadScheduledExecutor();
            this.hasFuture = false;
            this.average = new RollingTimeAverage(20_000L);
        }

        private class DoSubmitValues implements Runnable {
            @Override
            public void run() {
                List<Object[][]> localQueue;
                synchronized (this) {
                    hasFuture = false;
                    if (queue.isEmpty()) {
                        return;
                    }
                    localQueue = new ArrayList<>(queue);
                    queue.clear();
                }

                average.add(localQueue.size());
                Object[][] firstValues = localQueue.get(0);
                String statement = "INSERT INTO " + topic.getName() + '(';
                for (int i = 0; i < firstValues[0].length; i++) {
                    if (i > 0) {
                        statement += ',';
                    }
                    statement += firstValues[0][i];
                }
                statement += ") VALUES (";
                for (int i = 0; i < firstValues[1].length; i++) {
                    if (i > 0) {
                        statement += ",?";
                    } else {
                        statement += '?';
                    }
                }
                statement += ')';

                SQLiteDatabase db = dbHelper.getWritableDatabase();

                db.beginTransaction();
                try {
                    for (Object[][] values : localQueue) {
                        db.execSQL(statement, values[1]);
                    }
                    logger.debug("Committed {} records to topic {}", localQueue.size(), topic.getName());
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
                logger.info("Committing {} records per second to topic {}", Math.round(average.getAverage()), topic.getName());
            }
        }

        synchronized void add(Object[][] value) {
            if (!hasFuture) {
                this.executor.schedule(new DoSubmitValues(), window, TimeUnit.MILLISECONDS);
                hasFuture = true;
            }
            queue.add(value);
        }

        synchronized void flush() {
            this.executor.schedule(new DoSubmitValues(), window, TimeUnit.MILLISECONDS);
            hasFuture = true;
        }

        void close() {
            this.executor.shutdown();
        }
    }

    public void flush() {
        if (submitThread != null) {
            submitThread.flush();
        }
    }

    public void close() {
        if (submitThread != null) {
            this.submitThread.close();
            this.submitThread = null;
        }
    }

    /**
     * Add a measurement to the table.
     *
     * @param values values of the measurement. These must match the fieldTypes passed to the
     *               constructor.
     */
    public void addMeasurement(String deviceId, Object[] values) {
        if (submitThread == null) {
            start();
        }
        Object[][] content = new Object[2][values.length + 1];
        content[0][0] = "deviceId"; content[1][0] = deviceId;
        List<Schema.Field> fields = this.topic.getValueSchema().getFields();
        for (int i = 0; i < values.length; i++) {
            content[0][i + 1] = fields.get(i).name();
        }
        System.arraycopy(values, 0, content[1], 1, values.length);
        submitThread.add(content);
    }

    /**
     * Remove all sent measurements before a given offset.
     * @param timestamp time before which to remove.
     * @return number of rows removed
     */
    public int removeBeforeTimestamp(double timestamp) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int result = db.delete(topic.getName(), "timeReceived <= " + timestamp + " AND sent = 1", null);
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
        return db.update(topic.getName(), content, "offset <= " + offset + " AND sent = 0", null);
    }

    /**
     * Return all unsent measurements in a database cursor.
     *
     * Use in a try-with-resources statement.
     * @return Iterator with column-name to value map.
     */
    public MeasurementIterator getUnsentMeasurements(int limit) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        final Cursor cursor = db.rawQuery("SELECT * FROM " + topic.getName() + " WHERE sent = 0 ORDER BY offset ASC LIMIT " + limit, null);
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

    /** Create this table in the database. */
    void createTable(SQLiteDatabase db) {
        String query = "CREATE TABLE " + topic.getName() + " (offset INTEGER PRIMARY KEY AUTOINCREMENT, deviceId TEXT, sent INTEGER DEFAULT 0";
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
        logger.info("Created table: {}", query);
        db.execSQL(query);
    }

    /** Drop this table from the database. */
    void dropTable(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + topic.getName());
    }

    /**
     * A single measurement in the MeasurementTable.
     */
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

    /**
     * Converts a database row into a measurement.
     */
    Measurement rowToRecord(Cursor cursor) {
        List<Schema.Field> fields = topic.getValueSchema().getFields();

        GenericRecord avroRecord = new GenericData.Record(topic.getValueSchema());

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
}
