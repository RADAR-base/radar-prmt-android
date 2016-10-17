package org.radarcns.android;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Process;
import android.support.annotation.NonNull;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.radarcns.collect.Topic;
import org.radarcns.util.RollingTimeAverage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
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
    private final Schema.Field timeField;
    private final Schema.Field timeReceivedField;
    private final int valueSize;
    private SubmitThread submitThread;
    private final static NumberFormat decimalFormat = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.US));
    private final ThreadFactory threadFactory;
    static {
        decimalFormat.setMaximumIntegerDigits(Integer.MAX_VALUE);
        decimalFormat.setMaximumFractionDigits(24);
    }

    public MeasurementTable(Context context, Topic topic, long timeWindowMillis) {
        if (timeWindowMillis > System.currentTimeMillis()) {
            throw new IllegalArgumentException("Time window must be smaller than current absolute time");
        }
        this.dbHelper = new MeasurementDBHelper(this, context, topic.getName() + ".db");
        this.topic = topic;
        this.window = timeWindowMillis;
        this.submitThread = null;
        this.threadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable r) {
                return new Thread(r, "MeasurementTable-" + MeasurementTable.this.topic.getName());
            }
        };
        Schema schema = topic.getValueSchema();
        this.timeField = schema.getField("time");
        this.timeReceivedField = schema.getField("timeReceived");
        if (timeField == null) {
            throw new IllegalArgumentException("Schema must have time as its first field");
        }
        if (timeReceivedField == null) {
            throw new IllegalArgumentException("Schema must have timeReceived as its second field");
        }
        valueSize = 1 + schema.getFields().size();

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

    public Topic getTopic() {
        return topic;
    }

    /** Submits new values to the database */
    private class SubmitThread {
        private final ScheduledExecutorService executor;
        private final List<Object[]> queue;
        private final RollingTimeAverage average;
        private final SQLiteStatement statement;
        private boolean hasFuture;

        SubmitThread() {
            this.queue = new ArrayList<>();
            this.executor = Executors.newSingleThreadScheduledExecutor(threadFactory);
            this.executor.submit(new Runnable() {
                @Override
                public void run() {
                    android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                }
            });
            this.hasFuture = false;
            this.average = new RollingTimeAverage(20_000L);
            this.statement = compileInsert();
        }

        private SQLiteStatement compileInsert() {
            List<Schema.Field> fields = topic.getValueSchema().getFields();

            StringBuilder sb = new StringBuilder(50 + topic.getName().length() + 20 * fields.size());
            sb.append("INSERT INTO ").append(topic.getName()).append("(");
            for (Schema.Field field : fields) {
                sb.append(field.name()).append(',');
            }
            sb.append("deviceId) VALUES (");
            for (int i = 0; i < fields.size(); i++) {
                sb.append("?,");
            }
            sb.append("?)");

            return dbHelper.getWritableDatabase().compileStatement(sb.toString());
        }

        private class DoSubmitValues implements Runnable {
            @Override
            public void run() {
                List<Object[]> localQueue;
                synchronized (this) {
                    hasFuture = false;
                    if (queue.isEmpty()) {
                        return;
                    }
                    localQueue = new ArrayList<>(queue);
                    queue.clear();
                }

                SQLiteDatabase db = dbHelper.getWritableDatabase();
                List<Schema.Field> fields = topic.getValueSchema().getFields();

                db.beginTransaction();
                try {
                    for (Object[] values : localQueue) {
                        statement.clearBindings();
                        for (int i = 0; i < values.length; i++) {
                            Schema.Type expectedType;
                            if (i == values.length - 1) {
                                expectedType = Schema.Type.STRING;
                            } else {
                                expectedType = fields.get(i).schema().getType();
                            }
                            if (values[i] == null) {
                                statement.bindNull(i + 1);
                            } else if (values[i] instanceof Double || values[i] instanceof Float) {
                                if (expectedType != Schema.Type.FLOAT && expectedType != Schema.Type.DOUBLE) {
                                    throw new IllegalArgumentException("Expected type " + expectedType + " for column " + i + ", found DOUBLE " + values[i]);
                                }
                                statement.bindDouble(i + 1, ((Number) values[i]).doubleValue());
                            } else if (values[i] instanceof String) {
                                if (expectedType != Schema.Type.STRING) {
                                    throw new IllegalArgumentException("Expected type " + expectedType + " for column " + i + ", found STRING " + values[i]);
                                }
                                statement.bindString(i + 1, (String) values[i]);
                            } else if (values[i] instanceof Integer || values[i] instanceof Long) {
                                if (expectedType != Schema.Type.INT && expectedType != Schema.Type.LONG) {
                                    throw new IllegalArgumentException("Expected type " + expectedType + " for column " + i + ", found LONG " + values[i]);
                                }
                                statement.bindLong(i + 1, ((Number) values[i]).longValue());
                            } else if (values[i] instanceof Boolean) {
                                if (expectedType != Schema.Type.BOOLEAN) {
                                    throw new IllegalArgumentException("Expected type " + expectedType + " for column " + i + ", found BOOLEAN " + values[i]);
                                }
                                statement.bindLong(i + 1, values[i].equals(Boolean.TRUE) ? 1L : 0L);
                            } else if (values[i] instanceof byte[]) {
                                if (expectedType != Schema.Type.BYTES) {
                                    throw new IllegalArgumentException("Expected type " + expectedType + " for column " + i + ", found BYTES " + values[i]);
                                }
                                statement.bindBlob(i + 1, (byte[]) values[i]);
                            } else {
                                throw new IllegalArgumentException("Cannot parse type " + values[i].getClass());
                            }
                        }
                        if (statement.executeInsert() == -1) {
                            throw new RuntimeException("Failed to insert record with statement " + statement);
                        }
                    }
                    db.setTransactionSuccessful();
                } catch (RuntimeException ex) {
                    logger.error("Failed to add record", ex);
                    throw ex;
                } finally {
                    db.endTransaction();
                }
                logger.info("Committing {} records per second to topic {}", Math.round(average.getAverage()), topic.getName());
            }
        }

        synchronized void add(Object[] value) {
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
    void addMeasurement(String deviceId, double time, double timeReceived, Object[] values) {
        if (submitThread == null) {
            start();
        }

        Object[] content = new Object[valueSize];
        content[timeField.pos()] = time;
        content[timeReceivedField.pos()] = timeReceived;

        for (int i = 0; i < values.length; i += 2) {
            Schema.Field field;
            if (values[i] instanceof Schema.Field) {
                field = (Schema.Field) values[i];
            } else if (values[i] instanceof String) {
                field = topic.getValueSchema().getField((String) values[i]);
            } else {
                throw new IllegalArgumentException("Record key " + values[i] + " is not a Schema.Field or String");
            }
            content[field.pos()] = values[i + 1];
        }

        content[content.length - 1] = deviceId;
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
                    avroRecord.put(i, cursor.getString(i + 3));
                    break;
                case BYTES:
                    avroRecord.put(i, cursor.getBlob(i + 3));
                    break;
                case LONG:
                    avroRecord.put(i, cursor.getLong(i + 3));
                    break;
                case INT:
                    avroRecord.put(i, cursor.getInt(i + 3));
                    break;
                case BOOLEAN:
                    avroRecord.put(i, cursor.getInt(i + 3) > 0);
                    break;
                case FLOAT:
                    avroRecord.put(i, cursor.getFloat(i + 3));
                    break;
                case DOUBLE:
                    avroRecord.put(i, cursor.getDouble(i + 3));
                    break;
                case NULL:
                    avroRecord.put(i, null);
                    break;
                default:
                    throw new IllegalStateException("Cannot handle type " + fields.get(i).schema().getType());
            }
        }

        return new Measurement(cursor.getLong(0), cursor.getString(1), avroRecord);
    }
}
