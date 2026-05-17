package com.example.graderegister;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String DATA_FILE = "scores.tsv";

    private final List<String[]> studentData = new ArrayList<String[]>();
    private final List<Integer> candidateIndexes = new ArrayList<Integer>();
    private ArrayAdapter<String> candidateAdapter;

    private EditText etName;
    private EditText etScore;
    private ListView lvCandidates;
    private int selectedIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etName = (EditText) findViewById(R.id.etName);
        etScore = (EditText) findViewById(R.id.etScore);
        lvCandidates = (ListView) findViewById(R.id.lvCandidates);

        candidateAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_activated_1, new ArrayList<String>());
        lvCandidates.setAdapter(candidateAdapter);
        lvCandidates.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectedIndex = candidateIndexes.get(position);
                lvCandidates.setItemChecked(position, true);
            }
        });

        etName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshCandidates();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        createLetterKeyboard((GridLayout) findViewById(R.id.gridLetters));
        createNumberKeyboard((GridLayout) findViewById(R.id.gridNumbers));

        findViewById(R.id.btnImport).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importFromClipboard();
            }
        });
        findViewById(R.id.btnWrite).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeScore();
            }
        });
        findViewById(R.id.btnExport).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportToClipboard();
            }
        });

        loadData();
    }

    private void createLetterKeyboard(GridLayout grid) {
        String[] letterKeys = {
                "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P",
                "A", "S", "D", "F", "G", "H", "J", "K", "L",
                "Z", "X", "C", "V", "B", "N", "M"
        };
        for (String key : letterKeys) {
            addKey(grid, key, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    etName.append(((Button) v).getText());
                }
            });
        }
        addKey(grid, "删除", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                backspace(etName);
            }
        });
    }

    private void createNumberKeyboard(GridLayout grid) {
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "删除"};
        for (String key : keys) {
            addKey(grid, key, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String txt = ((Button) v).getText().toString();
                    if ("删除".equals(txt)) {
                        backspace(etScore);
                    } else {
                        etScore.append(txt);
                    }
                }
            });
        }
    }

    private void addKey(GridLayout grid, String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(listener);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED);
        b.setLayoutParams(lp);
        grid.addView(b);
    }

    private void backspace(EditText et) {
        int len = et.getText().length();
        if (len > 0) {
            et.getText().delete(len - 1, len);
        }
    }

    private void importFromClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
        final String text = item.getText() == null ? "" : item.getText().toString();

        new AlertDialog.Builder(this)
                .setTitle("导入内容")
                .setMessage(text)
                .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                    parseAndStore(text);
                    saveData();
                    etName.setText("");
                    etScore.setText("");
                    Toast.makeText(MainActivity.this, "名单已暂存", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void parseAndStore(String text) {
        studentData.clear();
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split("\\t");
            String name = parts[0].trim();
            String score = parts.length > 1 ? parts[1].trim() : "";
            studentData.add(new String[]{name, score});
        }
    }

    private void refreshCandidates() {
        String query = etName.getText().toString().trim().toUpperCase(Locale.US);
        candidateAdapter.clear();
        candidateIndexes.clear();
        selectedIndex = -1;
        lvCandidates.clearChoices();

        if (query.isEmpty()) {
            candidateAdapter.notifyDataSetChanged();
            return;
        }

        for (int i = 0; i < studentData.size(); i++) {
            String name = studentData.get(i)[0];
            if (matchByOrderedContains(query, getNameInitials(name))) {
                candidateAdapter.add(name);
                candidateIndexes.add(i);
            }
        }
        candidateAdapter.notifyDataSetChanged();
    }

    private boolean matchByOrderedContains(String query, String initials) {
        int at = 0;
        for (int i = 0; i < query.length(); i++) {
            char ch = query.charAt(i);
            at = initials.indexOf(ch, at);
            if (at < 0) return false;
            at++;
        }
        return true;
    }

    private String getNameInitials(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            sb.append(getFirstLetter(name.charAt(i)));
        }
        return sb.toString().toUpperCase(Locale.US);
    }

    private char getFirstLetter(char c) {
        if (c < 128) return c;
        String initials = "ABCDEFGHJKLMNOPQRSTWXYZ";
        int[] secPosValue = {1601,1637,1833,2078,2274,2302,2433,2594,2787,3106,3212,3472,3635,3722,3730,3858,4027,4086,4390,4558,4684,4925,5249};
        try {
            byte[] bytes = String.valueOf(c).getBytes("GBK");
            if (bytes.length < 2) return "#".charAt(0);
            int high = (bytes[0] & 0xFF) - 160;
            int low = (bytes[1] & 0xFF) - 160;
            int secPos = high * 100 + low;
            for (int i = 0; i < secPosValue.length - 1; i++) {
                if (secPos >= secPosValue[i] && secPos < secPosValue[i + 1]) {
                    return initials.charAt(i);
                }
            }
        } catch (Exception ignored) {
        }
        return '#';
    }

    private void writeScore() {
        if (selectedIndex < 0 || selectedIndex >= studentData.size()) {
            Toast.makeText(this, "请先在候选列表中选择姓名", Toast.LENGTH_SHORT).show();
            return;
        }
        String score = etScore.getText().toString().trim();
        studentData.get(selectedIndex)[1] = score;
        saveData();
        etName.setText("");
        etScore.setText("");
        Toast.makeText(this, "写入成功", Toast.LENGTH_SHORT).show();
    }

    private void exportToClipboard() {
        StringBuilder sb = new StringBuilder();
        for (String[] row : studentData) {
            sb.append(row[0]).append("\t").append(row[1] == null ? "" : row[1]).append("\n");
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("名单", sb.toString()));
        Toast.makeText(this, "已导出到剪贴板", Toast.LENGTH_SHORT).show();
    }

    private void saveData() {
        try {
            FileOutputStream fos = openFileOutput(DATA_FILE, MODE_PRIVATE);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos, "UTF-8"));
            for (String[] row : studentData) {
                writer.write(row[0] + "\t" + (row[1] == null ? "" : row[1]));
                writer.newLine();
            }
            writer.close();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadData() {
        studentData.clear();
        try {
            FileInputStream fis = openFileInput(DATA_FILE);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\t", -1);
                String name = parts.length > 0 ? parts[0] : "";
                String score = parts.length > 1 ? parts[1] : "";
                if (!name.trim().isEmpty()) {
                    studentData.add(new String[]{name, score});
                }
            }
            reader.close();
        } catch (Exception ignored) {
        }
    }
}
