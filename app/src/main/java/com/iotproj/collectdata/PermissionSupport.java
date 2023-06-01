/* 스마트폰에서 앱 구동 시 각종 권한 요청 등 권한을 관리하는 Class
   필요한 권한이 있으면 AndroidManifest.xml 파일에 먼저 권한 추가 후
   하단 요청해야 할 권한들 목록에 추가해주면 간편하게 권한 획득이 가능하다.
* */

package com.iotproj.collectdata;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

// WIFI 센싱에 필요한 권한을 관리하는 부분
public class PermissionSupport {
    private Context context;
    private Activity activity;

    //요청해야 할 권한들
    private String[] permissions = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
    };

    private List permissionList;
    private final int MULTIPLE_PERMISSIONS = 1023;

    public PermissionSupport(Activity _activity, Context _context) {
        this.activity = _activity;
        this.context = _context;
    }

    //허용되지 않은 권한이 있는지 확인 (전부 허용되었다면 true)
    public boolean checkPermission() {
        int result;
        permissionList = new ArrayList<>();

        //먼저 모든 권한에 대해 혀용되지 않은 권한만 확인
        for(String pm : permissions){
            result = ContextCompat.checkSelfPermission(context, pm);
            if (result != PackageManager.PERMISSION_GRANTED){
                permissionList.add(pm);
            }
        }
        if (!permissionList.isEmpty()) return false;
        else return true;
    }

    //배열에 있는 허용되지 않은 권한에 대해 사용자에게 요청
    public void requestPermission() {
        ActivityCompat.requestPermissions(activity, (String[]) permissionList.toArray(new String[permissionList.size()]), MULTIPLE_PERMISSIONS);
    }

    //요청한 권한의 결과 (전부 허용하였다면 true)
    public boolean permissionResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == MULTIPLE_PERMISSIONS && (grantResults.length > 0)) {
            for (int i = 0; i< grantResults.length; i++) {
                if (grantResults[i] == -1) return false;
            }
        }
        return true;
    }
}
