package org.radarcns.android;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.apache.avro.Schema;
import org.radarcns.collect.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class MeasurementDBHelper extends SQLiteOpenHelper {
    private final static String DATABASE_NAME = "Measurements.db";
    private final static int DATABASE_VERSION = 1;

    private final List<MeasurementTable> tables;
    private static Logger logger = LoggerFactory.getLogger(MeasurementDBHelper.class);

    public MeasurementDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.tables = new ArrayList<>();
    }

    public MeasurementTable createTable(Topic topic) {
        MeasurementTable table = new MeasurementTable(this, topic);
        this.tables.add(table);
        return table;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        for (MeasurementTable table : tables) {
            Topic topic = table.getTopic();
            String query = "CREATE TABLE " + topic.getName() + " (offset INTEGER PRIMARY KEY, deviceId TEXT, sent INTEGER DEFAULT 0";
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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        for (MeasurementTable table : tables) {
            db.execSQL("DROP TABLE IF EXISTS " + table.getTopic().getName());
        }
        onCreate(db);
    }
}
