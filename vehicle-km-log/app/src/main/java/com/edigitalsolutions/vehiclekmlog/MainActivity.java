package com.edigitalsolutions.vehiclekmlog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_EXPORT = 7001;
    private static final int TEAL = Color.rgb(15, 118, 110);
    private static final int DARK = Color.rgb(17, 24, 39);
    private static final int MUTED = Color.rgb(75, 85, 99);
    private static final int LIGHT = Color.rgb(243, 244, 246);

    private DatabaseHelper db;
    private LinearLayout content;
    private List<DatabaseHelper.LogRecord> pendingExport = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DatabaseHelper(this);
        getWindow().setStatusBarColor(Color.rgb(13, 98, 91));
        buildShell();
        showEntry(null);
    }

    private void buildShell() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.WHITE);

        TextView title = text("Vehicle KM Log", 24, true);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(18), dp(18), dp(18), dp(18));
        title.setBackgroundColor(TEAL);
        page.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(8), dp(8), dp(8), dp(8));
        nav.setBackgroundColor(LIGHT);
        nav.addView(navButton("Daily Entry", v -> showEntry(null)), weight());
        nav.addView(navButton("History", v -> showHistory()), weight());
        nav.addView(navButton("Vehicles", v -> showVehicles()), weight());
        page.addView(nav);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(40));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(page);
    }

    private void showEntry(Long logId) {
        clear();
        heading(logId == null ? "Daily Kilometres" : "Edit Kilometres");
        List<DatabaseHelper.Vehicle> vehicles = db.getVehicles();
        if (vehicles.isEmpty()) {
            notice("Add at least one vehicle before recording kilometres.");
            content.addView(primaryButton("Add Vehicle", v -> showVehicles()));
            return;
        }

        final DatabaseHelper.LogRecord editing = logId == null ? null : db.getLog(logId);
        final Long[] editingId = {editing == null ? null : editing.id};

        label("Date");
        EditText date = edit(false);
        date.setFocusable(false);
        date.setText(editing == null ? today() : editing.date);
        date.setOnClickListener(v -> pickDate(date));
        content.addView(date);

        label("Vehicle");
        Spinner vehicleSpinner = new Spinner(this);
        vehicleSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, vehicles));
        content.addView(vehicleSpinner, matchWrap());

        label("Driver / Employee");
        EditText driver = edit(false);
        if (editing != null) driver.setText(editing.driver);
        content.addView(driver);

        label("Opening KM");
        EditText opening = edit(true);
        content.addView(opening);

        label("Closing KM");
        EditText closing = edit(true);
        content.addView(closing);

        TextView distance = text("Distance: 0 km", 18, true);
        distance.setTextColor(TEAL);
        distance.setPadding(0, dp(14), 0, dp(8));
        content.addView(distance);

        label("Notes");
        EditText notes = edit(false);
        notes.setMinLines(3);
        notes.setGravity(Gravity.TOP);
        if (editing != null) notes.setText(editing.notes);
        content.addView(notes);

        TextWatcher distanceWatcher = new SimpleWatcher(() -> updateDistance(opening, closing, distance));
        opening.addTextChangedListener(distanceWatcher);
        closing.addTextChangedListener(distanceWatcher);

        final boolean[] firstLoad = {true};
        Runnable loadRecord = () -> {
            DatabaseHelper.Vehicle vehicle = (DatabaseHelper.Vehicle) vehicleSpinner.getSelectedItem();
            if (vehicle == null) return;
            DatabaseHelper.LogRecord record = db.getLogByVehicleDate(vehicle.id, date.getText().toString());
            if (record != null && (editingId[0] == null || record.id != editingId[0])) {
                editingId[0] = record.id;
                driver.setText(record.driver);
                opening.setText(formatKm(record.openingKm));
                closing.setText(formatKm(record.closingKm));
                notes.setText(record.notes);
                toast("Existing entry loaded for editing.");
            } else if (editingId[0] == null && (firstLoad[0] || opening.getText().length() == 0)) {
                Double previous = db.getLastClosingBefore(vehicle.id, date.getText().toString());
                opening.setText(previous == null ? "" : formatKm(previous));
                closing.setText("");
            }
            firstLoad[0] = false;
        };

        vehicleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { loadRecord.run(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        date.addTextChangedListener(new SimpleWatcher(loadRecord));

        if (editing != null) {
            for (int i = 0; i < vehicles.size(); i++) {
                if (vehicles.get(i).id == editing.vehicleId) vehicleSpinner.setSelection(i);
            }
            opening.setText(formatKm(editing.openingKm));
            closing.setText(formatKm(editing.closingKm));
            updateDistance(opening, closing, distance);
        }

        Button save = primaryButton(editing == null ? "Save Daily Entry" : "Save Changes", v -> {
            DatabaseHelper.Vehicle vehicle = (DatabaseHelper.Vehicle) vehicleSpinner.getSelectedItem();
            Double open = parseKm(opening);
            Double close = parseKm(closing);
            if (vehicle == null || date.getText().length() == 0 || open == null || close == null) {
                toast("Complete the date, vehicle, opening KM and closing KM.");
                return;
            }
            if (close < open) {
                closing.setError("Closing KM cannot be less than opening KM");
                return;
            }
            try {
                db.saveLog(editingId[0], date.getText().toString(), vehicle.id,
                        driver.getText().toString(), open, close, notes.getText().toString());
                toast("Kilometres saved.");
                showHistory();
            } catch (Exception e) {
                toast("Could not save: " + e.getMessage());
            }
        });
        save.setPadding(dp(12), dp(15), dp(12), dp(15));
        content.addView(save, topMargin(18));
    }

    private void showHistory() {
        clear();
        heading("History");
        List<DatabaseHelper.Vehicle> vehicles = db.getVehicles();

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button export = primaryButton("Export Excel", v -> startExport());
        Button refresh = secondaryButton("Refresh", v -> showHistory());
        actions.addView(export, weight());
        actions.addView(refresh, weightWithLeftMargin());
        content.addView(actions);

        label("Filter by vehicle");
        List<String> filterLabels = new ArrayList<>();
        filterLabels.add("All Vehicles");
        for (DatabaseHelper.Vehicle vehicle : vehicles) filterLabels.add(vehicle.toString());
        Spinner filter = new Spinner(this);
        filter.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, filterLabels));
        content.addView(filter, matchWrap());

        LinearLayout listHolder = new LinearLayout(this);
        listHolder.setOrientation(LinearLayout.VERTICAL);
        content.addView(listHolder);

        Runnable render = () -> {
            listHolder.removeAllViews();
            Long vehicleId = filter.getSelectedItemPosition() == 0 ? null : vehicles.get(filter.getSelectedItemPosition() - 1).id;
            List<DatabaseHelper.LogRecord> logs = db.getLogs(vehicleId);
            if (logs.isEmpty()) {
                TextView empty = noticeView("No kilometre records saved yet.");
                listHolder.addView(empty);
                return;
            }
            for (DatabaseHelper.LogRecord log : logs) listHolder.addView(historyCard(log), topMargin(10));
        };
        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { render.run(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        render.run();
    }

    private View historyCard(DatabaseHelper.LogRecord log) {
        LinearLayout card = card();
        TextView main = text(log.date + "  •  " + log.vehicleName + " (" + log.registration + ")", 16, true);
        main.setTextColor(DARK);
        card.addView(main);
        TextView kms = text(formatKm(log.openingKm) + " → " + formatKm(log.closingKm) +
                "   |   " + formatKm(log.distance()) + " km", 16, false);
        kms.setTextColor(TEAL);
        kms.setPadding(0, dp(6), 0, dp(3));
        card.addView(kms);
        if (!log.driver.isEmpty()) card.addView(text("Driver: " + log.driver, 14, false));
        if (!log.notes.isEmpty()) card.addView(text("Notes: " + log.notes, 14, false));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button edit = secondaryButton("Edit", v -> showEntry(log.id));
        Button delete = dangerButton("Delete", v -> confirmDeleteLog(log));
        buttons.addView(edit, weight());
        buttons.addView(delete, weightWithLeftMargin());
        card.addView(buttons, topMargin(10));
        return card;
    }

    private void confirmDeleteLog(DatabaseHelper.LogRecord log) {
        new AlertDialog.Builder(this)
                .setTitle("Delete entry?")
                .setMessage(log.date + " - " + log.registration)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    db.deleteLog(log.id);
                    toast("Entry deleted.");
                    showHistory();
                }).show();
    }

    private void showVehicles() {
        clear();
        heading("Vehicles");
        final Long[] editId = {null};

        label("Vehicle name / description");
        EditText name = edit(false);
        content.addView(name);
        label("Registration number");
        EditText registration = edit(false);
        registration.setAllCaps(true);
        content.addView(registration);

        Button save = primaryButton("Add Vehicle", null);
        content.addView(save, topMargin(16));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list, topMargin(10));

        Runnable render = new Runnable() {
            @Override public void run() {
                list.removeAllViews();
                List<DatabaseHelper.Vehicle> vehicles = db.getVehicles();
                if (vehicles.isEmpty()) {
                    list.addView(noticeView("No vehicles added yet."));
                    return;
                }
                for (DatabaseHelper.Vehicle vehicle : vehicles) {
                    LinearLayout row = card();
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    TextView info = text(vehicle.name + "\n" + vehicle.registration, 16, true);
                    info.setTextColor(DARK);
                    info.setOnClickListener(v -> {
                        editId[0] = vehicle.id;
                        name.setText(vehicle.name);
                        registration.setText(vehicle.registration);
                        save.setText("Update Vehicle");
                    });
                    row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    Button del = dangerButton("Delete", v -> {
                        if (db.vehicleHasLogs(vehicle.id)) {
                            toast("This vehicle has history and cannot be deleted.");
                            return;
                        }
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Delete vehicle?")
                                .setMessage(vehicle.toString())
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Delete", (d, w) -> { db.deleteVehicle(vehicle.id); run(); })
                                .show();
                    });
                    row.addView(del);
                    list.addView(row, topMargin(10));
                }
            }
        };

        save.setOnClickListener(v -> {
            if (name.getText().toString().trim().isEmpty() || registration.getText().toString().trim().isEmpty()) {
                toast("Enter the vehicle name and registration number.");
                return;
            }
            try {
                db.saveVehicle(editId[0], name.getText().toString(), registration.getText().toString());
                toast(editId[0] == null ? "Vehicle added." : "Vehicle updated.");
                editId[0] = null;
                name.setText("");
                registration.setText("");
                save.setText("Add Vehicle");
                render.run();
            } catch (Exception e) {
                toast("Registration number already exists.");
            }
        });
        render.run();
    }

    private void startExport() {
        pendingExport = db.getLogs(null);
        if (pendingExport.isEmpty()) {
            toast("There is no history to export.");
            return;
        }
        Intent intent = new Intent("android.intent.action.CREATE_DOCUMENT");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        intent.putExtra(Intent.EXTRA_TITLE, "Vehicle_KM_History_" + today() + ".xlsx");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        List<XlsxExporter.ExportRow> rows = new ArrayList<>();
        for (DatabaseHelper.LogRecord log : pendingExport) {
            rows.add(new XlsxExporter.ExportRow(log.date, log.vehicleName, log.registration,
                    log.driver, log.openingKm, log.closingKm, log.distance(), log.notes));
        }
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new IllegalStateException("Could not open selected file");
            XlsxExporter.write(output, rows);
            toast("Excel file exported.");
        } catch (Exception e) {
            toast("Export failed: " + e.getMessage());
        }
    }

    private void pickDate(EditText field) {
        Calendar calendar = Calendar.getInstance();
        String[] parts = field.getText().toString().split("-");
        if (parts.length == 3) {
            try { calendar.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2])); }
            catch (Exception ignored) {}
        }
        new DatePickerDialog(this, (view, year, month, day) -> {
            field.setText(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day));
            field.clearFocus();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDistance(EditText opening, EditText closing, TextView label) {
        Double open = parseKm(opening);
        Double close = parseKm(closing);
        if (open == null || close == null) label.setText("Distance: 0 km");
        else if (close < open) label.setText("Distance: Invalid closing KM");
        else label.setText("Distance: " + formatKm(close - open) + " km");
    }

    private Double parseKm(EditText edit) {
        try { return Double.parseDouble(edit.getText().toString().trim().replace(',', '.')); }
        catch (Exception e) { return null; }
    }

    private String formatKm(double value) {
        if (Math.rint(value) == value) return String.format(Locale.US, "%.0f", value);
        return String.format(Locale.US, "%.2f", value);
    }

    private String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private void clear() { content.removeAllViews(); }

    private void heading(String value) {
        TextView view = text(value, 22, true);
        view.setTextColor(DARK);
        view.setPadding(0, dp(4), 0, dp(10));
        content.addView(view);
    }

    private void label(String value) {
        TextView view = text(value, 14, true);
        view.setTextColor(MUTED);
        view.setPadding(0, dp(12), 0, dp(5));
        content.addView(view);
    }

    private void notice(String value) { content.addView(noticeView(value)); }

    private TextView noticeView(String value) {
        TextView view = text(value, 16, false);
        view.setTextColor(MUTED);
        view.setPadding(dp(14), dp(14), dp(14), dp(14));
        view.setBackground(rounded(LIGHT, 12));
        return view;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(MUTED);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private EditText edit(boolean number) {
        EditText edit = new EditText(this);
        edit.setTextSize(17);
        edit.setPadding(dp(12), dp(10), dp(12), dp(10));
        edit.setBackground(rounded(Color.rgb(249, 250, 251), 10));
        if (number) edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return edit;
    }

    private Button navButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTextColor(DARK);
        button.setBackground(rounded(Color.WHITE, 10));
        button.setOnClickListener(listener);
        return button;
    }

    private Button primaryButton(String label, View.OnClickListener listener) {
        Button button = baseButton(label, listener);
        button.setTextColor(Color.WHITE);
        button.setBackground(rounded(TEAL, 10));
        return button;
    }

    private Button secondaryButton(String label, View.OnClickListener listener) {
        Button button = baseButton(label, listener);
        button.setTextColor(DARK);
        button.setBackground(rounded(Color.rgb(229, 231, 235), 10));
        return button;
    }

    private Button dangerButton(String label, View.OnClickListener listener) {
        Button button = baseButton(label, listener);
        button.setTextColor(Color.WHITE);
        button.setBackground(rounded(Color.rgb(185, 28, 28), 10));
        return button;
    }

    private Button baseButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        if (listener != null) button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(Color.rgb(249, 250, 251), 12));
        return card;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private LinearLayout.LayoutParams weightWithLeftMargin() {
        LinearLayout.LayoutParams p = weight();
        p.leftMargin = dp(8);
        return p;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams topMargin(int dp) {
        LinearLayout.LayoutParams p = matchWrap();
        p.topMargin = this.dp(dp);
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }

    private static class SimpleWatcher implements TextWatcher {
        private final Runnable runnable;
        SimpleWatcher(Runnable runnable) { this.runnable = runnable; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { runnable.run(); }
        @Override public void afterTextChanged(Editable s) {}
    }
}
