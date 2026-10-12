package id.turus.stasiuncuaca;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.os.Looper;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "thingspeak_config";
    private static final String DEFAULT_CHANNEL = "2981880";
    private static final String DEFAULT_READ_KEY = "P4B56Z7HZM56Q7HJ";
    private static final String DEFAULT_TITLE = "STASIUN CUACA";
    private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");
    private static final long TS_REFRESH_MS = 30_000L;
    private static final long WEATHER_REFRESH_MS = 10 * 60_000L;
    private static final int LOCATION_REQ = 77;

    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final ExecutorService net = Executors.newFixedThreadPool(2);
    private android.content.SharedPreferences prefs;

    private TextView title, subtitle, statusChip, lastAccess, deviceDate, deviceClock, dataTime, channelView;
    private TextView weatherStatus, weatherLocation, weatherTemp, weatherFeels, weatherDew, weatherHumidity,
            weatherPressure, weatherCloud, weatherUv, weatherVisibility, weatherWind, weatherWindDir,
            weatherRain, weatherEt0, weatherCondition, weatherSunrise, weatherSunset, weatherSunHours, weatherAlerts,
            weatherForecast, weatherUpdated, weatherSw, weatherPar, weatherPpfd, weatherPpfdNote;
    private TextView soil, soilStatus, optRisk, overall, finalStatus;
    private GridLayout fieldGrid;
    private TextView[] fieldLabels = new TextView[8];
    private TextView[] fieldValues = new TextView[8];
    private TextView[] fieldUnits = new TextView[8];
    private String[] detectedNames = new String[8];
    private String[] latestValues = new String[8];
    private String latestCreatedAt = "";
    private long lastWeatherEpoch = 0L;
    private boolean tsLoading = false;
    private boolean weatherLoading = false;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            updateClock();
            handler.postDelayed(this, 1000L);
        }
    };

    private final Runnable refreshTick = new Runnable() {
        @Override public void run() {
            loadThingSpeak(false);
            handler.postDelayed(this, TS_REFRESH_MS);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        bindViews();
        buildFieldCards();

        findViewById(R.id.settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.refresh).setOnClickListener(v -> { loadThingSpeak(true); loadOpenMeteo(true); });
        findViewById(R.id.weatherRefresh).setOnClickListener(v -> loadOpenMeteo(true));
        findViewById(R.id.btnGps).setOnClickListener(v -> requestLocation());
        findViewById(R.id.btnFieldNotes).setOnClickListener(v -> startActivity(new Intent(this, FieldNotesActivity.class)));
        findViewById(R.id.btnAgronomy).setOnClickListener(v -> startActivity(new Intent(this, AgronomyActivity.class)));
        findViewById(R.id.btnCsv).setOnClickListener(v -> startActivity(new Intent(this, CsvDownloadActivity.class)));
        findViewById(R.id.btnPdfReport).setOnClickListener(v -> startActivity(new Intent(this, PdfReportActivity.class)));

        updateHeader();
        updateClock();
        handler.post(clockTick);
        loadThingSpeak(true);
        loadOpenMeteo(false);
    }

    @Override protected void onResume() {
        super.onResume();
        updateHeader();
        updateClock();
        loadThingSpeak(false);
        loadOpenMeteo(false);
        handler.removeCallbacks(refreshTick);
        handler.postDelayed(refreshTick, TS_REFRESH_MS);
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshTick);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        net.shutdownNow();
        super.onDestroy();
    }

    private void bindViews() {
        title = findViewById(R.id.title);
        subtitle = findViewById(R.id.subtitle);
        statusChip = findViewById(R.id.statusChip);
        lastAccess = findViewById(R.id.lastAccess);
        deviceDate = findViewById(R.id.deviceDate);
        deviceClock = findViewById(R.id.deviceClock);
        dataTime = findViewById(R.id.dataTime);
        channelView = findViewById(R.id.channelView);
        fieldGrid = findViewById(R.id.grid);

        weatherStatus = findViewById(R.id.weatherStatus);
        weatherLocation = findViewById(R.id.weatherLocation);
        weatherTemp = findViewById(R.id.weatherTemp);
        weatherFeels = findViewById(R.id.weatherFeels);
        weatherDew = findViewById(R.id.weatherDew);
        weatherHumidity = findViewById(R.id.weatherHumidity);
        weatherPressure = findViewById(R.id.weatherPressure);
        weatherCloud = findViewById(R.id.weatherCloud);
        weatherUv = findViewById(R.id.weatherUv);
        weatherVisibility = findViewById(R.id.weatherVisibility);
        weatherWind = findViewById(R.id.weatherWind);
        weatherWindDir = findViewById(R.id.weatherWindDir);
        weatherRain = findViewById(R.id.weatherRain);
        weatherEt0 = findViewById(R.id.weatherEt0);
        weatherCondition = findViewById(R.id.weatherCondition);
        weatherSunrise = findViewById(R.id.weatherSunrise);
        weatherSunset = findViewById(R.id.weatherSunset);
        weatherSunHours = findViewById(R.id.weatherSunHours);
        weatherAlerts = findViewById(R.id.weatherAlerts);
        weatherForecast = findViewById(R.id.weatherForecast);
        weatherUpdated = findViewById(R.id.weatherUpdated);
        weatherSw = findViewById(R.id.weatherSw);
        weatherPar = findViewById(R.id.weatherPar);
        weatherPpfd = findViewById(R.id.weatherPpfd);
        weatherPpfdNote = findViewById(R.id.weatherPpfdNote);

        soil = findViewById(R.id.soil);
        soilStatus = findViewById(R.id.soilStatus);
        optRisk = findViewById(R.id.optRisk);
        overall = findViewById(R.id.overall);
        finalStatus = findViewById(R.id.finalStatus);
    }

    private void buildFieldCards() {
        fieldGrid.removeAllViews();
        for (int i = 0; i < 8; i++) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(14, 12, 14, 12);
            card.setBackgroundResource(R.drawable.bg_panel);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
            lp.columnSpec = GridLayout.spec(i % 2, 1, 1f);
            lp.rowSpec = GridLayout.spec(i / 2);
            lp.setMargins(4, 4, 4, 4);
            card.setLayoutParams(lp);

            TextView label = new TextView(this);
            label.setText("Field " + (i + 1));
            label.setTextColor(getColorCompat(R.color.cyan));
            label.setTextSize(13);
            label.setGravity(Gravity.START);

            TextView value = new TextView(this);
            value.setText("--");
            value.setTextColor(getColorCompat(R.color.text_main));
            value.setTextSize(20);
            value.setPadding(0, 4, 0, 0);

            TextView unit = new TextView(this);
            unit.setText("");
            unit.setTextColor(getColorCompat(R.color.text_muted));
            unit.setTextSize(12);
            unit.setPadding(0, 2, 0, 0);

            card.addView(label);
            card.addView(value);
            card.addView(unit);
            fieldGrid.addView(card);
            fieldLabels[i] = label;
            fieldValues[i] = value;
            fieldUnits[i] = unit;
        }
    }

    private int getColorCompat(int id) {
        return getResources().getColor(id);
    }

    private void updateHeader() {
        String t = prefs.getString("app_title", DEFAULT_TITLE).trim();
        title.setText(t.isEmpty() ? DEFAULT_TITLE : t);
        subtitle.setText("GANI CAHYO-UNS • v1.5.8 • Sawit + Open-Meteo + Agronomi");
        String ch = prefs.getString("channel", "").trim();
        if (ch.isEmpty()) ch = DEFAULT_CHANNEL;
        channelView.setText("CHANNEL: " + (ch.isEmpty() ? "--" : ch));
    }

    private void updateClock() {
        ZonedDateTime n = ZonedDateTime.now(WIB);
        deviceDate.setText(cap(n.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", new Locale("id", "ID")))));
        deviceClock.setText(n.format(DateTimeFormatter.ofPattern("HH:mm:ss 'WIB'", Locale.US)));
    }

    private String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void loadOpenMeteo(boolean manual) {
        double lat = prefs.getFloat("latitude", Float.NaN);
        double lon = prefs.getFloat("longitude", Float.NaN);
        if (!Double.isFinite(lat) || !Double.isFinite(lon)) {
            weatherStatus.setText("OPEN-METEO • KOORDINAT BELUM ADA");
            weatherLocation.setText("Tekan GUNAKAN GPS HP atau isi latitude/longitude pada Pengaturan.");
            return;
        }
        long now = System.currentTimeMillis();
        if (!manual && now - lastWeatherEpoch < WEATHER_REFRESH_MS) return;
        if (weatherLoading) return;
        weatherLoading = true;
        lastWeatherEpoch = now;
        final String provider = prefs.getString("weather_provider", "Open-Meteo");
        weatherStatus.setText("MEMUAT " + provider.toUpperCase(Locale.US) + "...");
        net.execute(() -> {
            try {
                Weather w = "WeatherAPI.com".equals(provider) ? fetchWeatherApi(lat, lon) : fetchOpenMeteo(lat, lon);
                runOnUiThread(() -> {
                    double currentLat = prefs.getFloat("latitude", Float.NaN), currentLon = prefs.getFloat("longitude", Float.NaN);
                    if (Double.compare(currentLat, lat) != 0 || Double.compare(currentLon, lon) != 0) {
                        weatherLoading = false;
                        loadOpenMeteo(true);
                        return;
                    }
                    renderWeather(w); weatherLoading = false;
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    weatherStatus.setText(provider.toUpperCase(Locale.US) + " GAGAL");
                    weatherLocation.setText(msg(e));
                    weatherLoading = false;
                });
            }
        });
    }

    private Weather fetchWeatherApi(double lat, double lon) throws Exception {
        String key = prefs.getString("weatherapi_key", "").trim();
        if (key.isEmpty()) throw new Exception("Isi WeatherAPI.com key di Pengaturan, atau pilih Open-Meteo yang tidak memerlukan API key.");
        String u = "https://api.weatherapi.com/v1/forecast.json?key=" + URLEncoder.encode(key, "UTF-8")
                + "&q=" + URLEncoder.encode(String.format(Locale.US, "%.6f,%.6f", lat, lon), "UTF-8")
                + "&days=7&aqi=no&alerts=yes";
        JSONObject root = getJson(u);
        JSONObject cur = root.optJSONObject("current");
        JSONObject loc = root.optJSONObject("location");
        JSONObject forecast = root.optJSONObject("forecast");
        JSONArray days = forecast == null ? null : forecast.optJSONArray("forecastday");
        JSONObject day = days == null || days.length() == 0 ? new JSONObject() : days.optJSONObject(0);
        JSONObject dayInfo = day == null ? new JSONObject() : day.optJSONObject("day");
        JSONObject astro = day == null ? new JSONObject() : day.optJSONObject("astro");
        if (cur == null) throw new Exception("Respons WeatherAPI tidak memuat kondisi saat ini.");
        Weather w = new Weather();
        w.temp = cur.optDouble("temp_c", Double.NaN);
        w.rh = cur.optDouble("humidity", Double.NaN);
        w.apparent = cur.optDouble("feelslike_c", Double.NaN);
        w.dewpoint = cur.optDouble("dewpoint_c", Double.NaN);
        w.rainNow = cur.optDouble("precip_mm", Double.NaN);
        w.pressure = cur.optDouble("pressure_mb", Double.NaN);
        w.cloud = cur.optDouble("cloud", Double.NaN);
        w.windSpeed = cur.optDouble("wind_kph", Double.NaN) / 3.6;
        w.windDir = cur.optDouble("wind_degree", Double.NaN);
        w.gust = cur.optDouble("gust_kph", Double.NaN) / 3.6;
        w.visibility = cur.optDouble("vis_km", Double.NaN) * 1000.0;
        w.uv = cur.optDouble("uv", Double.NaN);
        w.sw = Double.NaN; w.par = Double.NaN; w.ppfd = Double.NaN;
        w.vpd = Double.NaN; w.soilTemp = Double.NaN; w.soilMoisture = Double.NaN;
        w.et0 = Double.NaN;
        w.rainDay = dayInfo == null ? Double.NaN : dayInfo.optDouble("totalprecip_mm", Double.NaN);
        w.sunHours = Double.NaN;
        w.sunrise = astro == null ? "" : astro.optString("sunrise", "");
        w.sunset = astro == null ? "" : astro.optString("sunset", "");
        JSONObject condition = cur.optJSONObject("condition");
        String conditionText = condition == null ? "Cuaca tidak diketahui" : condition.optString("text", "Cuaca tidak diketahui");
        w.code = -1;
        StringBuilder summary = new StringBuilder();
        if (days != null) for (int i=0; i<days.length() && i<7; i++) {
            JSONObject x = days.optJSONObject(i); if (x == null) continue;
            JSONObject di = x.optJSONObject("day"); if (di == null) continue;
            if (summary.length()>0) summary.append(" | ");
            summary.append(x.optString("date", "--")).append(" ")
                    .append(fmt(di.optDouble("mintemp_c", Double.NaN),0)).append("–")
                    .append(fmt(di.optDouble("maxtemp_c", Double.NaN),0)).append("°C hujan ")
                    .append(fmt(di.optDouble("totalprecip_mm", Double.NaN),1)).append(" mm");
        }
        w.forecast = summary.toString();
        w.provider = "WeatherAPI.com";
        w.conditionText = conditionText;
        w.elevation = Double.NaN;
        return w;
    }

    private Weather fetchOpenMeteo(double lat, double lon) throws Exception {
        String current = "temperature_2m,relative_humidity_2m,apparent_temperature,dew_point_2m,precipitation,weather_code,surface_pressure,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m,visibility,uv_index,shortwave_radiation,vapour_pressure_deficit,soil_temperature_0_to_10cm,soil_moisture_0_to_10cm";
        String daily = "temperature_2m_max,temperature_2m_min,precipitation_sum,sunshine_duration,et0_fao_evapotranspiration,wind_direction_10m_dominant,uv_index_max,sunrise,sunset";
        String u = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=" + URLEncoder.encode(current, "UTF-8")
                + "&daily=" + URLEncoder.encode(daily, "UTF-8")
                + "&timezone=Asia%2FJakarta&forecast_days=7";
        JSONObject root = getJson(u);
        JSONObject cur = root.getJSONObject("current");
        JSONObject d = root.getJSONObject("daily");
        Weather w = new Weather();
        w.temp = cur.optDouble("temperature_2m", Double.NaN);
        w.rh = cur.optDouble("relative_humidity_2m", Double.NaN);
        w.apparent = cur.optDouble("apparent_temperature", Double.NaN);
        w.dewpoint = cur.optDouble("dew_point_2m", Double.NaN);
        w.rainNow = cur.optDouble("precipitation", Double.NaN);
        w.code = cur.optInt("weather_code", -1);
        w.pressure = cur.optDouble("surface_pressure", Double.NaN);
        w.cloud = cur.optDouble("cloud_cover", Double.NaN);
        w.windSpeed = cur.optDouble("wind_speed_10m", Double.NaN);
        w.windDir = cur.optDouble("wind_direction_10m", Double.NaN);
        w.gust = cur.optDouble("wind_gusts_10m", Double.NaN);
        w.visibility = cur.optDouble("visibility", Double.NaN);
        w.uv = cur.optDouble("uv_index", Double.NaN);
        w.sw = cur.optDouble("shortwave_radiation", Double.NaN);
        w.vpd = cur.optDouble("vapour_pressure_deficit", Double.NaN);
        w.soilTemp = cur.optDouble("soil_temperature_0_to_10cm", Double.NaN);
        w.soilMoisture = cur.optDouble("soil_moisture_0_to_10cm", Double.NaN);
        w.elevation = root.optDouble("elevation", Double.NaN);
        w.par = LightConversion.shortwaveToParWm2(w.sw);
        w.ppfd = LightConversion.parToPpfd(w.par);
        w.et0 = first(d, "et0_fao_evapotranspiration");
        w.sunHours = first(d, "sunshine_duration") / 3600.0;
        w.rainDay = first(d, "precipitation_sum");
        w.sunrise = firstString(d, "sunrise");
        w.sunset = firstString(d, "sunset");
        w.forecast = forecastSummary(d);
        w.provider = "Open-Meteo";
        w.conditionText = wmo(w.code);
        return w;
    }

    private void renderWeather(Weather w) {
        String provider = w.provider == null ? "Open-Meteo" : w.provider;
        weatherStatus.setText("ONLINE • " + provider.toUpperCase(Locale.US));
        weatherLocation.setText("GPS "+fmt(prefs.getFloat("latitude",0),5)+", "+fmt(prefs.getFloat("longitude",0),5)
                + (Double.isFinite(w.elevation) ? " • elevasi DEM " + fmt(w.elevation,0) + " mdpl" : ""));
        weatherTemp.setText("Suhu " + fmt(w.temp,1) + " °C");
        weatherFeels.setText("Terasa " + fmt(w.apparent,1) + " °C");
        weatherHumidity.setText("RH " + fmt(w.rh,0) + " %");
        weatherDew.setText("Titik embun " + fmt(w.dewpoint,1) + " °C");
        weatherPressure.setText("Tekanan " + fmt(w.pressure,0) + " hPa");
        weatherCloud.setText("Awan " + fmt(w.cloud,0) + " %");
        weatherUv.setText("UV " + fmt(w.uv,1));
        weatherVisibility.setText("Visibilitas " + fmt(w.visibility / 1000.0,1) + " km");
        weatherWind.setText("Angin " + fmt(w.windSpeed,1) + " m/s");
        weatherWindDir.setText("Arah " + compass(w.windDir));
        weatherRain.setText("Hujan " + fmt(w.rainDay,1) + " mm/hari");
        weatherEt0.setText(Double.isFinite(w.et0) ? "ET₀ " + fmt(w.et0,2) + " mm/hari" : "ET₀ tidak tersedia dari provider ini");
        weatherCondition.setText("Kondisi: " + (w.conditionText == null ? wmo(w.code) : w.conditionText));
        weatherSunrise.setText("Terbit " + shortTime(w.sunrise));
        weatherSunset.setText("Terbenam " + shortTime(w.sunset));
        weatherSunHours.setText(Double.isFinite(w.sunHours) ? "Lama penyinaran matahari " + fmt(w.sunHours, 2) + " jam/hari (Open-Meteo)" : "Lama penyinaran tidak tersedia dari provider ini");
        weatherAlerts.setText("VPD " + fmt(w.vpd,2) + " kPa • " + AgronomyEngine.classifyVpd(w.vpd) + " • Gust " + fmt(w.gust,1) + " m/s");
        weatherForecast.setText("Prakiraan 7 hari: " + w.forecast);
        weatherUpdated.setText("Pembaruan: " + ZonedDateTime.now(WIB).format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss", Locale.US)));
        weatherSw.setText(Double.isFinite(w.sw) ? "Shortwave " + fmt(w.sw,0) + " W/m²" : "Shortwave tidak tersedia");
        weatherPar.setText(Double.isFinite(w.par) ? "PAR estimasi " + fmt(w.par,0) + " W/m²" : "PAR tidak tersedia");
        weatherPpfd.setText(Double.isFinite(w.ppfd) ? "PPFD estimasi " + fmt(w.ppfd,0) + " µmol/m²/s" : "PPFD tidak tersedia");
        weatherPpfdNote.setText(Double.isFinite(w.sw) ? "PAR = 0,45 × Shortwave; PPFD = PAR × 4,57 µmol/J (estimasi spektral broadband)" : "Provider ini tidak memberikan radiasi surya pada respons ini; data PAR/PPFD tidak diestimasi dari variabel yang tidak tersedia.");

        String crop = prefs.getString("crop", "Tanaman pertanian");
        double n = num(prefs.getString("soil_n", ""));
        double p = num(prefs.getString("soil_p", ""));
        double k = num(prefs.getString("soil_k", ""));
        double ph = num(prefs.getString("soil_ph", ""));
        double ec = num(prefs.getString("soil_ec_us_cm", ""));
        double moist = num(prefs.getString("soil_moisture_pct", ""));
        double ece = num(prefs.getString("soil_ece_ds_m", ""));
        double nLow = num(prefs.getString("soil_n_low", ""));
        double nHigh = num(prefs.getString("soil_n_high", ""));
        AgronomyEngine.CropProfile cp = AgronomyEngine.profile(crop);
        prefs.edit().putString("om_temp",fmt(w.temp,2)).putString("om_rh",fmt(w.rh,2))
                .putString("om_apparent_temp",fmt(w.apparent,2)).putString("om_dewpoint",fmt(w.dewpoint,2))
                .putString("om_pressure",fmt(w.pressure,2)).putString("om_rain",fmt(w.rainDay,2))
                .putString("om_et0",fmt(w.et0,2)).putString("om_vpd",fmt(w.vpd,3))
                .putString("om_wind_speed",fmt(w.windSpeed,2)).putString("om_wind_direction",compass(w.windDir))
                .putString("om_wind_gust",fmt(w.gust,2)).putString("om_cloud_cover",fmt(w.cloud,0))
                .putString("om_visibility",fmt(w.visibility,0)).putString("om_uv",fmt(w.uv,1))
                .putString("om_sun_hours",fmt(w.sunHours,2)).putString("om_radiation",fmt(w.sw,2))
                .putString("om_par",fmt(w.par,3)).putString("om_ppfd",fmt(w.ppfd,3))
                .putString("om_soil_temp",fmt(w.soilTemp,2)).putString("om_soil_moisture",fmt(w.soilMoisture,2))
                .putString("om_weather",wmo(w.code)).putString("om_forecast_7d",w.forecast).apply();

        soil.setText("pH " + fmt(ph,2) + " • N tersedia " + fmt(n,1) + " • P " + fmt(p,1) + " • K " + fmt(k,1) + " mg/kg\nEC " + fmt(ec,0) + " µS/cm • kelembapan " + fmt(moist,1) + " %");
        String nPred = NitrogenInference.estimateTotalN(n);
        String nClass = NitrogenInference.classifyRange(n);
        soilStatus.setText("pH: " + AgronomyEngine.classifyPH(ph,cp)
                + "\nN: " + AgronomyEngine.classifyN(n,nLow,nHigh)
                + "\nN-total prediksi: " + nPred
                + "\nKelas N-total yang mungkin: " + nClass
                + "\nP: " + AgronomyEngine.classifyP(p,prefs.getString("soil_test_method",""))
                + " • K: " + AgronomyEngine.classifyK(k,prefs.getString("soil_test_method",""))
                + "\nEC sensor: " + AgronomyEngine.classifyEC(ec,cp)
                + "\nECe lab: " + (Double.isFinite(ece)?AgronomyEngine.classifyECe(ece):"belum ada"));
        int hst = (int)Math.round(num(prefs.getString("farm_hst","-1")));
        optRisk.setText(AgronomyEngine.optRisk(crop,w.temp,w.rh,w.rainDay,w.windSpeed,recentOpt(),hst));
        String all = AgronomyEngine.comprehensiveRangeAnalysis(
                crop, ph, n, nLow, nHigh, p, k, ec, ece, moist,
                num(prefs.getString("soil_fc_pct","")), num(prefs.getString("soil_pwp_pct","")), w.soilMoisture, w.soilTemp,
                num(prefs.getString("soil_om_pct","")), num(prefs.getString("soil_cec","")),
                num(prefs.getString("soil_bulk_density_g_cm3","")), num(prefs.getString("soil_depth_cm","")),
                w.temp, w.apparent, w.dewpoint, w.rh, w.pressure, w.rainDay, w.et0,
                w.windSpeed, w.gust, w.cloud, w.uv, w.visibility,
                num(prefs.getString("lux", "")), w.sw, w.par, w.ppfd, w.sunHours, w.vpd, prefs.getString("soil_test_method",""));
        StringBuilder allText = new StringBuilder(all).append("\n\nREKOMENDASI KESELURUHAN\n");
        if (Double.isFinite(w.vpd) && w.vpd > 2) allText.append("• VPD tinggi. Bersamaan tanah kering, risiko stress lebih serius. Cek air zona akar.\n");
        if (Double.isFinite(w.et0) && Double.isFinite(w.rainDay) && w.rainDay < w.et0) allText.append("• Hujan < ET0. Jangan menambah irigasi hanya dari angka hujan; cek cadangan air tanah.\n");
        if (Double.isFinite(ph) && (ph < cp.phMin || ph > cp.phMax)) allText.append("• pH di luar kisaran profil komoditas: gunakan dasar koreksi laboratorium/buffer, bukan pH tunggal.\n");
        if (Double.isFinite(ec) && ec/1000.0 > cp.ecThresholdDsM) allText.append("• EC sensor di atas ambang profil: cek air/drainase dan konfirmasi ECe bila salinitas dicurigai.\n");
        if (Double.isFinite(moist) && moist < 20) allText.append("• Sensor menunjukkan tanah kering. Prioritaskan cek air sebelum pupuk larut.\n");
        allText.append("• ").append(AgronomyEngine.optRisk(crop,w.temp,w.rh,w.rainDay,w.windSpeed,"",hst));
        overall.setText(allText.toString());
        lastAccess.setText("Cuaca diperbarui: " + ZonedDateTime.now(WIB).format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)) + " WIB");
        finalStatus.setText("Open-Meteo + ThingSpeak • PPFD aktif • Field ThingSpeak otomatis terdeteksi");
    }

    private void loadThingSpeak(boolean manual) {
        if (tsLoading) return;
        tsLoading = true;
        String ch = prefs.getString("channel", "").trim();
        if (ch.isEmpty()) ch = DEFAULT_CHANNEL;
        String key = prefs.getString("read_key", "").trim();
        if (key.isEmpty()) key = DEFAULT_READ_KEY;
        if (ch.isEmpty()) {
            statusChip.setText("THINGSPeak BELUM DIATUR");
            tsLoading = false;
            return;
        }
        final String channel = ch;
        final String readKey = key;
        statusChip.setText(manual ? "MEMUAT THINGSPEAK..." : "THINGSPEAK TERHUBUNG");
        net.execute(() -> {
            try {
                JSONObject meta = null;
                try {
                    meta = getJson("https://api.thingspeak.com/channels/" + URLEncoder.encode(channel,"UTF-8") + ".json"
                            + (readKey.isEmpty() ? "" : "?api_key=" + URLEncoder.encode(readKey,"UTF-8")));
                } catch (Exception ignored) {}
                JSONObject feed = getJson("https://api.thingspeak.com/channels/" + URLEncoder.encode(channel,"UTF-8")
                        + "/feeds/last.json?timezone=Asia%2FJakarta&status=true"
                        + (readKey.isEmpty() ? "" : "&api_key=" + URLEncoder.encode(readKey,"UTF-8")));
                final JSONObject metadata = meta;
                final String[] vals = new String[8];
                for (int i=0;i<8;i++) vals[i] = feed.optString("field"+(i+1),"");
                final String created = feed.optString("created_at","");
                runOnUiThread(() -> renderThingSpeak(metadata, vals, created, channel));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusChip.setText("THINGSPEAK GAGAL");
                    finalStatus.setText("ThingSpeak: " + msg(e));
                    tsLoading = false;
                });
            }
        });
    }

    private void renderThingSpeak(JSONObject metadata, String[] vals, String created, String ch) {
        for (int i=0;i<8;i++) {
            detectedNames[i] = metadata == null ? "Field " + (i+1) : metadata.optString("field"+(i+1), "Field " + (i+1));
            latestValues[i] = vals[i];
            String custom = prefs.getString("field_name_"+(i+1), "").trim();
            String unit = prefs.getString("field_unit_"+(i+1), "").trim();
            String displayName = custom.isEmpty() ? detectedNames[i] : custom;
            if (displayName.isEmpty()) displayName = "Field " + (i+1);
            fieldLabels[i].setText(displayName);
            fieldValues[i].setText(vals[i].isEmpty() ? "--" : vals[i]);
            fieldUnits[i].setText(unit);
            prefs.edit()
                    .putString("ts_field_"+(i+1), vals[i])
                    .putString("ts_field_name_"+(i+1), detectedNames[i])
                    .putString("ts_field_unit_"+(i+1), unit)
                    .apply();
        }
        prefs.edit().putString("ts_channel", ch).putString("ts_created_at", created).apply();
        String titleFromChannel = metadata == null ? "" : metadata.optString("name","");
        channelView.setText("CHANNEL: " + ch + (titleFromChannel.isEmpty() ? "" : " • " + titleFromChannel));
        dataTime.setText("Data ThingSpeak: " + (created.isEmpty() ? "--" : created.replace("T"," ").replace("Z","")));
        statusChip.setText("ONLINE • THINGSPEAK");
        lastAccess.setText("Akses ThingSpeak: " + ZonedDateTime.now(WIB).format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)) + " WIB");
        tsLoading = false;
    }

    private JSONObject getJson(String urlString) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection)new URL(urlString).openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(9000);
            c.setReadTimeout(15000);
            c.setUseCaches(false);
            c.setRequestProperty("Accept","application/json");
            int code = c.getResponseCode();
            InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            String body = readAll(in);
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code + (body.trim().isEmpty()?"":" — "+body.substring(0,Math.min(200,body.length()))));
            if (body.trim().isEmpty()) throw new Exception("Respons kosong");
            return new JSONObject(body);
        } finally { if (c != null) c.disconnect(); }
    }

    private double first(JSONObject o, String key) {
        JSONArray a = o.optJSONArray(key);
        return a == null || a.length()==0 ? Double.NaN : a.optDouble(0,Double.NaN);
    }

    private String firstString(JSONObject o, String key) {
        JSONArray a = o.optJSONArray(key);
        return a == null || a.length()==0 ? "" : a.optString(0,"");
    }

    private String forecastSummary(JSONObject d) {
        JSONArray dates=d.optJSONArray("time"), tmax=d.optJSONArray("temperature_2m_max"), tmin=d.optJSONArray("temperature_2m_min"), rain=d.optJSONArray("precipitation_sum"), et=d.optJSONArray("et0_fao_evapotranspiration");
        if (dates == null) return "--";
        StringBuilder s=new StringBuilder();
        for(int i=0;i<dates.length()&&i<7;i++){
            if(i>0)s.append(" | ");
            s.append(dates.optString(i,"--")).append(" ")
                    .append(fmt(tmin==null?Double.NaN:tmin.optDouble(i,Double.NaN),0)).append("–")
                    .append(fmt(tmax==null?Double.NaN:tmax.optDouble(i,Double.NaN),0)).append("°C")
                    .append(" hujan ").append(fmt(rain==null?Double.NaN:rain.optDouble(i,Double.NaN),1)).append(" mm")
                    .append(" ET0 ").append(fmt(et==null?Double.NaN:et.optDouble(i,Double.NaN),1));
        }
        return s.toString();
    }

    private void requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_REQ);
            return;
        }
        try {
            LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);
            String provider = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER
                    : (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER : null);
            if (provider == null) { Toast.makeText(this,"Aktifkan Lokasi/GPS HP lalu coba lagi.",Toast.LENGTH_LONG).show(); return; }
            weatherStatus.setText("MENGAMBIL GPS TERBARU...");
            LocationListener listener = new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    lm.removeUpdates(this);
                    prefs.edit().putFloat("latitude",(float)location.getLatitude())
                            .putFloat("longitude",(float)location.getLongitude()).apply();
                    lastWeatherEpoch = 0L;
                    weatherLoading = false;
                    weatherLocation.setText(String.format(Locale.US,"GPS baru %.6f, %.6f",location.getLatitude(),location.getLongitude()));
                    Toast.makeText(MainActivity.this,"Koordinat GPS diperbarui. Memuat cuaca untuk lokasi baru...",Toast.LENGTH_SHORT).show();
                    loadOpenMeteo(true);
                }
                @Override public void onProviderDisabled(String p) { }
                @Override public void onProviderEnabled(String p) { }
                @Override public void onStatusChanged(String p,int status,android.os.Bundle extras) { }
            };
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper());
            handler.postDelayed(() -> {
                try { lm.removeUpdates(listener); } catch (Exception ignored) { }
                if (weatherStatus.getText().toString().contains("MENGAMBIL GPS"))
                    weatherStatus.setText("GPS BELUM DIPEROLEH • pastikan lokasi aktif dan coba lagi");
            }, 20000L);
        } catch(Exception e){Toast.makeText(this,"Gagal membaca GPS HP: "+msg(e),Toast.LENGTH_LONG).show();}
    }

    @Override public void onRequestPermissionsResult(int req,String[] perms,int[] grants){
        super.onRequestPermissionsResult(req,perms,grants);
        if(req==LOCATION_REQ){for(int g:grants)if(g==PackageManager.PERMISSION_GRANTED){requestLocation();return;}}
    }

    private String recentOpt(){
        try{JSONArray a=new JSONArray(prefs.getString("opt_history","[]"));StringBuilder s=new StringBuilder();for(int i=Math.max(0,a.length()-5);i<a.length();i++)s.append(a.optJSONObject(i)).append("; ");return s.toString();}
        catch(Exception e){return "";}
    }

    private double num(String s){try{return s==null||s.trim().isEmpty()?Double.NaN:Double.parseDouble(s.trim().replace(',','.'));}catch(Exception e){return Double.NaN;}}
    private String fmt(double v,int d){return Double.isFinite(v)?String.format(Locale.US,"%."+d+"f",v):"--";}
    private String msg(Exception e){return e.getMessage()==null?"kesalahan tidak diketahui":e.getMessage();}
    private String readAll(InputStream in)throws Exception{if(in==null)return "";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String l;while((l=r.readLine())!=null)b.append(l);}return b.toString();}
    private String shortTime(String s){if(s==null||s.isEmpty())return "--";int p=s.indexOf('T');if(p>=0&&s.length()>=p+6)return s.substring(p+1,p+6);return s;}
    public static String compass(double deg){if(!Double.isFinite(deg))return "--";String[] p={"Utara","Utara-Timur Laut","Timur Laut","Timur-Timur Laut","Timur","Timur-Tenggara","Tenggara","Selatan-Tenggara","Selatan","Selatan-Barat Daya","Barat Daya","Barat-Barat Daya","Barat","Barat-Barat Laut","Barat Laut","Utara-Barat Laut"};int i=(int)Math.floor((deg+11.25)/22.5)%16;return p[i];}
    private String wmo(int c){switch(c){case 0:return "Cerah";case 1:case 2:return "Cerah berawan / sebagian berawan";case 3:return "Berawan";case 45:case 48:return "Kabut";case 51:case 53:case 55:return "Gerimis";case 61:case 63:case 65:return "Hujan";case 66:case 67:return "Hujan beku";case 71:case 73:case 75:return "Salju";case 80:case 81:case 82:return "Hujan deras lokal";case 95:return "Badai petir";default:return "Kode WMO "+c;}}

    private static final class Weather{
        double temp,rh,apparent,dewpoint,rainNow,pressure,cloud,windSpeed,windDir,gust,visibility,uv,sw,par,ppfd,vpd,soilTemp,soilMoisture,et0,sunHours,rainDay,elevation;
        int code; String forecast,sunrise,sunset,provider,conditionText;
    }
}
