package com.iotproj.collectdata;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class NewSQLiteOpenHelper extends SQLiteOpenHelper {

    public static final String TABLE_NAME = "fingerprint";

    public NewSQLiteOpenHelper (Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 이곳에 DB를 create하는 SQL문 작성
        String sql = "CREATE table " + TABLE_NAME + " (mac text, rss int, pos text);";
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        String sql = ("drop table if exists " + TABLE_NAME);
        db.execSQL(sql);
        onCreate(db);
    }
}
