package org.radarcns.android.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.os.Parcel;
import android.os.Process;
import android.util.Pair;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;
import org.radarcns.android.util.AndroidThreadFactory;
import org.radarcns.data.AvroEncoder;
import org.radarcns.android.data.DataCache;
import org.radarcns.data.Record;
import org.radarcns.data.SpecificRecordEncoder;
import org.radarcns.key.MeasurementKey;
import org.radarcns.topic.AvroTopic;
import org.radarcns.util.ListPool;
import org.radarcns.util.RollingTimeAverage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static org.apache.avro.Schema.Type.NULL;
import static org.apache.avro.Schema.Type.UNION;
import static org.apache.avro.Schema.Type.ARRAY;
import static org.apache.avro.Schema.Type.FIXED;
import static org.apache.avro.Schema.Type.MAP;
import static org.apache.avro.Schema.Type.RECORD;

/**
 * Sqlite table for measurements.
 *
 * Measurements are grouped into transactions before being committed in a separate Thread
 */
public class MeasurementTable<V extends SpecificRecord> implements DataCache<MeasurementKey, V> {
    private static final Logger logger = LoggerFactory.getLogger(MeasurementTable.class);
    private static final int DATABASE_VERSION = 2;

    private final SQLiteOpenHelper dbHelper;
    private final AvroTopic<MeasurementKey, V> topic;
    private long window;
    private long lastOffsetSent;
    private final Object lastOffsetSentSync = new Object();
    private SubmitThread submitThread;
    private static final NumberFormat decimalFormat = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.US));
    private final ThreadFactory threadFactory;
    static {
        decimalFormat.setMaximumIntegerDigits(Integer.MAX_VALUE);
        decimalFormat.setMaximumFractionDigits(24);
    }

    private static final ListPool listPool = new ListPool(10);

    public MeasurementTable(Context context, AvroTopic<MeasurementKey, V> topic, long timeWindowMillis) {
        if (timeWindowMillis > System.currentTimeMillis()) {
            throw new IllegalArgumentException("Time window must be smaller than current absolute time");
        }
        this.dbHelper = new SQLiteOpenHelper(context, topic.getName() + ".db", null, DATABASE_VERSION) {
            @Override
            public void onCreate(SQLiteDatabase db) {
                createTable(db);
            }

            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                dropTable(db);
                onCreate(db);
            }
        };

        this.topic = topic;
        synchronized (this) {
            this.window = timeWindowMillis;
        }
        this.submitThread = null;
        this.lastOffsetSent = -1L;
        this.threadFactory = new AndroidThreadFactory(
                "MeasurementTable-" + MeasurementTable.this.topic.getName(),
                Process.THREAD_PRIORITY_BACKGROUND);

        for (Schema.Field field : this.topic.getValueSchema().getFields()) {
            Schema.Type fieldType = field.schema().getType();
            if (fieldType == UNION) {
                List<Schema> unionTypes = field.schema().getTypes();
                if (unionTypes.size() != 2) {
                    throw new IllegalArgumentException("Cannot handle UNION type with other than 2 types, not " + unionTypes + " in " + field.schema());
                }
                if (unionTypes.get(0).getType() == NULL) {
                    fieldType = unionTypes.get(1).getType();
                } else if (unionTypes.get(1).getType() == NULL) {
                    fieldType = unionTypes.get(0).getType();
                } else {
                    throw new IllegalArgumentException("Can only handle UNION that contains a null "
                            + "type, not a UNION with types " + unionTypes + " in " + field.schema());
                }
            }
            if (fieldType == RECORD || fieldType == ARRAY || fieldType == MAP || fieldType == FIXED || fieldType == UNION) {
                throw new IllegalArgumentException("Cannot handle type " + fieldType + " in schema " + field.schema());
            }
        }
    }

    @Override
    public synchronized void setTimeWindow(long timeWindowMillis) {
        this.window = timeWindowMillis;
    }

    private synchronized long getTimeWindow() {
        return this.window;
    }

    /** Start submitting data to the table. */
    private void start() {
        if (this.submitThread != null) {
            throw new IllegalStateException("Submit thread already started");
        }
        this.submitThread = new SubmitThread();
    }

    @Override
    public AvroTopic<MeasurementKey, V> getTopic() {
        return topic;
    }

    /** Submits new values to the database */
    private class SubmitThread {
        private final ScheduledExecutorService executor;
        private final List<Pair<MeasurementKey, V>> queue;
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
                List<Pair<MeasurementKey, V>> localQueue;
                synchronized (this) {
                    hasFuture = false;
                    if (queue.isEmpty()) {
                        return;
                    }
                    localQueue = listPool.get(queue);
                    queue.clear();
                }

                List<Schema.Field> fields = topic.getValueSchema().getFields();

                SQLiteDatabase db = dbHelper.getWritableDatabase();
                db.beginTransaction();
                try {
                    for (Pair<MeasurementKey, V> values : localQueue) {
                        insertRecord(values.first, values.second, fields);
                        db.yieldIfContendedSafely();
                    }
                    db.setTransactionSuccessful();
                } catch (RuntimeException ex) {
                    logger.error("Failed to add record", ex);
                    throw ex;
                } finally {
                    returnList(localQueue);
                    db.endTransaction();
                }
                logger.info("Committing {} records per second to topic {}", Math.round(average.getAverage()), topic.getName());
            }

            private void insertRecord(MeasurementKey key, SpecificRecord record, List<Schema.Field> fields) {
                statement.clearBindings();
                // bindings are 1-indexed
                statement.bindString(1, key.getUserId());
                statement.bindString(2, key.getSourceId());
                for (int i = 0; i < fields.size(); i++) {
                    Object value = record.get(i);
                    int idx = i + 3;
                    if (value == null) {
                        statement.bindNull(idx);
                    } else {
                        Schema.Type fieldType = fields.get(i).schema().getType();
                        if (fieldType == UNION) {
                            fieldType = getNonNullUnionType(fields.get(i).schema());
                        }
                        switch (fieldType) {
                            case DOUBLE:
                            case FLOAT:
                                statement.bindDouble(idx, ((Number) value).doubleValue());
                                break;
                            case LONG:
                            case INT:
                                statement.bindLong(idx, ((Number) value).longValue());
                                break;
                            case STRING:
                                statement.bindString(idx, value.toString());
                                break;
                            case BOOLEAN:
                                statement.bindLong(idx, value.equals(Boolean.TRUE) ? 1L : 0L);
                                break;
                            case BYTES:
                                statement.bindBlob(idx, (byte[]) value);
                                break;
                            case ENUM:
                                statement.bindString(idx, enumToString((Enum)value));
                                break;
                            default:
                                throw new IllegalArgumentException("Field cannot be processed, "
                                        + "with type " + fieldType + " in " + fields.get(i).schema());
                        }
                    }
                }
                if (statement.executeInsert() == -1) {
                    throw new RuntimeException("Failed to insert record with statement " + statement);
                }
            }
        }

        synchronized void add(MeasurementKey key, V value) {
            if (!hasFuture) {
                this.executor.schedule(new DoSubmitValues(), getTimeWindow(), TimeUnit.MILLISECONDS);
                hasFuture = true;
            }
            queue.add(new Pair<>(key, value));
        }

        synchronized void flush() {
            this.executor.schedule(new DoSubmitValues(), getTimeWindow(), TimeUnit.MILLISECONDS);
            hasFuture = true;
        }

        void close() {
            this.executor.shutdown();
        }
    }

    private static Schema.Type getNonNullUnionType(Schema unionSchema) {
        for (Schema subTypes : unionSchema.getTypes()) {
            if (subTypes.getType() != NULL) {
                return subTypes.getType();
            }
        }
        throw new IllegalArgumentException("UNION schema does not have non-null subtype: " + unionSchema);
    }

    /** Flush all pending measurements to the table */
    public void flush() {
        if (submitThread != null) {
            submitThread.flush();
        }
    }

    /** Close the data submission thread. */
    public void close() {
        if (submitThread != null) {
            this.submitThread.close();
            this.submitThread = null;
        }
        listPool.clear();
    }

    /**
     * Add a measurement to the table.
     *
     * This data is is written in a separate thread.
     *
     * @param value values of the measurement. These must match the fieldTypes passed to the
     *              constructor.
     */
    @Override
    public void addMeasurement(MeasurementKey key, V value) {
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
    @Override
    public int markSent(long offset) {
        synchronized (lastOffsetSentSync) {
            if (offset <= lastOffsetSent) {
                return 0;
            }
            lastOffsetSent = offset;
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues content = new ContentValues();
        content.put("sent", 1);
        return db.update(topic.getName(), content, "offset <= " + offset + " AND sent = 0", null);
    }

    /**
     * Return all unsent measurements in a database cursor.
     *
     * Use in a try-with-resources statement.
     * @return Iterator with column-name to value map.
     */
    @Override
    public List<Record<MeasurementKey, V>> unsentRecords(int limit) {
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
    private List<Record<MeasurementKey, V>> cursorToRecords(Cursor cursor) {
        List<Record<MeasurementKey, V>> records = listPool.get(
                Collections.<Record<MeasurementKey,V>>emptyList());

        List<Schema.Field> fields = topic.getValueSchema().getFields();

        while (cursor.moveToNext()) {
            V avroRecord = topic.newValueInstance();

            for (int i = 0; i < fields.size(); i++) {
                int idx = i + 4;
                Schema.Type fieldType = fields.get(i).schema().getType();
                if (fieldType == UNION) {
                    fieldType = getNonNullUnionType(fields.get(i).schema());
                }
                switch (fieldType) {
                    case STRING:
                        avroRecord.put(i, cursor.getString(idx));
                        break;
                    case BYTES:
                        avroRecord.put(i, cursor.getBlob(idx));
                        break;
                    case LONG:
                        avroRecord.put(i, cursor.getLong(idx));
                        break;
                    case INT:
                        avroRecord.put(i, cursor.getInt(idx));
                        break;
                    case BOOLEAN:
                        avroRecord.put(i, cursor.getInt(idx) > 0);
                        break;
                    case FLOAT:
                        avroRecord.put(i, cursor.getFloat(idx));
                        break;
                    case DOUBLE:
                        avroRecord.put(i, cursor.getDouble(idx));
                        break;
                    case ENUM:
                        avroRecord.put(i, parseEnum(cursor.getString(idx)));
                        break;
                    case NULL:
                        avroRecord.put(i, null);
                        break;
                    default:
                        throw new IllegalStateException("Cannot handle type " + fieldType);
                }
            }
            records.add(new Record<>(cursor.getLong(0), new MeasurementKey(cursor.getString(1), cursor.getString(2)), avroRecord));
        }
        return records;
    }

    private String enumToString(Enum value) {
        return value.getClass().getName() + ":" + value.name();
    }

    private Enum parseEnum(String value) {
        String[] classEnum = value.split(":");
        if (classEnum.length != 2) {
            throw new IllegalStateException("Cannot parse ENUM " + value + " which does not adhere to a class:value format.");
        }
        try {
            Class enumClass = Class.forName(classEnum[0]);
            return Enum.valueOf(enumClass, classEnum[1]);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot instantiate ENUM " + value + " of which the class " + classEnum[0] + " cannot be found");
        }
    }

    @Override
    public List<Record<MeasurementKey, V>> getRecords(int limit) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT * FROM " + topic.getName() + " ORDER BY offset DESC LIMIT " + limit;
        try (Cursor cursor = db.rawQuery(sql, null)) {
            return cursorToRecords(cursor);
        }
    }

    @Override
    public Pair<Long, Long> numberOfRecords() {
        long c1 = -1L;
        long c2 = -1L;
        try (Cursor records = dbHelper.getReadableDatabase().rawQuery("SELECT sent, COUNT(sent) FROM " + topic.getName() + " GROUP BY sent;", null)) {
            while (records.moveToNext()) {
                if (records.getInt(0) == 0) {
                    c1 = records.getLong(1);
                } else {
                    c2 = records.getLong(1);
                }
            }
        }
        return new Pair<>(c1, c2);
    }

    /** Create this table in the database. */
    private void createTable(SQLiteDatabase db) {
        String query = "CREATE TABLE " + topic.getName() + " (offset INTEGER PRIMARY KEY AUTOINCREMENT, userId TEXT, sourceId TEXT, sent INTEGER DEFAULT 0";
        for (Schema.Field f : topic.getValueSchema().getFields()) {
            query += ", " + f.name() + " ";
            Schema.Type fieldType = f.schema().getType();
            if (fieldType == UNION) {
                fieldType = getNonNullUnionType(f.schema());
            }
            switch (fieldType) {
                case STRING:
                case ENUM:
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
    private void dropTable(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + topic.getName());
    }

    @Override
    public void writeRecordsToParcel(Parcel dest, int limit) throws IOException {
        List<Record<MeasurementKey, V>> records = getRecords(limit);
        SpecificRecordEncoder specificEncoder = new SpecificRecordEncoder(true);
        AvroEncoder.AvroWriter<MeasurementKey> keyWriter = specificEncoder.writer(topic.getKeySchema(), MeasurementKey.class);
        AvroEncoder.AvroWriter<V> valueWriter = specificEncoder.writer(topic.getValueSchema(), topic.getValueClass());

        dest.writeInt(records.size());
        for (Record<MeasurementKey, V> record : records) {
            dest.writeLong(record.offset);
            dest.writeByteArray(keyWriter.encode(record.key));
            dest.writeByteArray(valueWriter.encode(record.value));
        }
        returnList(records);
    }

    @Override
    public void returnList(List list) {
        listPool.add(list);
    }
}
