package com.shashipage.slm_mylocation;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.Settings;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;

import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

public class Main extends AppCompatActivity implements OnMapReadyCallback, LocationListener {
    //------permission所需要申請的權限數組--------------------------
    private static final String[][] permissionsArray = new String[][]{
            {Manifest.permission.ACCESS_FINE_LOCATION, "僅GPS定位"},
            {Manifest.permission.ACCESS_COARSE_LOCATION, "一般定位"},
            {Manifest.permission.INTERNET, "網路"},
            {Manifest.permission.ACCESS_NETWORK_STATE, "上網"}
//            {Manifest.permission.WRITE_EXTERNAL_STORAGE, "外部存取"}
    };

    private List<String> permissionsList = new ArrayList<String>();
    //申請權限後的返回碼
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 101;
    private static final int REQUEST_CODE_ASK_PERMISSIONS = 1;
    //------------------------------------------------------------------

    private GoogleMap mMap;
    private LocationManager locationManager;
    private LatLng VGPS=new LatLng(24.137622102040563, 120.68662252293544);
    private float mapzoom=16;
    private String provider; //提供資料
    //------多久更新一次位置資訊
    private long minTime = 5000;// ms
    private float minDist = 30.0f;// meter
    private ArrayList<Map<String, String>> mList;
    private int total;
    private int t_total;
    private String m_location="24.137622102040563, 120.68662252293544";
    private BitmapDescriptor image_des;
    private Spinner loc_choose;
    private TextView range_t;
    private String[] loc_Text;
    private String[] loc_Value;
    private String loc_type="food";
    private int loc_range=500;
    private boolean searchOn=false;
    private Button btn_serchOn,btn_serchOff;
    private LinearLayout loading;

    //------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        checkRequiredPermission(this);
        enableStrictMode(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        //------------設定MapFragment-----------------------------------
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        //-------------------------------------------------------
//        u_checkgps();  //檢查GPS是否開啟
        setupViewComponent();
    }

    private void setupViewComponent() {
        if(getString(R.string.google_maps_key).equals("Your Key")) Toast.makeText(getApplicationContext(),getString(R.string.noKey),Toast.LENGTH_SHORT).show();
        loc_Text=getResources().getStringArray(R.array.map_locText);
        loc_Value=getResources().getStringArray(R.array.map_locValue);
        btn_serchOn=(Button)findViewById(R.id.findNearLocation);
        btn_serchOff=(Button)findViewById(R.id.c_findNearLocation);
        loading=(LinearLayout)findViewById(R.id.loading);
    }

    private void init_app() {    // 檢查定位是否成功
        try {
            if (initLocationProvider()) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    nowaddress();
                } else {}
            }
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(),getString(R.string.noGPS),Toast.LENGTH_LONG).show();
            //對話方塊啟用GPS
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("GPS未開啟")
                    .setMessage("GPS目前狀態尚未啟用.\n" + "請先開啟定位!,再次執行APP!")
                    .setPositiveButton("離開再次執行", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            //使用Intent物件啟動設定程式來更改GPS設定
                            Intent i = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivity(i);
                            finish();
                        }
                    }).setNegativeButton("不啟用", null).create().show();
            return;
        }
    }

    private void nowaddress() {
        if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            Location location = locationManager.getLastKnownLocation(provider);
/******************************************************** */
            updateWithNewLocation(location); //*****開啟GPS定位
/********************************************************* */
            return;
        }
        boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        Location location = null;
        if (!(isGPSEnabled || isNetworkEnabled))
            Toast.makeText(getApplicationContext(), "GPS 未開啟", Toast.LENGTH_SHORT).show();
        else {
            if (isNetworkEnabled) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        minTime, minDist, this);
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
//                tmsg.setText("使用網路GPS");
            }
            if (isGPSEnabled) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        minTime, minDist, this);
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
//                tmsg.setText("使用精確GPS");
            }
        }
    }

    private boolean initLocationProvider() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            provider = LocationManager.GPS_PROVIDER;
            return true;
        } else {
            return false;
        }
    }

    /*** 位置變更狀態監視*/
//    LocationListener locationListener = new LocationListener() {
//        @Override
//        public void onLocationChanged(Location location) {
//            updateWithNewLocation(location);
////            tmsg.setText("目前Zoom:" + map.getCameraPosition().zoom);
//        }
//
//        @Override
//        public void onStatusChanged(String provider, int status, Bundle extras) {
//            switch (status) {
//                case LocationProvider.OUT_OF_SERVICE:
////                    tmsg.setText("Out of Service");
//                    break;
//                case LocationProvider.TEMPORARILY_UNAVAILABLE:
////                    tmsg.setText("Temporarily Unavailable");
//                    break;
//                case LocationProvider.AVAILABLE:
////                    tmsg.setText("Available");
//                    break;
//            }
//        }
//
//        @Override
//        public void onProviderEnabled(String provider) {
////            tmsg.setText("目前Zoom:" + map.getCameraPosition().zoom);
//        }
//
//        @Override
//        public void onProviderDisabled(String provider) {
//            updateWithNewLocation(null);
//        }
//    };

    private void checkRequiredPermission(Main mapsActivity) {
        //        String permission_check= String[i][0] permission;
        for (int i = 0; i < permissionsArray.length; i++) {
            if (ContextCompat.checkSelfPermission(mapsActivity, permissionsArray[i][0]) != PackageManager.PERMISSION_GRANTED) {
                permissionsList.add(permissionsArray[i][0]);
            }
        }
        if (permissionsList.size() != 0) {
            ActivityCompat.requestPermissions(mapsActivity, permissionsList.toArray(new
                    String[permissionsList.size()]), REQUEST_CODE_ASK_PERMISSIONS);
        }
    }

    private void enableStrictMode(Main mapsActivity) {
        //--------抓取遠端資料庫設定執行續(官方)----------------------只要連MySQL都要加入
        StrictMode.setThreadPolicy(new
                StrictMode.
                        ThreadPolicy.Builder().
                detectDiskReads().
                detectDiskWrites().
                detectNetwork().
                penaltyLog().
                build());
        StrictMode.setVmPolicy(
                new
                        StrictMode.
                                VmPolicy.
                                Builder().
                        detectLeakedSqlLiteObjects().
                        penaltyLog().
                        penaltyDeath().
                        build());
    }



    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        //開啟GoogleMap 拖曳功能
        mMap.getUiSettings().setScrollGesturesEnabled(true);
        //右下角導覽及開啟GoogleMap 功能
        mMap.getUiSettings().setMapToolbarEnabled(true);
        //左上角顯示指北針，需旋轉畫面才會出現
        mMap.getUiSettings().setCompassEnabled(true);
        //右下角顯示縮放按鈕的放大縮小功能
        mMap.getUiSettings().setZoomControlsEnabled(true);
        //map.addMarker(new MarkerOptions().position(VGPS).title("中區職訓局"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(VGPS, mapzoom));
        //----------取得定位許可-----------------------
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            //----顯示我的位置ICO-------
            mMap.setMyLocationEnabled(true);
        } else {
//            Toast.makeText(getApplicationContext(), "GPS定位權限未允許", Toast.LENGTH_LONG).show();
        }

    }

    @Override
    public void onLocationChanged(Location location) {
        updateWithNewLocation(location);
    }

    private void updateWithNewLocation(Location location) {
        String where = "";
        if (location != null) {
            double lat = location.getLatitude();// 緯度
            double lng = location.getLongitude();// 經度
//            float speed = location.getSpeed();// 速度
//            long time = location.getTime();// 時間
//            double altitude = location.getAltitude();//海拔
            VGPS=new LatLng(lat, lng);
            m_location=lat+","+lng;
            cameraFocusOnMe(lat, lng);

        } else {
            where = "*位置訊號消失*";
        }

    }

    private void cameraFocusOnMe(double lat, double lng) {
        CameraPosition camPosition = new CameraPosition.Builder()
                .target(new LatLng(lat, lng))
                .zoom(mMap.getCameraPosition().zoom)
                .build();
        /* 移動地圖鏡頭 */
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(camPosition));
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        switch (status) {
            case LocationProvider.OUT_OF_SERVICE:
//                    tmsg.setText("Out of Service");
                break;
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
//                    tmsg.setText("Temporarily Unavailable");
                break;
            case LocationProvider.AVAILABLE:
//                    tmsg.setText("Available");
                break;
        }
    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {
        updateWithNewLocation(null);
    }

    //------載入opendata(Place Api)------
    private void u_importopendata() { //下載Opendata
        String google_place_key = getString(R.string.googke_place_key);
        try {
            String Task_opendata
                    = new TransTask().execute("https://maps.googleapis.com/maps/api/place/nearbysearch/json?location="+m_location+"&radius="+loc_range+"&types="+loc_type+"&sensor=true&key="+google_place_key).get();//     //旅館民宿 - 觀光資訊資料庫
//-------解析 json   帶有多層結構-------------
            mList = new ArrayList<Map<String, String>>();
            JSONObject json_obj1 = new JSONObject(Task_opendata);
            JSONArray info=json_obj1.getJSONArray("results");
            total = 0;
            t_total = info.length(); //總筆數
            //-----開始逐筆轉換-----
            total = info.length();
//            t_count.setText(getString(R.string.ncount) + total);
            for (int i = 0; i < info.length(); i++) {
                Map<String, String> item = new HashMap<String, String>();
                String Name = info.getJSONObject(i).getString("name").trim();
                String Icon_url = info.getJSONObject(i).getString("icon");
                if (Icon_url == null  ||  Icon_url=="" || Icon_url.length()<1){ //若icon找不到
                    Icon_url= "";
                }
                JSONObject aa = info.getJSONObject(i).getJSONObject("geometry");
                JSONObject bb=aa.getJSONObject("location");
                String lat_l = bb.getString("lat");
                String lng_l = bb.getString("lng");
                String lat_lng=lat_l+","+lng_l;


                item.put("Name", Name);
                item.put("Icon_url", Icon_url);
                item.put("lat_lng", lat_lng);
                item.put("lat_l", lat_l);
                item.put("lng_l", lng_l);
                mList.add(item);
//-------------------
            }
//            t_count.setText(getString(R.string.ncount) + total + "/" + t_total);
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
//----------SwipeLayout 結束 --------
    }
    private void showloc() {
        if (mMap!=null)mMap.clear();
        //將所有景點位置顯示
        for (int i = 0; i < mList.size(); i++) { //0是我的位置
            String vtitle = mList.get(i).get("Name");
            String lat_l=mList.get(i).get("lat_l");
            String lng_l=mList.get(i).get("lng_l");
            //設定所選位置圖片
            int resID = 0; //R 的機碼
            if (mList.get(i).get("Icon_url")!="" || mList.get(i).get("Icon_url")!=null){
                URL url = null;
                try {
                    url = new URL(mList.get(i).get("Icon_url"));
                    Bitmap bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());
                    image_des = BitmapDescriptorFactory.fromBitmap(bmp);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }else {
                image_des = BitmapDescriptorFactory
                        .defaultMarker(BitmapDescriptorFactory.HUE_ORANGE); // 使用橘色系統水滴
            }

            VGPS = new LatLng(Double.parseDouble(lat_l), Double.parseDouble(lng_l));// 更新成欲顯示的地圖座標
            mMap.addMarker(new MarkerOptions()
                            .position(VGPS)
                            .alpha(0.9f)
                            .title(vtitle)
                            .snippet("座標:" + lat_l + "," + lat_l)
                            .infoWindowAnchor(0.1f, 0.3f)
                            .icon(image_des) // 顯示圖標文字
                    // .draggable(true) //設定maker 可移動
            );
        }
        loading.setVisibility(View.GONE);
    }

    public void searchLocation(View view) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            if (!m_location.equals("24.137622102040563, 120.68662252293544")){
                if(getString(R.string.google_maps_key).equals("Your Key")){
                    Toast.makeText(getApplicationContext(),getString(R.string.noKey),Toast.LENGTH_SHORT).show();
                } else {
                    btn_serchOff.setVisibility(View.VISIBLE);
                    btn_serchOn.setVisibility(View.GONE);
                    loading.setVisibility(View.VISIBLE);
                    u_importopendata();
                    showloc();
                }
                if (mList == null || mList.size() == 0 ) Toast.makeText(getApplicationContext(),getString(R.string.noLoc),Toast.LENGTH_SHORT).show();
                searchOn=true;
            }else Toast.makeText(getApplicationContext(), getString(R.string.noGPS), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getApplicationContext(), getString(R.string.GPSnoStart), Toast.LENGTH_LONG).show();
            checkRequiredPermission(this);
        }
    }

    public void c_searchLocation(View view) {
        btn_serchOff.setVisibility(View.GONE);
        btn_serchOn.setVisibility(View.VISIBLE);
        mMap.clear();
        searchOn=false;
    }

    private class TransTask extends AsyncTask<String, Void, String> {
        String ans;

        @Override
        protected String doInBackground(String... params) {
            StringBuilder sb = new StringBuilder();
            try {
                URL url = new URL(params[0]);
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(url.openStream()));
                String line = in.readLine();
                while (line != null) {
                    String aa=line;
                    int bb=0;
                    sb.append(line);
                    line = in.readLine();
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            ans = sb.toString();
            //------------
            return ans;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            parseJson(s);
        }

        private void parseJson(String s) {
        }
    }

    private void show_settings() {
        //------設定自訂義Dialog------
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(Main.this);
        View dailog = getLayoutInflater().inflate(R.layout.settings, null); //取得自訂義layout
        alertDialog.setView(dailog);
        Button btnOK = dailog.findViewById(R.id.btn_ok); //確認按鈕
        Button btnC = dailog.findViewById(R.id.btn_cancel); //取消按鈕
        loc_choose = (Spinner)dailog.findViewById(R.id.spn_loc); //選擇設施
        //設定Adapter
        ArrayAdapter loc=new ArrayAdapter(this, android.R.layout.simple_spinner_item);
        if (loc_Text.length == loc_Value.length){
            for(int i=0; i<loc_Text.length;i++) loc.add(loc_Text[i]);
        }else {
            loc.add(getString(R.string.default_type));
        }
        loc_choose.setAdapter(loc);
        range_t = (TextView)dailog.findViewById(R.id.bar_text); //搜尋範圍
        SeekBar bar_range = dailog.findViewById(R.id.bar_range);
        bar_range.setOnSeekBarChangeListener(barCL);
        final AlertDialog dialog_create = alertDialog.create();
        dialog_create.setCancelable(false);
        dialog_create.show();
        dialog_create.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); //dlg背景全透明
        //------------------------------
        btnOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (loc_Text.length == loc_Value.length){
                loc_type=loc_Value[loc_choose.getSelectedItemPosition()];
                }else loc_type="food";
                loc_range=bar_range.getProgress();
                dialog_create.cancel();
                if (searchOn){
                    u_importopendata();
                    showloc();
                    if (mList == null || mList.size() == 0 ) Toast.makeText(getApplicationContext(),getString(R.string.noLoc),Toast.LENGTH_SHORT).show();
                }
            }
        });
        btnC.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog_create.cancel();
            }
        });
    }
    private SeekBar.OnSeekBarChangeListener barCL=new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            range_t.setText(progress+"m");
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };

    //------生命週期------

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        init_app();
    }

    @Override
    protected void onStop() {
        super.onStop();
//        locationManager.removeUpdates(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    //Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.m_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()){
            case R.id.settings:
                show_settings();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

}