package org.radarcns.android;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Process;
import android.util.Pair;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;
import org.radarcns.data.DataCache;
import org.radarcns.data.Record;
import org.radarcns.kafka.AvroTopic;
import org.radarcns.key.MeasurementKey;
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
public class MeasurementTable implements DataCache<MeasurementKey, SpecificRecord> {
    private final static Logger logger = LoggerFactory.getLogger(MeasurementTable.class);
    private final MeasurementDBHelper dbHelper;
    private final AvroTopic topic;
    private final long window;
    private SubmitThread submitThread;
    private final static NumberFormat decimalFormat = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.US));
    private final ThreadFactory threadFactory;
    static {
        decimalFormat.setMaximumIntegerDigits(Integer.MAX_VALUE);
        decimalFormat.setMaximumFractionDigits(24);
    }

    public MeasurementTable(Context context, AvroTopic topic, long timeWindowMillis) {
        if (timeWindowMillis > System.currentTimeMillis()) {
            throw new IllegalArgumentException("Time window must be smaller than current absolute time");
        }
        this.dbHelper = new MeasurementDBHelper(this, context, topic.getName() + ".db");
        this.topic = topic;
        this.window = timeWindowMillis;
        this.submitThread = null;
        this.threadFactory = new AndroidThreadFactory(
                "MeasurementTable-" + MeasurementTable.this.topic.getName(),
                Process.THREAD_PRIORITY_BACKGROUND);

        for (Schema.Type fieldType : this.topic.getValueFieldTypes()) {
            switch (fieldType) {
                case RECORD:
                case ENUM:
                case ARRAY:
                case MAP:
                case UNION:
                case FIXED:
                    throw new IllegalArgumentException("Cannot handle type " + fieldType);
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

    @Override
    public AvroTopic getTopic() {
        return topic;
    }

    /** Submits new values to the database */
    private class SubmitThread {
        private final ScheduledExecutorService executor;
        private final List<Pair<MeasurementKey, SpecificRecord>> queue;
        private final RollingTimeAverage average;
        private final SQLiteStatement statement;
        private boolean hasFuture;

        SubmitThread() {
            this.queue = new ArrayList<>();
            this.executor = Executors.newSingleThreadScheduledExecutor(threadFactory);
            this.hasFuture = false;
            this.average = new RollingTimeAverage(20_000L);
            this.statement = compileInsert();
        }

        private SQLiteStatement compileInsert() {
            List<Schema.Field> fields = topic.getValueSchema().getFields();

            StringBuilder sb = new StringBuilder(50 + topic.getName().length() + 20 * fields.size());
            sb.append("INSERT INTO ").append(topic.getName()).append("(userId, sourceId");
            for (Schema.Field field : fields) {
                sb.append(',').append(field.name());
            }
            sb.append(") VALUES (?,?");
            for (int i = 0; i < fields.size(); i++) {
                sb.append(",?");
            }
            sb.append(')');

            return dbHelper.getWritableDatabase().compileStatement(sb.toString());
        }

        private class DoSubmitValues implements Runnable {
            @Override
            public void run() {
                List<Pair<MeasurementKey, SpecificRecord>> localQueue;
                synchronized (this) {
                    hasFuture = false;
                    if (queue.isEmpty()) {
                        return;
                    }
                    localQueue = new ArrayList<>(queue);
                    queue.clear();
                }

                Schema.Type[] fieldTypes = topic.getValueFieldTypes();

                SQLiteDatabase db = dbHelper.getWritableDatabase();
                db.beginTransaction();
                try {
                    for (Pair<MeasurementKey, SpecificRecord> values : localQueue) {
                        insertRecord(values.first, values.second, fieldTypes);
                        db.yieldIfContendedSafely();
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

            private void insertRecord(MeasurementKey key, SpecificRecord record, Schema.Type[] fieldTypes) {
                statement.clearBindings();
                statement.bindString(0, key.getUserId());
                statement.bindString(1, key.getDeviceId());
                for (int i = 0; i < fieldTypes.length; i++) {
                    Object value = record.get(i);
                    if (value == null) {
                        statement.bindNull(i + 1);
                    } else switch (fieldTypes[i]) {
                        case DOUBLE:
                        case FLOAT:
                            statement.bindDouble(i + 2, ((Number) value).doubleValue());
                            break;
                        case LONG:
                        case INT:
                            statement.bindLong(i + 2, ((Number) value).longValue());
                            break;
                        case STRING:
                            statement.bindLong(i + 2, ((Number) value).longValue());
                            break;
                        case BOOLEAN:
                            statement.bindLong(i + 2, value.equals(Boolean.TRUE) ? 1L : 0L);
                            break;
                        case BYTES:
                            statement.bindBlob(i + 2, (byte[]) value);
                            break;
                        default:
                            throw new IllegalArgumentException("Field type " + fieldTypes[i] + " cannot be processed");
                    }
                }
                if (statement.executeInsert() == -1) {
                    throw new RuntimeException("Failed to insert record with statement " + statement);
                }
            }
        }

        synchronized void add(MeasurementKey key, SpecificRecord value) {
            if (!hasFuture) {
                this.executor.schedule(new DoSubmitValues(), window, TimeUnit.MILLISECONDS);
                hasFuture = true;
            }
            queue.add(new Pair<>(key, value));
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
     * @param value values of the measurement. These must match the fieldTypes passed to the
     *              constructor.
     */
    @Override
    public void addMeasurement(MeasurementKey key, SpecificRecord value) {
        if (submitThread == null) {
            start();
        }
        submitThread.add(key, value);
    }

    /**
     * Remove all sent measurements before a given offset.
     * @param timestamp time before which to remove.
     * @return number of rows removed
     */
    public int removeBeforeTimestamp(long timestamp) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int result = db.delete(topic.getName(), "timeReceived <= " + timestamp/1000d + " AND sent = 1", null);
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + this.topic.getName() + " WHERE timeReceived <= " + timestamp/1000d + " AND sent = 0", null)) {
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
    @Override
    public Iterable<Record<MeasurementKey, SpecificRecord>> unsentRecords(int limit) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT * FROM " + topic.getName() +
                " WHERE sent = 0 ORDER BY offset ASC LIMIT " + limit;
        try (Cursor cursor = db.rawQuery(sql, null)) {
            return cursorToRecords(cursor);
        }
    }

    /**
     * Converts a database rows into a measurement.
     */
    private List<Record<MeasurementKey, SpecificRecord>> cursorToRecords(Cursor cursor) {
        List<Record<MeasurementKey, SpecificRecord>> records = new ArrayList<>(cursor.getCount());
        Schema.Type[] fieldTypes = topic.getValueFieldTypes();

        while (cursor.moveToNext()) {
            SpecificRecord avroRecord = topic.newValueInstance();

            for (int i = 0; i < fieldTypes.length; i++) {
                switch (fieldTypes[i]) {
                    case STRING:
                        avroRecord.put(i, cursor.getString(i + 4));
                        break;
                    case BYTES:
                        avroRecord.put(i, cursor.getBlob(i + 4));
                        break;
                    case LONG:
                        avroRecord.put(i, cursor.getLong(i + 4));
                        break;
                    case INT:
                        avroRecord.put(i, cursor.getInt(i + 4));
                        break;
                    case BOOLEAN:
                        avroRecord.put(i, cursor.getInt(i + 4) > 0);
                        break;
                    case FLOAT:
                        avroRecord.put(i, cursor.getFloat(i + 4));
                        break;
                    case DOUBLE:
                        avroRecord.put(i, cursor.getDouble(i + 4));
                        break;
                    case NULL:
                        avroRecord.put(i, null);
                        break;
                    default:
                        throw new IllegalStateException("Cannot handle type " + fieldTypes[i]);
                }
            }
            records.add(new Record<>(cursor.getLong(0), new MeasurementKey(cursor.getString(1), cursor.getString(2)), avroRecord));
        }
        return records;
    }

    /**
     * Return a given number of measurements.
     *
     * Use in a try-with-resources statement.
     * @return Iterator with column-name to value map.
     */
    public List<Record<MeasurementKey, SpecificRecord>> getMeasurements(int limit) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT * FROM " + topic.getName() + " ORDER BY offset DESC LIMIT " + limit;
        try (Cursor cursor = db.rawQuery(sql, null)) {
            return cursorToRecords(cursor);
        }
    }

    /** Create this table in the database. */
    void createTable(SQLiteDatabase db) {
        String query = "CREATE TABLE " + topic.getName() + " (offset INTEGER PRIMARY KEY AUTOINCREMENT, userId TEXT, sourceId TEXT, sent INTEGER DEFAULT 0";
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
}
