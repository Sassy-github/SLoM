package com.shashipage.slm_mylocation

import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.OnMapReadyCallback
import android.location.LocationListener
import com.google.android.gms.maps.GoogleMap
import android.location.LocationManager
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.BitmapDescriptor
import android.os.Bundle
import com.shashipage.slm_mylocation.R
import com.google.android.gms.maps.SupportMapFragment
import android.content.pm.PackageManager
import android.content.DialogInterface
import android.content.Intent
import android.Manifest.permission
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import android.location.LocationProvider
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.MarkerOptions
import android.os.AsyncTask
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.Exception
import java.lang.StringBuilder
import java.net.MalformedURLException
import java.net.URL
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.ExecutionException

class Main : AppCompatActivity(), OnMapReadyCallback, LocationListener {
    private val permissionsList: MutableList<String> = ArrayList()

    //------------------------------------------------------------------
    private var mMap: GoogleMap? = null
    private var locationManager: LocationManager? = null
    private var VGPS = LatLng(24.137622102040563, 120.68662252293544)
    private val mapzoom = 16f
    private var provider //提供資料
            : String? = null

    //------多久更新一次位置資訊
    private val minTime: Long = 5000 // ms
    private val minDist = 30.0f // meter
    private var mList: ArrayList<Map<String, String?>>? = null
    private var total = 0
    private var t_total = 0
    private var m_location = "24.137622102040563, 120.68662252293544"
    private var image_des: BitmapDescriptor? = null
    private var loc_choose: Spinner? = null
    private var range_t: TextView? = null
    private lateinit var loc_Text: Array<String>
    private lateinit var loc_Value: Array<String>
    private var loc_type = "food"
    private var loc_range = 500
    private var searchOn = false
    private var btn_serchOn: Button? = null
    private var btn_serchOff: Button? = null
    private var loading: LinearLayout? = null

    //------
    override fun onCreate(savedInstanceState: Bundle?) {
        checkRequiredPermission(this)
        enableStrictMode(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        //------------設定MapFragment-----------------------------------
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment!!.getMapAsync(this)
        //-------------------------------------------------------
//        u_checkgps();  //檢查GPS是否開啟
        setupViewComponent()
    }

    private fun setupViewComponent() {
        if(getString(R.string.google_maps_key).equals("Your Key")) Toast.makeText(applicationContext,getString(R.string.noKey),Toast.LENGTH_SHORT).show();
        loc_Text = resources.getStringArray(R.array.map_locText)
        loc_Value = resources.getStringArray(R.array.map_locValue)
        btn_serchOn = findViewById<View>(R.id.findNearLocation) as Button
        btn_serchOff = findViewById<View>(R.id.c_findNearLocation) as Button
        loading = findViewById<View>(R.id.loading) as LinearLayout
    }

    private fun init_app() {    // 檢查定位是否成功
        try {
            if (initLocationProvider()) {
                if (ContextCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    nowaddress()
                } else {
                }
            }
        } catch (e: Exception) {
            Toast.makeText(applicationContext, getString(R.string.noGPS), Toast.LENGTH_LONG).show()
            //對話方塊啟用GPS
            val builder = AlertDialog.Builder(this)
            builder.setTitle("GPS未開啟")
                .setMessage(
                    """
    GPS目前狀態尚未啟用.
    請先開啟定位!,再次執行APP!
    """.trimIndent()
                )
                .setPositiveButton("離開再次執行") { dialog, which -> //使用Intent物件啟動設定程式來更改GPS設定
                    val i = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivity(i)
                    finish()
                }.setNegativeButton("不啟用", null).create().show()
            return
        }
    }

    private fun nowaddress() {
        if (ActivityCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this,
                permission.ACCESS_COARSE_LOCATION
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            val location = locationManager!!.getLastKnownLocation(provider)
            /********************************************************  */
            updateWithNewLocation(location) //*****開啟GPS定位
            /*********************************************************  */
            return
        }
        val isGPSEnabled = locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager!!.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        var location: Location? = null
        if (!(isGPSEnabled || isNetworkEnabled)) Toast.makeText(
            applicationContext,
            "GPS 未開啟",
            Toast.LENGTH_SHORT
        ).show() else {
            if (isNetworkEnabled) {
                locationManager!!.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    minTime, minDist, this
                )
                location = locationManager!!.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                //                tmsg.setText("使用網路GPS");
            }
            if (isGPSEnabled) {
                locationManager!!.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    minTime, minDist, this
                )
                location = locationManager!!.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                //                tmsg.setText("使用精確GPS");
            }
        }
    }

    private fun initLocationProvider(): Boolean {
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return if (locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            provider = LocationManager.GPS_PROVIDER
            true
        } else {
            false
        }
    }

    /*** 位置變更狀態監視 */ //    LocationListener locationListener = new LocationListener() {
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
    private fun checkRequiredPermission(mapsActivity: Main) {
        //        String permission_check= String[i][0] permission;
        for (i in permissionsArray.indices) {
            if (ContextCompat.checkSelfPermission(
                    mapsActivity,
                    permissionsArray[i][0]
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsList.add(permissionsArray[i][0])
            }
        }
        if (permissionsList.size != 0) {
            ActivityCompat.requestPermissions(
                mapsActivity,
                permissionsList.toTypedArray(),
                REQUEST_CODE_ASK_PERMISSIONS
            )
        }
    }

    private fun enableStrictMode(mapsActivity: Main) {
        //--------抓取遠端資料庫設定執行續(官方)----------------------只要連MySQL都要加入
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork()
                .penaltyLog().build()
        )
        StrictMode.setVmPolicy(
            VmPolicy.Builder().detectLeakedSqlLiteObjects().penaltyLog().penaltyDeath().build()
        )
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
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        //開啟GoogleMap 拖曳功能
        mMap!!.uiSettings.isScrollGesturesEnabled = true
        //右下角導覽及開啟GoogleMap 功能
        mMap!!.uiSettings.isMapToolbarEnabled = true
        //左上角顯示指北針，需旋轉畫面才會出現
        mMap!!.uiSettings.isCompassEnabled = true
        //右下角顯示縮放按鈕的放大縮小功能
        mMap!!.uiSettings.isZoomControlsEnabled = true
        //map.addMarker(new MarkerOptions().position(VGPS).title("中區職訓局"));
        mMap!!.moveCamera(CameraUpdateFactory.newLatLngZoom(VGPS, mapzoom))
        //----------取得定位許可-----------------------
        if (ContextCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            //----顯示我的位置ICO-------
            mMap!!.isMyLocationEnabled = true
        } else {
//            Toast.makeText(getApplicationContext(), "GPS定位權限未允許", Toast.LENGTH_LONG).show();
        }
    }

    override fun onLocationChanged(location: Location) {
        updateWithNewLocation(location)
    }

    private fun updateWithNewLocation(location: Location?) {
        var where = ""
        if (location != null) {
            val lat = location.latitude // 緯度
            val lng = location.longitude // 經度
            //            float speed = location.getSpeed();// 速度
//            long time = location.getTime();// 時間
//            double altitude = location.getAltitude();//海拔
            VGPS = LatLng(lat, lng)
            m_location = "$lat,$lng"
            cameraFocusOnMe(lat, lng)
        } else {
            where = "*位置訊號消失*"
        }
    }

    private fun cameraFocusOnMe(lat: Double, lng: Double) {
        val camPosition = CameraPosition.Builder()
            .target(LatLng(lat, lng))
            .zoom(mMap!!.cameraPosition.zoom)
            .build()
        /* 移動地圖鏡頭 */mMap!!.animateCamera(CameraUpdateFactory.newCameraPosition(camPosition))
    }

    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
        when (status) {
            LocationProvider.OUT_OF_SERVICE -> {
            }
            LocationProvider.TEMPORARILY_UNAVAILABLE -> {
            }
            LocationProvider.AVAILABLE -> {
            }
        }
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {
        updateWithNewLocation(null)
    }

    //------載入opendata(Place Api)------
    private fun u_importopendata() { //下載Opendata
        val google_place_key = getString(R.string.googke_place_key)
        try {
            val Task_opendata: String = TransTask().execute(
                "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=$m_location&radius=$loc_range&types=$loc_type&sensor=true&key=$google_place_key"
            ).get() //     //旅館民宿 - 觀光資訊資料庫
            //-------解析 json   帶有多層結構-------------
            mList = ArrayList()
            val json_obj1 = JSONObject(Task_opendata)
            val info = json_obj1.getJSONArray("results")
            total = 0
            t_total = info.length() //總筆數
            //-----開始逐筆轉換-----
            total = info.length()
            //            t_count.setText(getString(R.string.ncount) + total);
            for (i in 0 until info.length()) {
                val item: MutableMap<String, String?> = HashMap()
                val Name = info.getJSONObject(i).getString("name").trim { it <= ' ' }
                var Icon_url = info.getJSONObject(i).getString("icon")
                if (Icon_url == null || Icon_url === "" || Icon_url.length < 1) { //若icon找不到
                    Icon_url = ""
                }
                val aa = info.getJSONObject(i).getJSONObject("geometry")
                val bb = aa.getJSONObject("location")
                val lat_l = bb.getString("lat")
                val lng_l = bb.getString("lng")
                val lat_lng = "$lat_l,$lng_l"
                item["Name"] = Name
                item["Icon_url"] = Icon_url
                item["lat_lng"] = lat_lng
                item["lat_l"] = lat_l
                item["lng_l"] = lng_l
                mList!!.add(item)
                //-------------------
            }
            //            t_count.setText(getString(R.string.ncount) + total + "/" + t_total);
        } catch (e: JSONException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } catch (e: ExecutionException) {
            e.printStackTrace()
        }
        //----------SwipeLayout 結束 --------
    }

    private fun showloc() {
        if (mMap != null) mMap!!.clear()
        //將所有景點位置顯示
        for (i in mList!!.indices) { //0是我的位置
            val vtitle = mList!![i]["Name"]
            val lat_l = mList!![i]["lat_l"]
            val lng_l = mList!![i]["lng_l"]
            //設定所選位置圖片
            val resID = 0 //R 的機碼
            if (mList!![i]["Icon_url"] !== "" || mList!![i]["Icon_url"] != null) {
                var url: URL? = null
                try {
                    url = URL(mList!![i]["Icon_url"])
                    val bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream())
                    image_des = BitmapDescriptorFactory.fromBitmap(bmp)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                image_des = BitmapDescriptorFactory
                    .defaultMarker(BitmapDescriptorFactory.HUE_ORANGE) // 使用橘色系統水滴
            }
            VGPS = LatLng(lat_l!!.toDouble(), lng_l!!.toDouble()) // 更新成欲顯示的地圖座標
            mMap!!.addMarker(
                MarkerOptions()
                    .position(VGPS)
                    .alpha(0.9f)
                    .title(vtitle)
                    .snippet("座標:$lat_l,$lat_l")
                    .infoWindowAnchor(0.1f, 0.3f)
                    .icon(image_des) // 顯示圖標文字
                // .draggable(true) //設定maker 可移動
            )
        }
        loading!!.visibility = View.GONE
    }

    fun searchLocation(view: View?) {
        if (ContextCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            if (m_location != "24.137622102040563, 120.68662252293544") {
                if(getString(R.string.google_maps_key).equals("Your Key")){
                    Toast.makeText(applicationContext,getString(R.string.noKey),Toast.LENGTH_SHORT).show();
                } else{
                    btn_serchOff!!.visibility = View.VISIBLE
                    btn_serchOn!!.visibility = View.GONE
                    loading!!.visibility = View.VISIBLE
                    u_importopendata()
                    showloc()
                    if (mList == null || mList!!.size == 0) Toast.makeText(
                        applicationContext,
                        getString(R.string.noLoc),
                        Toast.LENGTH_SHORT
                    ).show()
                    searchOn = true
                }
            } else Toast.makeText(applicationContext, getString(R.string.noGPS), Toast.LENGTH_LONG)
                .show()
        } else {
            Toast.makeText(applicationContext, getString(R.string.GPSnoStart), Toast.LENGTH_LONG)
                .show()
            checkRequiredPermission(this)
        }
    }

    fun c_searchLocation(view: View?) {
        btn_serchOff!!.visibility = View.GONE
        btn_serchOn!!.visibility = View.VISIBLE
        mMap!!.clear()
        searchOn = false
    }

    private inner class TransTask : AsyncTask<String?, Void?, String>() {
        var ans: String? = null
        protected override fun doInBackground(vararg params: String?): String? {
            val sb = StringBuilder()
            try {
                val url = URL(params[0])
                val `in` = BufferedReader(
                    InputStreamReader(url.openStream())
                )
                var line = `in`.readLine()
                while (line != null) {
                    val aa = line
                    val bb = 0
                    sb.append(line)
                    line = `in`.readLine()
                }
            } catch (e: MalformedURLException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            ans = sb.toString()
            //------------
            return ans!!
        }

        override fun onPostExecute(s: String) {
            super.onPostExecute(s)
            parseJson(s)
        }

        private fun parseJson(s: String) {}
    }

    private fun show_settings() {
        //------設定自訂義Dialog------
        val alertDialog = AlertDialog.Builder(this@Main)
        val dailog = layoutInflater.inflate(R.layout.settings, null) //取得自訂義layout
        alertDialog.setView(dailog)
        val btnOK = dailog.findViewById<Button>(R.id.btn_ok) //確認按鈕
        val btnC = dailog.findViewById<Button>(R.id.btn_cancel) //取消按鈕
        loc_choose = dailog.findViewById<View>(R.id.spn_loc) as Spinner //選擇設施
        //設定Adapter
        val loc = ArrayAdapter<Any?>(this, android.R.layout.simple_spinner_item)
        if (loc_Text.size == loc_Value.size) {
            for (i in loc_Text.indices) loc.add(loc_Text[i])
        } else {
            loc.add(getString(R.string.default_type))
        }
        loc_choose!!.adapter = loc
        range_t = dailog.findViewById<View>(R.id.bar_text) as TextView //搜尋範圍
        val bar_range = dailog.findViewById<SeekBar>(R.id.bar_range)
        bar_range.setOnSeekBarChangeListener(barCL)
        val dialog_create = alertDialog.create()
        dialog_create.setCancelable(false)
        dialog_create.show()
        dialog_create.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) //dlg背景全透明
        //------------------------------
        btnOK.setOnClickListener {
            loc_type = if (loc_Text.size == loc_Value.size) {
                loc_Value[loc_choose!!.selectedItemPosition]
            } else "food"
            loc_range = bar_range.progress
            dialog_create.cancel()
            if (searchOn) {
                u_importopendata()
                showloc()
                if (mList == null || mList!!.size == 0) Toast.makeText(
                    applicationContext,
                    getString(R.string.noLoc),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        btnC.setOnClickListener { dialog_create.cancel() }
    }

    private val barCL: OnSeekBarChangeListener = object : OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            range_t!!.text = progress.toString() + "m"
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {}
        override fun onStopTrackingTouch(seekBar: SeekBar) {}
    }

    //------生命週期------
    override fun onStart() {
        super.onStart()
    }

    override fun onResume() {
        super.onResume()
        init_app()
    }

    override fun onStop() {
        super.onStop()
        //        locationManager.removeUpdates(this);
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    //Menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.m_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings -> show_settings()
            else -> {
            }
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        //------permission所需要申請的權限數組--------------------------
        private val permissionsArray = arrayOf(
            arrayOf(permission.ACCESS_FINE_LOCATION, "僅GPS定位"), arrayOf(
                permission.ACCESS_COARSE_LOCATION, "一般定位"
            ), arrayOf(permission.INTERNET, "網路"), arrayOf(
                permission.ACCESS_NETWORK_STATE, "上網"
            )
        )

        //申請權限後的返回碼
        private const val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 101
        private const val REQUEST_CODE_ASK_PERMISSIONS = 1
    }
}