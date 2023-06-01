package com.iotproj.collectdata;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class NewSQLiteOpenHelper2 extends SQLiteOpenHelper {

    public static final String TABLE_NAME = "newfinger";

    public NewSQLiteOpenHelper2 (Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 이곳에 DB를 create하는 SQL문 작성
        String sql = "CREATE table " + TABLE_NAME +
                " (currentpos text, targetpos text, diff1 int, diff2 int, diff3 int, diff4 int, diff5 int, diff6 int, diff7 int, eucdist decimal(3,1));";
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        String sql = ("drop table if exists " + TABLE_NAME);
        db.execSQL(sql);
        onCreate(db);
    }

}
