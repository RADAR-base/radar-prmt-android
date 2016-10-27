package org.radarcns.android;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class MeasurementDBHelper extends SQLiteOpenHelper {
    private final static int DATABASE_VERSION = 1;
    private final MeasurementTable table;

    public MeasurementDBHelper(MeasurementTable table, Context context, String databaseName) {
        super(context, databaseName, null, DATABASE_VERSION);
        this.table = table;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        table.createTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        table.dropTable(db);
        onCreate(db);
    }
}
