package com.edigitalsolutions.vehiclekmlog;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "vehicle_km_log.db";
    private static final int DB_VERSION = 1;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE vehicles (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "registration TEXT NOT NULL UNIQUE COLLATE NOCASE)");
        db.execSQL("CREATE TABLE logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "log_date TEXT NOT NULL," +
                "vehicle_id INTEGER NOT NULL," +
                "driver TEXT DEFAULT ''," +
                "opening_km REAL NOT NULL," +
                "closing_km REAL NOT NULL," +
                "notes TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT CURRENT_TIMESTAMP," +
                "UNIQUE(log_date, vehicle_id)," +
                "FOREIGN KEY(vehicle_id) REFERENCES vehicles(id))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1.
    }

    public long saveVehicle(Long id, String name, String registration) {
        ContentValues values = new ContentValues();
        values.put("name", name.trim());
        values.put("registration", registration.trim().toUpperCase());
        SQLiteDatabase db = getWritableDatabase();
        if (id == null) {
            return db.insertOrThrow("vehicles", null, values);
        }
        db.update("vehicles", values, "id=?", new String[]{String.valueOf(id)});
        return id;
    }

    public List<Vehicle> getVehicles() {
        List<Vehicle> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,name,registration FROM vehicles ORDER BY name,registration", null)) {
            while (c.moveToNext()) {
                list.add(new Vehicle(c.getLong(0), c.getString(1), c.getString(2)));
            }
        }
        return list;
    }

    public boolean vehicleHasLogs(long vehicleId) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM logs WHERE vehicle_id=?",
                new String[]{String.valueOf(vehicleId)})) {
            return c.moveToFirst() && c.getInt(0) > 0;
        }
    }

    public void deleteVehicle(long id) {
        getWritableDatabase().delete("vehicles", "id=?", new String[]{String.valueOf(id)});
    }

    public long saveLog(Long id, String date, long vehicleId, String driver,
                        double opening, double closing, String notes) {
        ContentValues values = new ContentValues();
        values.put("log_date", date);
        values.put("vehicle_id", vehicleId);
        values.put("driver", driver.trim());
        values.put("opening_km", opening);
        values.put("closing_km", closing);
        values.put("notes", notes.trim());

        SQLiteDatabase db = getWritableDatabase();
        if (id != null) {
            db.update("logs", values, "id=?", new String[]{String.valueOf(id)});
            return id;
        }

        LogRecord existing = getLogByVehicleDate(vehicleId, date);
        if (existing != null) {
            db.update("logs", values, "id=?", new String[]{String.valueOf(existing.id)});
            return existing.id;
        }
        return db.insertOrThrow("logs", null, values);
    }

    public LogRecord getLog(long id) {
        return firstLog("WHERE l.id=?", new String[]{String.valueOf(id)});
    }

    public LogRecord getLogByVehicleDate(long vehicleId, String date) {
        return firstLog("WHERE l.vehicle_id=? AND l.log_date=?",
                new String[]{String.valueOf(vehicleId), date});
    }

    private LogRecord firstLog(String where, String[] args) {
        String sql = baseLogSql() + " " + where + " LIMIT 1";
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            if (c.moveToFirst()) return readLog(c);
        }
        return null;
    }

    public Double getLastClosingBefore(long vehicleId, String date) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT closing_km FROM logs WHERE vehicle_id=? AND log_date<? " +
                        "ORDER BY log_date DESC,id DESC LIMIT 1",
                new String[]{String.valueOf(vehicleId), date})) {
            if (c.moveToFirst()) return c.getDouble(0);
        }
        return null;
    }

    public List<LogRecord> getLogs(Long vehicleId) {
        List<LogRecord> list = new ArrayList<>();
        String where = vehicleId == null ? "" : " WHERE l.vehicle_id=?";
        String[] args = vehicleId == null ? null : new String[]{String.valueOf(vehicleId)};
        try (Cursor c = getReadableDatabase().rawQuery(
                baseLogSql() + where + " ORDER BY l.log_date DESC,l.id DESC", args)) {
            while (c.moveToNext()) list.add(readLog(c));
        }
        return list;
    }

    public void deleteLog(long id) {
        getWritableDatabase().delete("logs", "id=?", new String[]{String.valueOf(id)});
    }

    private String baseLogSql() {
        return "SELECT l.id,l.log_date,l.vehicle_id,v.name,v.registration,l.driver," +
                "l.opening_km,l.closing_km,l.notes " +
                "FROM logs l JOIN vehicles v ON v.id=l.vehicle_id";
    }

    private LogRecord readLog(Cursor c) {
        return new LogRecord(
                c.getLong(0), c.getString(1), c.getLong(2), c.getString(3),
                c.getString(4), c.getString(5), c.getDouble(6), c.getDouble(7), c.getString(8));
    }

    public static class Vehicle {
        public final long id;
        public final String name;
        public final String registration;

        public Vehicle(long id, String name, String registration) {
            this.id = id;
            this.name = name;
            this.registration = registration;
        }

        @Override
        public String toString() {
            return name + " (" + registration + ")";
        }
    }

    public static class LogRecord {
        public final long id;
        public final String date;
        public final long vehicleId;
        public final String vehicleName;
        public final String registration;
        public final String driver;
        public final double openingKm;
        public final double closingKm;
        public final String notes;

        public LogRecord(long id, String date, long vehicleId, String vehicleName,
                         String registration, String driver, double openingKm,
                         double closingKm, String notes) {
            this.id = id;
            this.date = date;
            this.vehicleId = vehicleId;
            this.vehicleName = vehicleName;
            this.registration = registration;
            this.driver = driver == null ? "" : driver;
            this.openingKm = openingKm;
            this.closingKm = closingKm;
            this.notes = notes == null ? "" : notes;
        }

        public double distance() {
            return closingKm - openingKm;
        }
    }
}
