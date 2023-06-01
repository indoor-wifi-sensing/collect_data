package com.iotproj.collectdata;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // Wifi 스캐닝, 권한 획득 관련 변수
    private PermissionSupport permission;
    WifiManager wifiManager;
    BroadcastReceiver wifiScanReceiver;

    // DB 관리 관련 변수
    SQLiteDatabase db, db2;
    NewSQLiteOpenHelper dbHelper;
    NewSQLiteOpenHelper2 dbHelper2;

    //레이아웃 컨트롤 관련 변수
    EditText inputLocate;
    Button scanBtn,submitBtn, dbmBtn;
    ListView wifiList;
    List<ScanResult> wifiResult;

    // 딥러닝 전용 계산 관련 변수
    String[][] wifiData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 사용자에게 필요한 권한 요청
        permissionCheck();

        inputLocate = findViewById(R.id.inputLocation);
        scanBtn = findViewById(R.id.scanNow);
        submitBtn = findViewById(R.id.submitDB);
        dbmBtn = findViewById(R.id.calDbm);

        wifiList = findViewById(R.id.wifiList);
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        // DB 2개 전부 열고 이미 있는 person.db 복사 후 배열에 넣기
        dbHelper = new NewSQLiteOpenHelper(MainActivity.this, "person.db", null, 1);
        dbHelper2 = new NewSQLiteOpenHelper2(MainActivity.this, "deepLearn.db", null, 1);
        copyDatabaseFromAssets("person.db");
        wifiData = select();

        // 시스템에서 각종 변경 정보를 인식했을 때, 그 중에서도 Wifi 스캔 값이 변경되었을 경우 동작
        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                if (success) {
                    scanSuccess();
                    Log.e("wifi","scan Success!!!!!");
                    wifiAnalyzer();
                }
                else {
                    scanFailure();
                    Log.e("wifi","scan Failure.....");
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        this.registerReceiver(wifiScanReceiver, intentFilter);

        // Scan Now 버튼을 눌렀을 때 작동
        scanBtn.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("MissingPermission")
            @Override
            public void onClick(View v) {
                boolean success = wifiManager.startScan();
                if (!success) {
                    scanFailure();
                }
                wifiResult = wifiManager.getScanResults();
            }
        });

        // DB 제출 버튼을 눌렀을 때 작동
        submitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String targetPos = inputLocate.getText().toString();
                if (targetPos.length() == 0) {
                    Toast.makeText(MainActivity.this, "Target Position should not empty", Toast.LENGTH_LONG).show();
                    return;
                }

                //경고창을 띄울 공간
                AlertDialog.Builder myAlertBuilder = new AlertDialog.Builder(MainActivity.this);
                myAlertBuilder.setTitle("위치 확인");
                myAlertBuilder.setMessage("직전에 WiFi를 스캔한 위치가 " + targetPos + "이(가) 맞습니까? \n 이 작업은 되돌릴 수 없습니다.");
                myAlertBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // OK를 눌렀을 경우에 행동할 내용들
                        int totDelete = delete(targetPos);

                        // DB 업로드 시 스캔 결과 정렬
                        wifiListSorter();
                        int count = 0;
                        for (ScanResult choseWifi : wifiResult) {
                            if (count >= 7) break;
                            count += 1;
                            insert(choseWifi.BSSID, choseWifi.level, targetPos);
                        }

                        Toast.makeText(MainActivity.this, totDelete + "행 데이터 제거, " + count + "행 데이터 추가", Toast.LENGTH_SHORT).show();
                        //
                    }
                });
                myAlertBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(MainActivity.this, "DB upload canceled.", Toast.LENGTH_LONG).show();
                    }
                });
                myAlertBuilder.show();
            }
        });

        // dbm 계산해서 리턴할 데이터 준비하기
        dbmBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String currentPos = inputLocate.getText().toString();
                if (currentPos.length() == 0) {
                    Toast.makeText(MainActivity.this, "Target Position should not empty", Toast.LENGTH_LONG).show();
                    return;
                }

                // wifiData : 이미 있는 DB에서 복사된 배열
                // wifiResult : 센싱한 wifi의 결과값 (List<ScanResult>)
                // wifiSensing : wifiResult에서 dBm 순서대로 7개 추린 값 (원코드 wifi)
                String[][] wifiSensing = new String[7][2];
                String pos = wifiData[0][2];
                wifiListSorter();
                int ii = 0;
                for (ScanResult choseWifi : wifiResult) {
                    if (ii >= 7) break;
                    wifiSensing[ii][0] = choseWifi.BSSID;
                    wifiSensing[ii][1] = Integer.toString(choseWifi.level);
                    ii += 1;
                }

                //여기부터 가장 가까운 거리 계산
                double[] disList = new double[600]; // 각 위치값에
                String[] disPos = new String[600]; // DB에 존재하는 모든 String 위치를 순차적으로 넣음
                int count = 0;
                int num = 0;
                disPos[count] = pos;

                // 전체 데이터를 순차적으로 확인
                for (String[] WiFi : wifiData) {
                    // Log.e("pos", pos);
                    if(WiFi[2] == null) {
                        break;
                    }
                    if (!(WiFi[2].equals(pos))) {
                        disList[count] = disList[count] / num;
                        num = 0;
                        count++;
                        pos = WiFi[2];
                        disPos[count] = pos;
                        // Log.e("dispos", disPos[count]);
                    }
                    num++;
                    int checkPos = 0;

                    //센싱된 모든 Wifi에 관하여 데이터 추출
                    for (ScanResult choseWifi : wifiResult) {
                        String MAC = choseWifi.BSSID;
                        // 확인중인 위치와 mac과 센싱된 wifi의 mac값이 같다면
                        if(MAC.equals((WiFi[0]))) {
                            int a = Integer.parseInt(WiFi[1]) - choseWifi.level;
                            a = a*a;
                            disList[count] += Math.sqrt(a);
                            checkPos = 1;
                            break;
                        }
                    }
                    if(checkPos == 0) {
                        int a = Integer.parseInt(WiFi[1]);
                        a = a*a;
                        disList[count] += Math.sqrt(a);
                    }
                }

                double min = disList[0];
                int index = 0;
                for (int i = 1; i < disList.length; i++) {
                    Log.e("dislist", i + " | " + disList[i]+ " | " + disPos[i] + " | " + min);
                    if(disList[i] == 0) {
                        break;
                    }
                    if (disList[i] < min) {
                        min = disList[i];
                        index = i;
                    }
                }
                //Toast.makeText(MainActivity.this, "distance = " + disPos[index], Toast.LENGTH_LONG).show();
                //여기까지 계산, disPos[index]

                String estimatePos = disPos[index]; // 리턴할 예측된 위치 이름
                double estimateDist = disList[index]; // 리턴할 거리
                int[] dbmDist = new int[7]; // 리턴할 리스트, DB값이 5개 아래면 0 리턴
                int cnt = 0;
                for (String[] WiFi : wifiData) {
                    if (WiFi[2] == null) break;
                    if (!WiFi[2].equals(estimatePos)) continue;
                    // 여기까지 왔으면 보고자 하는 데이터의 위치정보 DB를 확인중
                    dbmDist[cnt] = Integer.parseInt(WiFi[1]);
                    for (ScanResult choseWifi : wifiResult) {
                        String MAC = choseWifi.BSSID;
                        if(MAC.equals(WiFi[0]) ) {
                            dbmDist[cnt] -= choseWifi.level;
                            cnt += 1;
                            break;
                        }
                    }
                }
                // 여기까지 오면 dbmDist에 모든 int 차이값이 저장됨

                insert2(currentPos, estimatePos, dbmDist, estimateDist);
                Toast.makeText(MainActivity.this, "실제 위치 : " + currentPos + " | 예측된 위치 : " +
                        estimatePos + " | 예상 정확도 : " + estimateDist , Toast.LENGTH_LONG).show();
            }
        });
    }

    //존재하는 DB 복사하기
    private void copyDatabaseFromAssets(String copyFile) {
        try {
            InputStream inputStream = getAssets().open("person.db");
            String outFileName = getDatabasePath(copyFile).getPath();
            OutputStream outputStream = new FileOutputStream(outFileName);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            outputStream.flush();
            outputStream.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //===========================================
    //=========== SQLite DB 명령어 영역 ===========
    //===========================================

    // DB에 존재하는 모든 데이터 array에 복사
    private String[][] select() {
        db = dbHelper.getReadableDatabase();
        String[] columns = {"mac", "rss", "pos"};
        String[][] result = new String[600][3];
        int cursorPos = 0;

        //Cursor cursor = db.query("fingerprint", columns, null, null, null, null, null);
        Cursor cursor = db.rawQuery("Select * From fingerprint", null);
        if (cursor != null && cursor.moveToFirst()) {
            do {
                String macValue = cursor.getString(0);
                int rssValue = cursor.getInt(1);
                String posValue = cursor.getString(2);

                result[cursorPos][0] = macValue;
                result[cursorPos][1] = Integer.toString(rssValue);
                result[cursorPos][2] = posValue;

                cursorPos += 1;
            } while (cursor.moveToNext());
        }

        Log.i("result", result.toString());
        return result;
    }

    private void insert(String mac, int rss, String pos) {
        db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put("mac", mac);
        values.put("rss", rss);
        values.put("pos", pos);
        db.insert("fingerprint", null, values);
    }

    private void insert2(String currentpos, String targetpos, int[] diff, double eucdist) {
        db2 = dbHelper2.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put("currentpos", currentpos);
        values.put("targetpos", targetpos);
        values.put("diff1", diff[0]);
        values.put("diff2", diff[1]);
        values.put("diff3", diff[2]);
        values.put("diff4", diff[3]);
        values.put("diff5", diff[4]);
        values.put("diff6", diff[5]);
        values.put("diff7", diff[6]);
        values.put("eucdist", Math.round(eucdist*10)/10.0);
        db2.insert("newfinger", null, values);

    }

    // 현재 위치에 등록된 정보를 전부 삭제 (DB 업로드 시 delete 후 insert)
    private int delete(String pos) {
        db = dbHelper.getWritableDatabase();
        int deleteRows = db.delete("fingerprint", "pos=?", new String[]{pos});
        return deleteRows;
    }

    private int delete2(String pos) {
        db2 = dbHelper2.getWritableDatabase();
        int deleteRows = db2.delete("newfinger", "currentpos=?", new String[]{pos});
        return deleteRows;
    }

    //===========================================
    //========== WiFi 스캐닝 컨트롤 영역 ===========
    //===========================================
    //수집한 Wifi 정보를 배열에 뿌리는 역할
    private void wifiAnalyzer() {
        List<String> list = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, list);
        String mac, dbm, freq;
        for (ScanResult choseWifi : wifiResult) {
            mac = choseWifi.BSSID;
            dbm = Integer.toString(choseWifi.level) + "dBm";
            freq = Integer.toString(choseWifi.frequency);

            String completeInfo = mac + " | " + dbm + " | " + freq;
            list.add(completeInfo);
        }
        wifiList.setAdapter(adapter);
    }

    //Wifi 정보 스캔에 성공했을 경우에 행동할 것들
    private void scanSuccess() {
        @SuppressLint("MissingPermission") List<ScanResult> results = wifiManager.getScanResults();
        Log.e("wifi", results.toString());
        Toast.makeText(this.getApplicationContext(), "Wifi Scan Success", Toast.LENGTH_LONG).show();
    }

    //Wifi 정보 스캔에 실패했을 경우에 행동할 것들
    private void scanFailure() {
        @SuppressLint("MissingPermission") List<ScanResult> results = wifiManager.getScanResults();
        Log.e("wifi", results.toString());
        Toast.makeText(this.getApplicationContext(), "Wifi Scan Failure, Old Information may appear.", Toast.LENGTH_LONG).show();
    }

    private void wifiListSorter() {
        Comparator<ScanResult> comparator = new Comparator<ScanResult>() {
            @Override
            public int compare(ScanResult o1, ScanResult o2) {
                return o2.level - o1.level;
            }
        };
        Collections.sort(wifiResult, comparator);
    }

    //===========================================
    //=========== 필요한 권한 확인 영역 ============
    //===========================================
    // 필요한 권한 체크
    private void permissionCheck() {
        permission = new PermissionSupport(this, this);
        if (!permission.checkPermission()) {
            permission.requestPermission();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (!permission.permissionResult(requestCode, permissions, grantResults)) {
            permission.requestPermission();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}