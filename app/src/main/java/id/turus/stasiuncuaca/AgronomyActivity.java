package id.turus.stasiuncuaca;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Modul data lahan + tanah + pemupukan + OPT + analisis AI + histori + PDF.
 * Fitur lama dipertahankan; field baru menambah konteks agar analisis lebih spesifik.
 */
public class AgronomyActivity extends Activity {
    private static final String PREFS = "thingspeak_config";
    private static final String KEY_FERT = "fert_history";
    private static final String KEY_OPT = "opt_history";
    private static final String KEY_DATA_HISTORY = "agro_data_history";
    private static final String KEY_ANALYSIS_HISTORY = "agro_analysis_history";
    private static final String KEY_AI_HISTORY = "agro_ai_history";
    private static final int LOCATION_REQ = 712;
    private static final int CREATE_PDF_REQ = 713;
    private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss 'WIB'", Locale.US);
    private static final String DEFAULT_AI_MODEL = "gpt-6-luna";

    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private LocationManager locationManager;

    private EditText farmerName, crop, age, area, landLat, landLon;
    private Spinner ageUnit;
    private EditText ph, n, p, k, ec, moist, targetN, targetP, targetK, dolomite, manure, poc;
    private TextView recommendation, fertHistory, optHistory, optAnalysis, aiStatus, aiAdvice;
    private TextView dataHistory, analysisHistory, aiHistory;
    private String lastReport = "";
    private String lastAiAdvice = "";
    private boolean aiRunning = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_agronomy);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        bind();
        load();
        refresh();

        findViewById(R.id.back).setOnClickListener(v -> finish());
        findViewById(R.id.saveFarm).setOnClickListener(v -> {
            if (validate()) {
                save(true);
                Toast.makeText(this, "Profil lahan tersimpan.", Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.captureLocation).setOnClickListener(v -> captureLandLocation());
        findViewById(R.id.calculate).setOnClickListener(v -> calculate());
        findViewById(R.id.addFertilizer).setOnClickListener(v -> fertDialog());
        findViewById(R.id.addOpt).setOnClickListener(v -> optDialog());
        findViewById(R.id.aiAgronomyButton).setOnClickListener(v -> ai());
        findViewById(R.id.dataHistoryButton).setOnClickListener(v -> showHistoryList(KEY_DATA_HISTORY, "RIWAYAT DATA LAHAN"));
        findViewById(R.id.analysisHistoryButton).setOnClickListener(v -> showHistoryList(KEY_ANALYSIS_HISTORY, "RIWAYAT ANALISIS & REKOMENDASI"));
        findViewById(R.id.aiHistoryButton).setOnClickListener(v -> showHistoryList(KEY_AI_HISTORY, "RIWAYAT REKOMENDASI AI"));
        findViewById(R.id.deleteFertilizerHistory).setOnClickListener(v -> confirmClear(KEY_FERT, "riwayat pemupukan"));
        findViewById(R.id.deleteOptHistory).setOnClickListener(v -> confirmClear(KEY_OPT, "riwayat pengendalian OPT"));
        findViewById(R.id.pdfButton).setOnClickListener(v -> createPdfFile());
        findViewById(R.id.printButton).setOnClickListener(v -> printReport());
    }

    @Override
    protected void onDestroy() {
        net.shutdownNow();
        super.onDestroy();
    }

    private void bind() {
        farmerName = findViewById(R.id.farmerName);
        crop = findViewById(R.id.cropA);
        age = findViewById(R.id.ageValue);
        ageUnit = findViewById(R.id.ageUnit);
        area = findViewById(R.id.areaHa);
        landLat = findViewById(R.id.landLat);
        landLon = findViewById(R.id.landLon);
        ph = findViewById(R.id.soilPh);
        n = findViewById(R.id.soilN);
        p = findViewById(R.id.soilP);
        k = findViewById(R.id.soilK);
        ec = findViewById(R.id.soilEc);
        moist = findViewById(R.id.soilMoisture);
        targetN = findViewById(R.id.targetN);
        targetP = findViewById(R.id.targetP);
        targetK = findViewById(R.id.targetK);
        dolomite = findViewById(R.id.targetDolomite);
        manure = findViewById(R.id.targetManure);
        poc = findViewById(R.id.targetPoc);
        recommendation = findViewById(R.id.recommendation);
        fertHistory = findViewById(R.id.fertHistory);
        optHistory = findViewById(R.id.optHistory);
        optAnalysis = findViewById(R.id.optAnalysis);
        aiStatus = findViewById(R.id.aiAgronomyStatus);
        aiAdvice = findViewById(R.id.aiAgronomyAdvice);
        dataHistory = findViewById(R.id.dataHistory);
        analysisHistory = findViewById(R.id.analysisHistory);
        aiHistory = findViewById(R.id.aiHistory);
    }

    private void load() {
        farmerName.setText(prefs.getString("farmer_name", ""));
        crop.setText(prefs.getString("crop", prefs.getString("commodity", "")));
        String savedAge = prefs.getString("farm_age_value", "");
        if (savedAge.isEmpty()) savedAge = prefs.getString("farm_age_months", "");
        age.setText(savedAge);
        String unit = prefs.getString("farm_age_unit", "Bulan");
        String[] units = getResources().getStringArray(R.array.age_units);
        for (int i = 0; i < units.length; i++) {
            if (units[i].equalsIgnoreCase(unit)) { ageUnit.setSelection(i); break; }
        }
        area.setText(prefs.getString("farm_area_ha", ""));
        landLat.setText(prefs.getString("land_lat", ""));
        landLon.setText(prefs.getString("land_lon", ""));
        ph.setText(prefs.getString("soil_ph", ""));
        n.setText(prefs.getString("soil_n", ""));
        p.setText(prefs.getString("soil_p", ""));
        k.setText(prefs.getString("soil_k", ""));
        ec.setText(prefs.getString("soil_ec_us_cm", ""));
        moist.setText(prefs.getString("soil_moisture_pct", ""));
        targetN.setText(prefs.getString("target_n_kg_ha", ""));
        targetP.setText(prefs.getString("target_p2o5_kg_ha", ""));
        targetK.setText(prefs.getString("target_k2o_kg_ha", ""));
        dolomite.setText(prefs.getString("target_dolomite_kg_ha", ""));
        manure.setText(prefs.getString("target_manure_kg_ha", ""));
        poc.setText(prefs.getString("target_poc_l_ha", ""));
        lastReport = prefs.getString("last_agro_report", "");
        lastAiAdvice = prefs.getString("last_agro_ai", "");
        if (!lastReport.isEmpty()) recommendation.setText(lastReport);
        if (!lastAiAdvice.isEmpty()) aiAdvice.setText(lastAiAdvice);
    }

    private void save(boolean addSnapshot) {
        SharedPreferences.Editor e = prefs.edit();
        String ageValue = s(age);
        String selectedUnit = ageUnit.getSelectedItem() == null ? "Bulan" : ageUnit.getSelectedItem().toString();
        double ageMonths = ageInMonths();
        e.putString("farmer_name", s(farmerName))
                .putString("crop", s(crop))
                .putString("commodity", s(crop))
                .putString("farm_age_value", ageValue)
                .putString("farm_age_unit", selectedUnit)
                .putString("farm_age_months", Double.isFinite(ageMonths) ? f(ageMonths, 2) : "")
                .putString("farm_area_ha", s(area))
                .putString("land_lat", s(landLat))
                .putString("land_lon", s(landLon))
                .putString("soil_ph", s(ph))
                .putString("soil_n", s(n))
                .putString("soil_p", s(p))
                .putString("soil_k", s(k))
                .putString("soil_ec_us_cm", s(ec))
                .putString("soil_moisture_pct", s(moist))
                .putString("target_n_kg_ha", s(targetN))
                .putString("target_p2o5_kg_ha", s(targetP))
                .putString("target_k2o_kg_ha", s(targetK))
                .putString("target_dolomite_kg_ha", s(dolomite))
                .putString("target_manure_kg_ha", s(manure))
                .putString("target_poc_l_ha", s(poc));
        e.apply();
        if (addSnapshot) saveHistoryItem(KEY_DATA_HISTORY, "Snapshot " + now(), buildDataSnapshot(), 30);
    }

    private boolean validate() {
        if (s(farmerName).isEmpty()) { farmerName.setError("Nama petani wajib diisi"); return false; }
        if (s(crop).isEmpty()) { crop.setError("Nama komoditas wajib diisi"); return false; }
        double a = num(area);
        if (!Double.isFinite(a) || a <= 0) { area.setError("Luas lahan harus > 0 ha"); return false; }
        if (!range(age, 0, 1e5)) return false;
        double lat = num(landLat), lon = num(landLon);
        if (!s(landLat).isEmpty() && (!Double.isFinite(lat) || lat < -90 || lat > 90)) { landLat.setError("Latitude -90 sampai 90"); return false; }
        if (!s(landLon).isEmpty() && (!Double.isFinite(lon) || lon < -180 || lon > 180)) { landLon.setError("Longitude -180 sampai 180"); return false; }
        if (!range(ph,0,14) || !range(n,0,1e9) || !range(p,0,1e9) || !range(k,0,1e9)
                || !range(ec,0,1e9) || !range(moist,0,100)) return false;
        return range(targetN,0,1e9) && range(targetP,0,1e9) && range(targetK,0,1e9)
                && range(dolomite,0,1e9) && range(manure,0,1e9) && range(poc,0,1e9);
    }

    private boolean range(EditText e, double lo, double hi) {
        String x = s(e); if (x.isEmpty()) return true;
        double v = num(e); if (!Double.isFinite(v) || v < lo || v > hi) { e.setError("Nilai tidak valid"); return false; }
        return true;
    }

    private double ageInDays() {
        double v = num(age); if (!Double.isFinite(v) || v < 0) return Double.NaN;
        String u = ageUnit.getSelectedItem() == null ? "Bulan" : ageUnit.getSelectedItem().toString();
        if (u.equalsIgnoreCase("Hari")) return v;
        if (u.equalsIgnoreCase("Minggu")) return v * 7.0;
        if (u.equalsIgnoreCase("Tahun")) return v * 365.25;
        return v * 30.4375;
    }

    private double ageInMonths() {
        double d = ageInDays(); return Double.isFinite(d) ? d / 30.4375 : Double.NaN;
    }

    private String ageDescription() {
        if (s(age).isEmpty()) return "--";
        String u = ageUnit.getSelectedItem() == null ? "Bulan" : ageUnit.getSelectedItem().toString();
        double days = ageInDays();
        return s(age) + " " + u + (Double.isFinite(days) ? " (~" + f(days, 0) + " hari)" : "");
    }

    private void calculate() {
        if (!validate()) return;
        save(false);
        double a = num(area);
        double tn = z(targetN), tp = z(targetP), tk = z(targetK), td = z(dolomite), tm = z(manure), tc = z(poc);
        double[] c = credit();
        double nr = Math.max(0, tn-c[0]), pr = Math.max(0, tp-c[1]), kr = Math.max(0, tk-c[2]);
        double dr = Math.max(0, td-categoryCredit("Dolomit","kg/ha"));
        double mr = Math.max(0, tm-categoryCredit("Pupuk kandang","kg/ha"));
        double cr = Math.max(0, tc-categoryCredit("POC","L/ha"));

        StringBuilder r = new StringBuilder();
        r.append("HASIL HITUNG PEMUPUKAN BERIKUTNYA\n\n");
        r.append("Petani: ").append(dash(s(farmerName))).append("\n");
        r.append("Komoditas: ").append(dash(s(crop))).append("\n");
        r.append("Umur tanam: ").append(ageDescription()).append("\n");
        r.append("Luas: ").append(f(a,2)).append(" ha\n");
        r.append("Koordinat lahan: ").append(coordText()).append("\n\n");
        r.append("Sisa kebutuhan hara per ha:\n");
        r.append("N: ").append(f(nr,2)).append(" kg/ha\nP2O5: ").append(f(pr,2)).append(" kg/ha\nK2O: ").append(f(kr,2)).append(" kg/ha\n\n");
        r.append("Contoh pupuk tunggal:\n");
        r.append("Urea 46%: ").append(f(nr/0.46,2)).append(" kg/ha (total ").append(f(nr/0.46*a,2)).append(" kg)\n");
        r.append("SP-36 36%: ").append(f(pr/0.36,2)).append(" kg/ha (total ").append(f(pr/0.36*a,2)).append(" kg)\n");
        r.append("KCl 60%: ").append(f(kr/0.60,2)).append(" kg/ha (total ").append(f(kr/0.60*a,2)).append(" kg)\n");
        r.append("Dolomit tersisa: ").append(f(dr,2)).append(" kg/ha\n");
        r.append("Pupuk kandang tersisa: ").append(f(mr,2)).append(" kg/ha\n");
        r.append("POC tersisa: ").append(f(cr,2)).append(" L/ha\n\n");
        r.append("4 T PEMUPUKAN:\n");
        r.append("1. Tepat jenis – sesuaikan sumber hara dengan target.\n");
        r.append("2. Tepat dosis – target dikurangi kredit riwayat pupuk.\n");
        r.append("3. Tepat waktu – sesuaikan fase tanaman dan kondisi air.\n");
        r.append("4. Tepat cara – gunakan metode aplikasi sesuai jenis pupuk dan kondisi lahan.\n\n");
        r.append("PEMERIKSAAN TANAH:\n");
        r.append("pH ").append(dash(s(ph))).append(" • N ").append(dash(s(n))).append(" mg/kg • P ")
                .append(dash(s(p))).append(" mg/kg • K ").append(dash(s(k))).append(" mg/kg • EC ")
                .append(dash(s(ec))).append(" uS/cm • kelembapan ").append(dash(s(moist))).append(" %\n");
        double pv=num(ph), ev=num(ec), mv=num(moist);
        if (Double.isFinite(pv) && pv<5.0) r.append("• pH rendah: kebutuhan kapur memerlukan dasar analisis pengapuran, bukan pH saja.\n");
        if (Double.isFinite(pv) && pv>7.5) r.append("• pH tinggi: jangan menambah dolomit tanpa dasar analisis.\n");
        if (Double.isFinite(ev) && ev>2000) r.append("• EC > 2.000 uS/cm: evaluasi garam terlarut, sumber air dan riwayat pupuk.\n");
        if (Double.isFinite(mv) && mv<25) r.append("• Kelembapan terukur cukup rendah; cek kondisi air sebelum pemupukan.\n");
        if (Double.isFinite(mv) && mv>80) r.append("• Kelembapan sangat tinggi; hindari aplikasi yang berisiko banyak hilang.\n");
        r.append("\nCatatan: angka dosis dihitung dari target yang Anda masukkan dan riwayat pupuk yang tersimpan. Jangan menganggap hasil ini menggantikan rekomendasi uji tanah/lapang yang sah.");

        lastReport = r.toString();
        prefs.edit().putString("last_agro_report", lastReport).apply();
        recommendation.setText(lastReport);
        saveHistoryItem(KEY_ANALYSIS_HISTORY, "Analisis " + now(), lastReport, 20);
        refresh();
    }

    private double[] credit() {
        double N=0,P=0,K=0;
        try {
            JSONArray a=new JSONArray(prefs.getString(KEY_FERT,"[]"));
            LocalDate cut=LocalDate.now(WIB).minusDays(180); String cropNow=s(crop);
            for(int i=0;i<a.length();i++){
                JSONObject o=a.optJSONObject(i); if(o==null||!"kg/ha".equals(o.optString("unit","kg/ha")))continue;
                if(!cropNow.isEmpty()&&!cropNow.equalsIgnoreCase(o.optString("crop","")))continue;
                try{if(LocalDate.parse(o.optString("date",""),DF).isBefore(cut))continue;}catch(Exception ex){continue;}
                double d=o.optDouble("dose",0);N+=d*o.optDouble("nPct",0)/100;P+=d*o.optDouble("pPct",0)/100;K+=d*o.optDouble("kPct",0)/100;
            }
        }catch(Exception ignored){}
        return new double[]{N,P,K};
    }

    private double categoryCredit(String cat,String unit){
        double q=0;
        try{
            JSONArray a=new JSONArray(prefs.getString(KEY_FERT,"[]")); LocalDate cut=LocalDate.now(WIB).minusDays(180); String cropNow=s(crop);
            for(int i=0;i<a.length();i++){
                JSONObject o=a.optJSONObject(i); if(o==null||!cat.equals(o.optString("category",""))||!unit.equals(o.optString("unit","")))continue;
                if(!cropNow.isEmpty()&&!cropNow.equalsIgnoreCase(o.optString("crop","")))continue;
                try{if(LocalDate.parse(o.optString("date",""),DF).isBefore(cut))continue;}catch(Exception ex){continue;}
                q+=o.optDouble("dose",0);
            }
        }catch(Exception ignored){}
        return q;
    }

    private void fertDialog(){
        LinearLayout r=root(); String[] cats={"Pupuk N","Pupuk P","Pupuk K","NPK","Dolomit","Pupuk kandang","POC","Lainnya"}; String[] units={"kg/ha","L/ha"};
        EditText date=edit(r,"Tanggal (YYYY-MM-DD)",LocalDate.now(WIB).format(DF),false), product=edit(r,"Nama pupuk","",false), dose=edit(r,"Dosis produk per ha","",true), nPct=edit(r,"Kandungan N (%)","",true), pPct=edit(r,"Kandungan P2O5 (%)","",true), kPct=edit(r,"Kandungan K2O (%)","",true), method=edit(r,"Cara pemberian","",false), stage=edit(r,"Umur/fase saat aplikasi","",false), note=edit(r,"Catatan hasil/pengamatan","",false);
        Spinner cat=spinner(r,"Jenis catatan",cats), unit=spinner(r,"Satuan dosis",units);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Tambah Riwayat Pemupukan").setView(wrap(r)).setNegativeButton("BATAL",null).setPositiveButton("SIMPAN",null).create();
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{double dv=num(dose);if(product.getText().toString().trim().isEmpty()||!validDate(s(date))||!Double.isFinite(dv)||dv<0){Toast.makeText(this,"Lengkapi tanggal, nama pupuk, dan dosis.",Toast.LENGTH_SHORT).show();return;}try{JSONObject o=new JSONObject();o.put("date",s(date));o.put("crop",s(crop));o.put("product",s(product));o.put("category",cat.getSelectedItem().toString());o.put("dose",dv);o.put("unit",unit.getSelectedItem().toString());o.put("nPct",z(nPct));o.put("pPct",z(pPct));o.put("kPct",z(kPct));o.put("method",s(method));o.put("stage",s(stage));o.put("note",s(note));append(KEY_FERT,o,60);d.dismiss();refresh();}catch(Exception ex){Toast.makeText(this,"Gagal menyimpan riwayat.",Toast.LENGTH_SHORT).show();}}));
        d.show();
    }

    private void optDialog(){
        LinearLayout r=root(); EditText date=edit(r,"Tanggal (YYYY-MM-DD)",LocalDate.now(WIB).format(DF),false),target=edit(r,"Sasaran OPT","",false),product=edit(r,"Produk","",false),active=edit(r,"Bahan aktif","",false),dose=edit(r,"Dosis label","",true),conc=edit(r,"Konsentrasi (%)","",true),water=edit(r,"Volume air (L/ha)","",true),application=edit(r,"Cara aplikasi","",false),interval=edit(r,"Interval aplikasi","",false),result=edit(r,"Hasil pengendalian","",false),registered=edit(r,"Status label/pendaftaran produk","Belum dicek",false);
        Spinner method=spinner(r,"Metode",new String[]{"Pengamatan","Manual/Mekanis","Biologis","Kimia"});
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Tambah Riwayat Pengendalian OPT").setView(wrap(r)).setNegativeButton("BATAL",null).setPositiveButton("SIMPAN",null).create();
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{if(!validDate(s(date))||s(target).isEmpty()){Toast.makeText(this,"Tanggal dan sasaran OPT wajib diisi.",Toast.LENGTH_SHORT).show();return;}try{JSONObject o=new JSONObject();o.put("date",s(date));o.put("crop",s(crop));o.put("target",s(target));o.put("method",method.getSelectedItem().toString());o.put("product",s(product));o.put("active",s(active));putNum(o,"dose",dose);o.put("doseUnit","sesuai label");putNum(o,"concentrationPct",conc);putNum(o,"waterLHa",water);o.put("application",s(application));o.put("interval",s(interval));o.put("result",s(result));o.put("registered",s(registered));append(KEY_OPT,o,60);d.dismiss();refresh();}catch(Exception ex){Toast.makeText(this,"Gagal menyimpan riwayat OPT.",Toast.LENGTH_SHORT).show();}}));
        d.show();
    }

    private void refresh(){
        fertHistory.setText(history(KEY_FERT,"RIWAYAT PEMUPUKAN"));
        optHistory.setText(history(KEY_OPT,"RIWAYAT PENGENDALIAN OPT"));
        optAnalysis.setText(analyzeOpt());
        dataHistory.setText(summaryHistory(KEY_DATA_HISTORY,"RIWAYAT DATA LAHAN"));
        analysisHistory.setText(summaryHistory(KEY_ANALYSIS_HISTORY,"RIWAYAT ANALISIS"));
        aiHistory.setText(summaryHistory(KEY_AI_HISTORY,"RIWAYAT AI"));
    }

    private String history(String key,String title){
        try{JSONArray a=new JSONArray(prefs.getString(key,"[]"));if(a.length()==0)return title+"\nBelum ada catatan.";StringBuilder b=new StringBuilder(title+" (maks. 60)\n");int start=Math.max(0,a.length()-20);for(int i=a.length()-1;i>=start;i--){JSONObject o=a.optJSONObject(i);if(o==null)continue;b.append(o.optString("date",o.optString("time","--"))).append(" • ").append(o.optString(key.equals(KEY_FERT)?"product":"target","--"));if(key.equals(KEY_FERT))b.append(" • ").append(f(o.optDouble("dose",Double.NaN),2)).append(" ").append(o.optString("unit",""));else b.append(" • ").append(o.optString("method","--")).append(" • ").append(o.optString("result",""));b.append("\n");}return b.toString().trim();}catch(Exception e){return title+"\nRiwayat rusak/tidak terbaca.";}
    }

    private String analyzeOpt(){
        try{JSONArray a=new JSONArray(prefs.getString(KEY_OPT,"[]"));String c=s(crop);JSONObject latest=null;for(int i=a.length()-1;i>=0;i--){JSONObject o=a.optJSONObject(i);if(o!=null&&(c.isEmpty()||c.equalsIgnoreCase(o.optString("crop","")))){latest=o;break;}}if(latest==null)return "ANALISIS OPT\nBelum ada catatan untuk tanaman ini.";StringBuilder b=new StringBuilder("ANALISIS OPT TERAKHIR\n");b.append("Sasaran: ").append(dash(latest.optString("target",""))).append("\nMetode: ").append(dash(latest.optString("method",""))).append("\n");if("Kimia".equalsIgnoreCase(latest.optString("method",""))){b.append("1. Tepat sasaran: SUDAH dicatat\n");b.append("2. Tepat jenis: ").append(latest.optString("active","").isEmpty()?"BELUM":"SUDAH dicatat").append("\n");b.append("3. Tepat dosis/konsentrasi: ").append((latest.has("dose")||latest.has("concentrationPct"))?"SUDAH dicatat":"BELUM").append("\n");b.append("4. Tepat waktu: ").append(latest.optString("date","").isEmpty()?"BELUM":"SUDAH dicatat").append("\n");b.append("5. Tepat cara: ").append(latest.optString("application","").isEmpty()?"BELUM":"SUDAH dicatat").append("\n");b.append("Cek label/pendaftaran: ").append(latest.optString("registered","Belum dicek")).append("\n");if(!latest.optString("active","").isEmpty()&&usedRecently(latest.optString("active",""),latest.optString("target","")))b.append("PERINGATAN: bahan aktif sama tercatat digunakan baru-baru ini; evaluasi hasil sebelum pengulangan.\n");}else b.append("Utamakan pengamatan lapang dan metode non-kimia yang sesuai sebelum intervensi kimia.\n");return b.toString().trim();}catch(Exception e){return "ANALISIS OPT\nBelum dapat dihitung.";}
    }

    private boolean usedRecently(String active,String target){try{JSONArray a=new JSONArray(prefs.getString(KEY_OPT,"[]"));LocalDate cut=LocalDate.now(WIB).minusDays(30);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null||!"Kimia".equalsIgnoreCase(o.optString("method","")))continue;if(!active.equalsIgnoreCase(o.optString("active",""))||!target.equalsIgnoreCase(o.optString("target","")))continue;try{if(!LocalDate.parse(o.optString("date",""),DF).isBefore(cut))return true;}catch(Exception ignored){}}}catch(Exception ignored){}return false;}

    private void append(String key,JSONObject o,int max)throws Exception{JSONArray old=new JSONArray(prefs.getString(key,"[]")),out=new JSONArray();int start=Math.max(0,old.length()-max+1);for(int i=start;i<old.length();i++)out.put(old.get(i));out.put(o);prefs.edit().putString(key,out.toString()).apply();}

    private void ai(){
        if(aiRunning)return;
        String key=prefs.getString("ai_api_key","").trim();
        if(key.isEmpty()){aiStatus.setText("AI belum dikonfigurasi. Isi OpenAI API key di Pengaturan.");return;}
        if(!validate())return;
        save(false);
        if(lastReport.isEmpty())calculate();
        aiRunning=true; aiStatus.setText("AI menganalisis petani, lahan, tanah, cuaca, pupuk, dan OPT…"); findViewById(R.id.aiAgronomyButton).setEnabled(false);
        String model=prefs.getString("ai_model",DEFAULT_AI_MODEL).trim(); if(model.isEmpty())model=DEFAULT_AI_MODEL;
        String input=buildInput(); final String m=model;
        net.execute(()->{try{String out=callAi(key,m,input);runOnUiThread(()->{lastAiAdvice=out;aiAdvice.setText(out);aiStatus.setText("Analisis AI selesai • data tersimpan ke riwayat.");findViewById(R.id.aiAgronomyButton).setEnabled(true);prefs.edit().putString("last_agro_ai",out).apply();saveHistoryItem(KEY_AI_HISTORY,"AI " + now(),out,20);refresh();});}catch(Exception ex){runOnUiThread(()->{aiAdvice.setText("Analisis AI gagal: "+safe(ex));aiStatus.setText("AI tidak tersedia.");findViewById(R.id.aiAgronomyButton).setEnabled(true);});}});
    }

    private String buildInput(){
        StringBuilder b=new StringBuilder();
        b.append("PROFIL PETANI DAN LAHAN\n");
        b.append("Nama petani: ").append(dash(s(farmerName))).append("\n");
        b.append("Komoditas: ").append(dash(s(crop))).append("\n");
        b.append("Umur tanam: ").append(ageDescription()).append("\n");
        b.append("Umur standar hari: ").append(Double.isFinite(ageInDays())?f(ageInDays(),1):"--").append("\n");
        b.append("Luas lahan ha: ").append(dash(s(area))).append("\n");
        b.append("Koordinat lahan: ").append(coordText()).append("\n");
        b.append("pH: ").append(dash(s(ph))).append("\nN mg/kg: ").append(dash(s(n))).append("\nP mg/kg: ").append(dash(s(p))).append("\nK mg/kg: ").append(dash(s(k))).append("\nEC uS/cm: ").append(dash(s(ec))).append("\nKelembapan tanah %: ").append(dash(s(moist))).append("\n\n");
        b.append("TARGET HARA\nN: ").append(dash(s(targetN))).append(" kg/ha\nP2O5: ").append(dash(s(targetP))).append(" kg/ha\nK2O: ").append(dash(s(targetK))).append(" kg/ha\nDolomit: ").append(dash(s(dolomite))).append(" kg/ha\nPupuk kandang: ").append(dash(s(manure))).append(" kg/ha\nPOC: ").append(dash(s(poc))).append(" L/ha\n\n");
        b.append("HASIL ANALISIS LOKAL\n").append(lastReport).append("\n\n");
        b.append("ANALISIS OPT\n").append(analyzeOpt()).append("\n\n");
        b.append("DATA CUACA OPEN-METEO TERAKHIR DI PERANGKAT\n").append(dash(prefs.getString("weather_last_report",""))).append("\n\n");
        b.append("DATA THINGSPEAK TERBARU\n").append(buildThingSpeakContext()).append("\n\n");
        b.append("RIWAYAT PUPUK (maks 30)\n").append(historyForAi(KEY_FERT)).append("\n\nRIWAYAT OPT (maks 30)\n").append(historyForAi(KEY_OPT));
        b.append("\n\nATURAN ANALISIS: gunakan data yang tersedia saja, jangan mengarang dosis pestisida. Tandai ketidakpastian. Untuk pemupukan gunakan 4 tepat. Untuk OPT gunakan 5 tepat. Untuk dolomit jangan membuat dosis baru hanya dari pH. Prioritaskan tindakan hari ini, 6-24 jam, lalu evaluasi ulang.");
        return b.toString();
    }

    private String buildThingSpeakContext(){
        try{
            JSONObject weather=new JSONObject(prefs.getString("cache_json","{}")); StringBuilder s=new StringBuilder();
            for(int i=0;i<8;i++){String raw=weather.optString("field"+(i+1),"");if(raw.isEmpty())continue;String name=prefs.getString("field_name_"+(i+1),"").trim();if(name.isEmpty())name="FIELD "+(i+1);String unit=prefs.getString("field_unit_"+(i+1),"").trim();s.append(i+1).append(". ").append(name).append(" = ").append(raw);if(!unit.isEmpty())s.append(" ").append(unit);s.append("\n");}
            s.append("Waktu data: ").append(weather.optString("created_at","--")); return s.toString();
        }catch(Exception e){return "Data ThingSpeak belum tersedia.";}
    }

    private String historyForAi(String key){try{JSONArray a=new JSONArray(prefs.getString(key,"[]"));int start=Math.max(0,a.length()-30);JSONArray out=new JSONArray();for(int i=start;i<a.length();i++)out.put(a.get(i));return out.toString();}catch(Exception e){return "[]";}}

    private String callAi(String key,String model,String input)throws Exception{
        JSONObject p=new JSONObject();
        p.put("model",model);
        p.put("instructions","Anda adalah asisten agronomi lapang. Gunakan identitas petani, komoditas, umur, luas, koordinat, data tanah, cuaca, ThingSpeak, riwayat pupuk dan OPT untuk membuat analisis yang spesifik. Jangan mengarang data. Bedakan fakta, indikasi, dan asumsi. Jangan membuat dosis pestisida baru; ikuti label bila ada. Untuk pemupukan gunakan 4 tepat. Untuk OPT gunakan 5 tepat. Untuk dolomit jangan menetapkan angka baru hanya dari pH. Berikan prioritas tindakan hari ini, 6-24 jam, 2-7 hari, serta data apa yang perlu diukur lagi. Bila data kurang, nyatakan secara tegas apa yang kurang.");
        p.put("input",input); p.put("max_output_tokens",1600);
        HttpURLConnection c=null;try{c=(HttpURLConnection)new URL("https://api.openai.com/v1/responses").openConnection();c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(45000);c.setDoOutput(true);c.setUseCaches(false);c.setRequestProperty("Authorization","Bearer "+key);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");byte[] body=p.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(body.length);try(OutputStream os=c.getOutputStream()){os.write(body);}int code=c.getResponseCode();if(code<200||code>=300)throw new Exception("HTTP "+code+" — "+readAll(c.getErrorStream()));JSONObject r=new JSONObject(readAll(c.getInputStream()));String direct=r.optString("output_text","").trim();if(!direct.isEmpty())return direct;JSONArray out=r.optJSONArray("output");StringBuilder s=new StringBuilder();if(out!=null)for(int i=0;i<out.length();i++){JSONObject it=out.optJSONObject(i);if(it==null)continue;JSONArray ct=it.optJSONArray("content");if(ct==null)continue;for(int j=0;j<ct.length();j++){JSONObject q=ct.optJSONObject(j);if(q!=null&&"output_text".equals(q.optString("type",""))){String t=q.optString("text","").trim();if(!t.isEmpty()){if(s.length()>0)s.append('\n');s.append(t);}}}}if(s.length()>0)return s.toString();throw new Exception("Respons AI tidak berisi teks.");}finally{if(c!=null)c.disconnect();}}

    private void captureLandLocation(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_REQ);return;}
        try{
            Location best=null; Location a=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER), b=locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if(a!=null)best=a; if(b!=null&&(best==null||b.getAccuracy()<best.getAccuracy()))best=b;
            if(best==null){Toast.makeText(this,"Lokasi terakhir belum tersedia. Aktifkan GPS lalu coba lagi.",Toast.LENGTH_LONG).show();return;}
            landLat.setText(String.format(Locale.US,"%.6f",best.getLatitude())); landLon.setText(String.format(Locale.US,"%.6f",best.getLongitude()));
            Toast.makeText(this,"Koordinat lahan diambil dari lokasi HP.",Toast.LENGTH_SHORT).show();
        }catch(SecurityException e){Toast.makeText(this,"Izin lokasi tidak tersedia.",Toast.LENGTH_SHORT).show();}
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){super.onRequestPermissionsResult(requestCode,permissions,grants);if(requestCode==LOCATION_REQ&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)captureLandLocation();}

    private void showHistoryList(String key,String title){
        try{
            JSONArray a=new JSONArray(prefs.getString(key,"[]")); if(a.length()==0){Toast.makeText(this,title+" masih kosong.",Toast.LENGTH_SHORT).show();return;}
            String[] items=new String[a.length()];
            for(int idx=0;idx<a.length();idx++){int i=a.length()-1-idx;JSONObject o=a.optJSONObject(i);String stamp=o==null?"--":o.optString("time",o.optString("date","--"));String text=o==null?"":o.optString("text","");String one=text.replace('\n',' ');if(one.length()>90)one=one.substring(0,90)+"…";items[idx]=stamp+" • "+one;}
            new AlertDialog.Builder(this).setTitle(title).setItems(items,(d,which)->{int actual=a.length()-1-which;showHistoryDetail(key,title,actual);}).setNegativeButton("TUTUP",null).setNeutralButton("HAPUS SEMUA",(d,w)->confirmClear(key,title)).show();
        }catch(Exception e){Toast.makeText(this,"Riwayat tidak terbaca.",Toast.LENGTH_SHORT).show();}
    }

    private void showHistoryDetail(String key,String title,int actual){
        try{JSONArray a=new JSONArray(prefs.getString(key,"[]"));JSONObject o=a.optJSONObject(actual);if(o==null)return;String text=o.optString("text","");new AlertDialog.Builder(this).setTitle(title).setMessage(text).setNegativeButton("TUTUP",null).setPositiveButton("HAPUS CATATAN",(d,w)->deleteAt(key,actual)).show();}catch(Exception ignored){}
    }

    private void deleteAt(String key,int index){
        try{JSONArray a=new JSONArray(prefs.getString(key,"[]")),o=new JSONArray();for(int i=0;i<a.length();i++)if(i!=index)o.put(a.get(i));prefs.edit().putString(key,o.toString()).apply();refresh();Toast.makeText(this,"Catatan dihapus.",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Gagal menghapus catatan.",Toast.LENGTH_SHORT).show();}
    }

    private void confirmClear(String key,String label){new AlertDialog.Builder(this).setTitle("Hapus "+label+"?").setMessage("Semua catatan lokal pada bagian ini akan dihapus dari perangkat.").setNegativeButton("BATAL",null).setPositiveButton("HAPUS",(d,w)->{prefs.edit().remove(key).apply();refresh();Toast.makeText(this,"Riwayat dihapus.",Toast.LENGTH_SHORT).show();}).show();}

    private void saveHistoryItem(String key,String time,String text,int max){
        if(text==null||text.trim().isEmpty())return;
        try{JSONArray old=new JSONArray(prefs.getString(key,"[]")),out=new JSONArray();int start=Math.max(0,old.length()-max+1);for(int i=start;i<old.length();i++)out.put(old.get(i));JSONObject o=new JSONObject();o.put("time",time);o.put("text",text);out.put(o);prefs.edit().putString(key,out.toString()).apply();}catch(Exception ignored){}
    }

    private String summaryHistory(String key,String title){
        try{JSONArray a=new JSONArray(prefs.getString(key,"[]"));if(a.length()==0)return title+"\nBelum ada catatan. Tekan tombol RIWAYAT untuk melihat setelah data tersimpan.";int start=Math.max(0,a.length()-3);StringBuilder s=new StringBuilder(title+" • "+a.length()+" catatan\n");for(int i=a.length()-1;i>=start;i--){JSONObject o=a.optJSONObject(i);if(o==null)continue;String t=o.optString("text","").replace('\n',' ');if(t.length()>120)t=t.substring(0,120)+"…";s.append(o.optString("time","--")).append(" • ").append(t).append("\n");}return s.toString().trim();}catch(Exception e){return title+"\nTidak terbaca.";}
    }

    private String buildDataSnapshot(){
        StringBuilder s=new StringBuilder();s.append("PETANI: ").append(dash(s(farmerName))).append("\n");s.append("KOMODITAS: ").append(dash(s(crop))).append("\n");s.append("UMUR: ").append(ageDescription()).append("\n");s.append("LUAS: ").append(dash(s(area))).append(" ha\n");s.append("KOORDINAT: ").append(coordText()).append("\n");s.append("pH: ").append(dash(s(ph))).append("\nN: ").append(dash(s(n))).append(" mg/kg\nP: ").append(dash(s(p))).append(" mg/kg\nK: ").append(dash(s(k))).append(" mg/kg\nEC: ").append(dash(s(ec))).append(" uS/cm\nKelembapan: ").append(dash(s(moist))).append(" %\n");s.append("Cuaca terakhir: ").append(dash(prefs.getString("weather_last_report","")));return s.toString();
    }

    private void createPdfFile(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/pdf");i.putExtra(Intent.EXTRA_TITLE,"Laporan-Agronomi-"+LocalDate.now(WIB)+".pdf");startActivityForResult(i,CREATE_PDF_REQ);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==CREATE_PDF_REQ&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri u=data.getData();
            final String report=buildFullReport();
            net.execute(()->{try(OutputStream os=getContentResolver().openOutputStream(u)){if(os==null)throw new IOException("Output PDF tidak tersedia");writePdf(os,report);runOnUiThread(()->Toast.makeText(this,"PDF laporan berhasil disimpan.",Toast.LENGTH_LONG).show());}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Gagal membuat PDF: "+safe(e),Toast.LENGTH_LONG).show());}});
        }
    }

    private void printReport(){
        PrintManager pm=(PrintManager)getSystemService(Context.PRINT_SERVICE);if(pm==null){Toast.makeText(this,"Layanan cetak tidak tersedia.",Toast.LENGTH_LONG).show();return;}
        String title="Laporan Agronomi - "+s(crop);pm.print(title,new ReportPrintAdapter(buildFullReport()),new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build());
    }

    private String buildFullReport(){
        StringBuilder r=new StringBuilder();r.append("STASIUN CUACA DESA TURUS\nLAPORAN DATA LAHAN & AGRONOMI\n");r.append("Dibuat: ").append(now()).append("\n\n");r.append("PROFIL PETANI & LAHAN\n");r.append("Nama petani: ").append(dash(s(farmerName))).append("\n");r.append("Komoditas: ").append(dash(s(crop))).append("\n");r.append("Umur tanam: ").append(ageDescription()).append("\n");r.append("Luas lahan: ").append(dash(s(area))).append(" ha\n");r.append("Koordinat lahan: ").append(coordText()).append("\n\n");r.append("PARAMETER TANAH\n");r.append("pH: ").append(dash(s(ph))).append("\nN: ").append(dash(s(n))).append(" mg/kg\nP: ").append(dash(s(p))).append(" mg/kg\nK: ").append(dash(s(k))).append(" mg/kg\nEC: ").append(dash(s(ec))).append(" uS/cm\nKelembapan: ").append(dash(s(moist))).append(" %\n\n");r.append("TARGET HARA\nN: ").append(dash(s(targetN))).append(" kg/ha\nP2O5: ").append(dash(s(targetP))).append(" kg/ha\nK2O: ").append(dash(s(targetK))).append(" kg/ha\nDolomit: ").append(dash(s(dolomite))).append(" kg/ha\nPupuk kandang: ").append(dash(s(manure))).append(" kg/ha\nPOC: ").append(dash(s(poc))).append(" L/ha\n\n");r.append("HASIL ANALISIS & REKOMENDASI\n").append(lastReport.isEmpty()?"Belum dihitung.":lastReport).append("\n\n");r.append(analyzeOpt()).append("\n\n");r.append("REKOMENDASI AI TERAKHIR\n").append(lastAiAdvice.isEmpty()?"Belum ada analisis AI.":lastAiAdvice).append("\n\n");r.append("CUACA OPEN-METEO TERAKHIR\n").append(dash(prefs.getString("weather_last_report","Belum ada cache cuaca."))).append("\n\n");r.append("DATA THINGSPEAK TERBARU\n").append(buildThingSpeakContext()).append("\n\n");r.append("RIWAYAT PEMUPUKAN\n").append(historyForAi(KEY_FERT)).append("\n\nRIWAYAT OPT\n").append(historyForAi(KEY_OPT));return r.toString();
    }

    private void writePdf(OutputStream os,String report)throws Exception{
        PdfDocument doc=new PdfDocument();Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.BLACK);paint.setTextSize(10f);paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.NORMAL));
        int pageNo=1;PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,pageNo).create());android.graphics.Canvas c=page.getCanvas();float x=40,y=42;paint.setTextSize(16);paint.setTypeface(Typeface.DEFAULT_BOLD);c.drawText("STASIUN CUACA DESA TURUS",x,y,paint);paint.setTextSize(10);paint.setTypeface(Typeface.DEFAULT);y+=22;for(String line:wrapReport(report,520,paint,10f)){if(y>800){doc.finishPage(page);pageNo++;page=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,pageNo).create());c=page.getCanvas();paint.setTextSize(10);y=40;c.drawText("LAPORAN AGRONOMI • HALAMAN "+pageNo,x,y,paint);y+=20;}c.drawText(line,x,y,paint);y+=13;}doc.finishPage(page);doc.writeTo(os);doc.close();
    }

    private java.util.List<String> wrapReport(String text,float width,Paint p,float size){java.util.List<String> out=new java.util.ArrayList<>();p.setTextSize(size);for(String raw:text.replace("\r","").split("\\n",-1)){if(raw.isEmpty()){out.add("");continue;}String line="";for(String word:raw.split(" ")){String test=line.isEmpty()?word:line+" "+word;if(p.measureText(test)>width&&!line.isEmpty()){out.add(line);line=word;}else line=test;}out.add(line);}return out;}

    private final class ReportPrintAdapter extends PrintDocumentAdapter {
        private final String report;
        private PdfDocument doc;
        ReportPrintAdapter(String report){this.report=report;}
        @Override public void onLayout(android.print.PrintAttributes oldAttributes, android.print.PrintAttributes newAttributes, CancellationSignal cancellationSignal, LayoutResultCallback callback, android.os.Bundle extras){doc=new PdfDocument();PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(595,842,1).create();PdfDocument.Page page=doc.startPage(info);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(10);float y=40;for(String line:wrapReport(report,520,p,10)){if(y>800){doc.finishPage(page);page=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,doc.getPages().size()+1).create());y=40;}page.getCanvas().drawText(line,40,y,p);y+=13;}doc.finishPage(page);PrintDocumentInfo infoOut=new PrintDocumentInfo.Builder("laporan-agronomi.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(doc.getPages().size()).build();callback.onLayoutFinished(infoOut,true);}
        @Override public void onWrite(PageRange[] pages,ParcelFileDescriptor destination,CancellationSignal cancellationSignal,WriteResultCallback callback){try(FileOutputStreamHolder h=new FileOutputStreamHolder(destination)){doc.writeTo(h.os);callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});}catch(Exception e){callback.onWriteFailed(e.getMessage());}finally{try{doc.close();}catch(Exception ignored){}}}
    }

    private static final class FileOutputStreamHolder implements AutoCloseable {
        final OutputStream os;
        FileOutputStreamHolder(ParcelFileDescriptor pfd)throws Exception{os=new java.io.FileOutputStream(pfd.getFileDescriptor());}
        @Override public void close()throws Exception{os.close();}
    }

    private LinearLayout root(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(16),dp(4),dp(16),dp(8));return l;}
    private ScrollView wrap(View v){ScrollView s=new ScrollView(this);s.addView(v);return s;}
    private EditText edit(LinearLayout r,String hint,String val,boolean numeric){EditText e=new EditText(this);e.setHint(hint);e.setText(val);e.setSingleLine(false);e.setTextColor(Color.WHITE);e.setHintTextColor(Color.rgb(157,176,188));e.setBackgroundResource(R.drawable.bg_edit);e.setPadding(dp(12),dp(8),dp(12),dp(8));if(numeric)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);r.addView(e,new LinearLayout.LayoutParams(-1,dp(48)));return e;}
    private Spinner spinner(LinearLayout r,String title,String[] vals){TextView t=new TextView(this);t.setText(title);t.setTextColor(Color.rgb(41,198,199));t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setTextSize(10);r.addView(t);Spinner s=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,vals);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);r.addView(s,new LinearLayout.LayoutParams(-1,dp(48)));return s;}
    private void putNum(JSONObject o,String k,EditText e)throws Exception{double v=num(e);if(Double.isFinite(v))o.put(k,v);}
    private boolean validDate(String s){try{LocalDate.parse(s,DF);return true;}catch(Exception e){return false;}}
    private String s(EditText e){return e.getText().toString().trim();}
    private double num(EditText e){String t=s(e);if(t.isEmpty())return Double.NaN;try{return Double.parseDouble(t.replace(',','.'));}catch(Exception ex){return Double.NaN;}}
    private double z(EditText e){double v=num(e);return Double.isFinite(v)?Math.max(0,v):0;}
    private String f(double v,int d){if(!Double.isFinite(v))return "--";NumberFormat n=NumberFormat.getNumberInstance(new Locale("id","ID"));n.setGroupingUsed(false);n.setMinimumFractionDigits(d);n.setMaximumFractionDigits(d);return n.format(v);}
    private String dash(String x){return x==null||x.trim().isEmpty()?"--":x.trim();}
    private String coordText(){if(s(landLat).isEmpty()&&s(landLon).isEmpty())return "--";return dash(s(landLat))+", "+dash(s(landLon));}
    private String now(){return ZonedDateTime.now(WIB).format(TS);}
    private String safe(Throwable e){return e==null||e.getMessage()==null?"Kesalahan tidak diketahui":e.getMessage();}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private String readAll(InputStream in)throws Exception{if(in==null)return "";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String l;while((l=r.readLine())!=null)b.append(l);}return b.toString();}
}
