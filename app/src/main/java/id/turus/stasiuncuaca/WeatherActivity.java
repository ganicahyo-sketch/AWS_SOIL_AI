package id.turus.stasiuncuaca;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cuaca makro berbasis Open-Meteo. Tidak memerlukan API key.
 * Lokasi diambil dari GPS/network HP, lalu endpoint forecast mengembalikan
 * kondisi saat ini, 7-hari forecast, elevasi, UV, sunshine, hujan, dan ET0.
 */
public class WeatherActivity extends Activity {
    private static final int LOCATION_REQ = 701;
    private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private TextView status, location, current, forecast, updated;
    private LocationManager locationManager;
    private Location bestLocation;
    private LocationListener locationListener;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_weather);
        status = findViewById(R.id.weatherStatus);
        location = findViewById(R.id.weatherLocation);
        current = findViewById(R.id.weatherCurrent);
        forecast = findViewById(R.id.weatherForecast);
        updated = findViewById(R.id.weatherUpdated);
        findViewById(R.id.backWeather).setOnClickListener(v -> finish());
        findViewById(R.id.refreshWeather).setOnClickListener(v -> locateAndLoad());
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locateAndLoad();
    }

    @Override protected void onDestroy() {
        stopLocation();
        net.shutdownNow();
        super.onDestroy();
    }

    private void locateAndLoad() {
        stopLocation();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQ);
            return;
        }
        bestLocation = getLastKnown();
        if (bestLocation != null) loadWeather(bestLocation);
        try {
            LocationListener listener = new LocationListener() {
                @Override public void onLocationChanged(Location l) {
                    if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                        bestLocation = l;
                        stopLocation();
                        loadWeather(l);
                    }
                }
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            };
            locationListener = listener;
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 10f, listener);
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 10f, listener);
            if (bestLocation == null) {
                status.setText("MENCARI LOKASI HP…");
                Toast.makeText(this, "Aktifkan lokasi/GPS agar data Open-Meteo dapat dibaca.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            status.setText("LOKASI TIDAK TERSEDIA");
            if (bestLocation == null) location.setText("Periksa izin lokasi dan GPS HP.");
        }
    }

    private void stopLocation() {
        try { if (locationListener != null) locationManager.removeUpdates(locationListener); } catch (Exception ignored) {}
        locationListener = null;
    }

    private Location getLastKnown() {
        Location out = null;
        try {
            Location a = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location b = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (a != null) out = a;
            if (b != null && (out == null || b.getAccuracy() < out.getAccuracy())) out = b;
        } catch (SecurityException ignored) {}
        return out;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == LOCATION_REQ) locateAndLoad();
    }

    private void loadWeather(Location l) {
        final double lat = l.getLatitude(), lon = l.getLongitude();
        runOnUiThread(() -> {
            status.setText("MENGAMBIL DATA OPEN-METEO…");
            location.setText(String.format(Locale.US, "GPS HP • %.5f, %.5f", lat, lon));
        });
        net.execute(() -> {
            HttpURLConnection c = null;
            try {
                String params = "latitude=" + enc(fmt5(lat))
                        + "&longitude=" + enc(fmt5(lon))
                        + "&timezone=Asia%2FJakarta"
                        + "&forecast_days=7"
                        + "&current=temperature_2m,relative_humidity_2m,dew_point_2m,apparent_temperature,precipitation,rain,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m,visibility"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min,uv_index_max,sunrise,sunset,daylight_duration,sunshine_duration,precipitation_sum,precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max,shortwave_radiation_sum,et0_fao_evapotranspiration_sum";
                URL u = new URL("https://api.open-meteo.com/v1/forecast?" + params);
                c = (HttpURLConnection) u.openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(12000);
                c.setReadTimeout(20000);
                c.setUseCaches(true);
                int code = c.getResponseCode();
                if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
                JSONObject root = new JSONObject(readAll(c.getInputStream()));
                render(root, lat, lon);
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    status.setText("OFFLINE / GAGAL OPEN-METEO");
                    current.setText("Tidak dapat mengambil data cuaca.\n" + safe(ex));
                    updated.setText("Periksa internet lalu tekan PERBARUI.");
                });
            } finally { if (c != null) c.disconnect(); }
        });
    }

    private void render(JSONObject root, double lat, double lon) throws Exception {
        JSONObject cur = root.optJSONObject("current");
        JSONObject cu = root.optJSONObject("current_units");
        JSONObject d = root.optJSONObject("daily");
        if (cur == null) throw new Exception("Respons current tidak ada");
        double elev = root.optDouble("elevation", Double.NaN);
        String condition = wmo(cur.optInt("weather_code", -1));
        StringBuilder s = new StringBuilder();
        s.append("KONDISI SAAT INI\n");
        s.append("Cuaca: ").append(condition).append("\n");
        s.append("Suhu: ").append(fmt(cur.optDouble("temperature_2m", Double.NaN), 1)).append(" °C\n");
        s.append("Terasa: ").append(fmt(cur.optDouble("apparent_temperature", Double.NaN), 1)).append(" °C\n");
        s.append("Kelembapan: ").append(fmt(cur.optDouble("relative_humidity_2m", Double.NaN), 0)).append(" %\n");
        s.append("Titik embun: ").append(fmt(cur.optDouble("dew_point_2m", Double.NaN), 1)).append(" °C\n");
        s.append("Tekanan MSL: ").append(fmt(cur.optDouble("pressure_msl", Double.NaN), 0)).append(" hPa\n");
        s.append("Angin: ").append(fmt(cur.optDouble("wind_speed_10m", Double.NaN), 1)).append(" km/jam • arah ").append(fmt(cur.optDouble("wind_direction_10m", Double.NaN), 0)).append("°\n");
        s.append("Gust: ").append(fmt(cur.optDouble("wind_gusts_10m", Double.NaN), 1)).append(" km/jam\n");
        s.append("Awan: ").append(fmt(cur.optDouble("cloud_cover", Double.NaN), 0)).append(" %\n");
        s.append("Presipitasi: ").append(fmt(cur.optDouble("precipitation", Double.NaN), 1)).append(" mm\n");
        s.append("Jarak pandang: ").append(fmt(cur.optDouble("visibility", Double.NaN) / 1000.0, 1)).append(" km\n");
        if (Double.isFinite(elev)) s.append("Elevasi Open-Meteo: ").append(fmt(elev, 0)).append(" mdpl\n");
        String todayEt0 = d == null ? "--" : arrNum(d, "et0_fao_evapotranspiration_sum", 0);
        String uv = d == null ? "--" : arrNum(d, "uv_index_max", 0);
        s.append("ET0 hari ini: ").append(todayEt0).append(" mm\n");
        s.append("UV maksimum hari ini: ").append(uv).append("\n");
        runOnUiThread(() -> {
            status.setText("ONLINE • OPEN-METEO");
            location.setText(String.format(Locale.US, "GPS HP • %.5f, %.5f • Elevasi %s mdpl", lat, lon, fmt(elev,0)));
            current.setText(s.toString());
            forecast.setText(formatDaily(d));
            String fullReport = s.toString() + "\n\n" + formatDaily(d);
            getSharedPreferences("thingspeak_config", MODE_PRIVATE).edit()
                    .putString("weather_last_report", fullReport)
                    .putString("weather_lat", String.format(Locale.US, "%.6f", lat))
                    .putString("weather_lon", String.format(Locale.US, "%.6f", lon))
                    .putString("weather_elevation", fmt(elev, 0))
                    .putString("weather_last_time", ZonedDateTime.now(WIB).format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss 'WIB'", Locale.US)))
                    .apply();
            updated.setText("Diperbarui: " + ZonedDateTime.now(WIB).format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss 'WIB'", Locale.US)) + " • Open-Meteo tanpa API key");
        });
    }

    private String formatDaily(JSONObject d) {
        if (d == null) return "FORECAST 7 HARI\n--";
        JSONArray time = d.optJSONArray("time");
        StringBuilder s = new StringBuilder("FORECAST 7 HARI\n");
        if (time == null) return s.append("--").toString();
        for (int i=0;i<time.length();i++) {
            String day = time.optString(i, "--");
            s.append(day).append(" • ")
                    .append(wmo(at(d,"weather_code",i))).append(" • ")
                    .append(fmt(atD(d,"temperature_2m_min",i),0)).append("–").append(fmt(atD(d,"temperature_2m_max",i),0)).append(" °C")
                    .append(" • hujan ").append(fmt(atD(d,"precipitation_sum",i),1)).append(" mm")
                    .append(" • peluang ").append(fmt(atD(d,"precipitation_probability_max",i),0)).append("%")
                    .append(" • ET0 ").append(fmt(atD(d,"et0_fao_evapotranspiration_sum",i),1)).append(" mm")
                    .append(" • UV ").append(fmt(atD(d,"uv_index_max",i),1)).append("\n");
        }
        return s.toString().trim();
    }

    private int at(JSONObject o,String k,int i){JSONArray a=o.optJSONArray(k);return a==null?-1:a.optInt(i,-1);}
    private double atD(JSONObject o,String k,int i){JSONArray a=o.optJSONArray(k);return a==null?Double.NaN:a.optDouble(i,Double.NaN);}
    private String arrNum(JSONObject o,String k,int i){return fmt(atD(o,k,i),1)+"";}
    private String wmo(int c) {
        switch(c){
            case 0:return "Cerah"; case 1:return "Cerah berawan"; case 2:return "Sebagian berawan"; case 3:return "Mendung";
            case 45:case 48:return "Kabut"; case 51:case 53:case 55:return "Gerimis"; case 56:case 57:return "Gerimis beku";
            case 61:case 63:case 65:return "Hujan"; case 66:case 67:return "Hujan beku"; case 71:case 73:case 75:case 77:return "Salju";
            case 80:case 81:case 82:return "Hujan deras sesaat"; case 85:case 86:return "Salju sesaat"; case 95:return "Badai petir";
            case 96:case 99:return "Badai petir + hujan es"; default:return "Kode cuaca " + c;
        }
    }
    private String enc(String s)throws Exception{return URLEncoder.encode(s, StandardCharsets.UTF_8.name());}
    private String fmt5(double v){return String.format(Locale.US,"%.5f",v);}
    private String fmt(double v,int dec){if(!Double.isFinite(v))return "--";NumberFormat n=NumberFormat.getNumberInstance(new Locale("id","ID"));n.setGroupingUsed(false);n.setMinimumFractionDigits(dec);n.setMaximumFractionDigits(dec);return n.format(v);}
    private String safe(Throwable e){return e==null||e.getMessage()==null?"Kesalahan tidak diketahui":e.getMessage();}
    private String readAll(InputStream in)throws Exception{if(in==null)return "";StringBuilder s=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String l;while((l=r.readLine())!=null)s.append(l);}return s.toString();}
}
