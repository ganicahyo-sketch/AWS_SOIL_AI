package id.turus.stasiuncuaca;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Comprehensive agronomy dashboard and evidence-assisted AI report. */
public class AgronomyActivity extends Activity {
    private static final String PREFS="thingspeak_config";
    private static final String DEFAULT_AI_KEY="";
    private final ExecutorService net=Executors.newSingleThreadExecutor();
    private android.content.SharedPreferences prefs;
    private TextView report, aiStatus;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        setContentView(R.layout.activity_agronomy);
        report=findViewById(R.id.agroReport); aiStatus=findViewById(R.id.agroAiStatus);
        findViewById(R.id.agroBack).setOnClickListener(v->finish());
        findViewById(R.id.agroFieldNotes).setOnClickListener(v->startActivity(new Intent(this,FieldNotesActivity.class)));
        findViewById(R.id.agroPdf).setOnClickListener(v->startActivity(new Intent(this,PdfReportActivity.class)));
        findViewById(R.id.agroAiButton).setOnClickListener(v->requestAi());
        findViewById(R.id.agroNotes).setOnClickListener(v->showNotes());
        render();
    }
    @Override protected void onDestroy(){net.shutdownNow();super.onDestroy();}

    private void render(){
        String crop=prefs.getString("crop","Tanaman pertanian");
        int hst=(int)Math.round(num(prefs.getString("farm_hst","-1")));
        AgronomyEngine.CropProfile cp=AgronomyEngine.profile(crop);
        String mode=prefs.getString("farm_cultivation_mode","Konvensional / PHT");
        String method=prefs.getString("soil_test_method","Metode tidak diketahui");
        double ph=num(prefs.getString("soil_ph","")),n=num(prefs.getString("soil_n","")),p=num(prefs.getString("soil_p","")),k=num(prefs.getString("soil_k",""));
        double nLow=num(prefs.getString("soil_n_low","")),nHigh=num(prefs.getString("soil_n_high",""));
        double ec=num(prefs.getString("soil_ec_us_cm","")),ece=num(prefs.getString("soil_ece_ds_m","")),moist=num(prefs.getString("soil_moisture_pct",""));
        double bd=num(prefs.getString("soil_bulk_density_g_cm3","1.30")),depth=num(prefs.getString("soil_depth_cm","20")),fc=num(prefs.getString("soil_fc_pct","")),pwp=num(prefs.getString("soil_pwp_pct",""));
        double phBuf=num(prefs.getString("soil_ph_buffer","")),al=num(prefs.getString("soil_al_dd","")),hd=num(prefs.getString("soil_h_dd","")),cec=num(prefs.getString("soil_cec","")),om=num(prefs.getString("soil_om_pct","")),lime=num(prefs.getString("soil_lime_requirement_kg_ha",""));
        double temp=num(prefs.getString("om_temp","")),rh=num(prefs.getString("om_rh","")),rain=num(prefs.getString("om_rain","")),et0=num(prefs.getString("om_et0","")),vpd=num(prefs.getString("om_vpd",""));
        double apparent=num(prefs.getString("om_apparent_temp","")),dew=num(prefs.getString("om_dewpoint","")),pressure=num(prefs.getString("om_pressure",""));
        double wind=num(prefs.getString("om_wind_speed","")),gust=num(prefs.getString("om_wind_gust","")),cloud=num(prefs.getString("om_cloud_cover",""));
        double uv=num(prefs.getString("om_uv","")),visibility=num(prefs.getString("om_visibility","")),shortwave=num(prefs.getString("om_radiation",""));
        double parEnergy=num(prefs.getString("om_par","")),ppfd=num(prefs.getString("om_ppfd","")),sunshine=num(prefs.getString("om_sun_hours",""));
        StringBuilder s=new StringBuilder();
        s.append("ANALISIS AGRONOMI TERPADU v1.5.8\n\n");
        s.append("KOMODITAS: ").append(crop).append("\nMODE: ").append(mode).append("\nHST: ").append(hst<0?"--":hst).append("\nFASE: ").append(AgronomyEngine.phase(hst,crop)).append("\nLUAS: ").append(pref("farm_area_ha","--")).append(" ha\nTARGET HASIL: ").append(pref("farm_target_yield_t_ha","--")).append(" t/ha\n\n");
        s.append("1. TANAH\n");
        s.append("pH ").append(show(ph)).append(" -> ").append(AgronomyEngine.classifyPH(ph,cp)).append("\n");
        s.append("Metode N/P/K: ").append(method).append("\n");
        s.append("N tersedia ").append(show(n)).append(" mg/kg -> ").append(AgronomyEngine.classifyN(n,nLow,nHigh)).append("\n");
        s.append("N-total diprediksi dari N tersedia: ").append(NitrogenInference.estimateTotalN(n)).append("\n");
        s.append("Status N-total estimasi titik tengah: ").append(NitrogenInference.classifyRange(n)).append("\n");
        s.append("P tersedia ").append(show(p)).append(" mg/kg -> ").append(AgronomyEngine.classifyP(p,method)).append("\n");
        s.append("K tersedia ").append(show(k)).append(" mg/kg -> ").append(AgronomyEngine.classifyK(k,method)).append("\n");
        s.append("EC sensor ").append(show(ec)).append(" µS/cm -> ").append(AgronomyEngine.classifyEC(ec,cp)).append("\n");
        s.append("URUTAN ANALISIS: tentukan rentang setiap parameter terlebih dahulu; rekomendasi diberikan setelah identifikasi faktor pembatas utama.\n");
        if(Double.isFinite(ece))s.append("ECe lab ").append(show(ece)).append(" dS/m -> ").append(AgronomyEngine.classifyECe(ece)).append("\n");
        else s.append("ECe lab: belum tersedia; EC sensor tidak dikonversi otomatis menjadi ECe.\n");
        s.append("Kelembapan tanah ").append(show(moist)).append(" % -> ").append(AgronomyEngine.classifyMoisture(moist)).append("\n");
        s.append("FC/PWP: ").append(show(fc)).append(" / ").append(show(pwp)).append(" % volume -> ").append(AgronomyEngine.soilWaterAssessment(moist,fc,pwp,depth,et0,crop)).append("\n");
        if(Double.isFinite(depth)&&Double.isFinite(bd)){
            s.append("Stok lapisan ").append(show(depth)).append(" cm; BD ").append(show(bd)).append(" g/cm³: N ").append(show(AgronomyEngine.soilStockKgHa(n,bd,depth))).append("; P ").append(show(AgronomyEngine.soilStockKgHa(p,bd,depth))).append("; K ").append(show(AgronomyEngine.soilStockKgHa(k,bd,depth))).append(" kg/ha.\n");
            s.append("Stok = konsentrasi × BD × kedalaman × 0,10 kg/ha; stok ≠ serapan langsung tanaman.\n");
        }
        s.append("\n2. ANALISIS RENTANG SEMUA PARAMETER\n");
        s.append(AgronomyEngine.comprehensiveRangeAnalysis(
                crop, ph, n, nLow, nHigh, p, k, ec, ece, moist, fc, pwp,
                num(prefs.getString("om_soil_moisture","")), num(prefs.getString("om_soil_temp","")), om, cec, bd, depth,
                temp, apparent, dew, rh, pressure, rain, et0, wind, gust, cloud, uv,
                visibility, num(prefs.getString("om_lux", prefs.getString("lux", ""))), shortwave, parEnergy, ppfd, sunshine, vpd, method));
        s.append("\n\n3. NPK & PREDIKSI KEBUTUHAN FASE\n");
        double target=num(pref("farm_target_yield_t_ha",""));
        double nn=AgronomyEngine.nutrientNeed(n,"N",crop,hst,fertCredit(crop)[0],target,method,depth,bd,nLow,nHigh);
        double pp=AgronomyEngine.nutrientNeed(p,"P",crop,hst,fertCredit(crop)[1],target,method,depth,bd);
        double kk=AgronomyEngine.nutrientNeed(k,"K",crop,hst,fertCredit(crop)[2],target,method,depth,bd);
        s.append("N skrining fase: ").append(show(nn)).append(" kg N/ha\nP2O5 skrining fase: ").append(show(pp)).append(" kg P2O5/ha\nK2O skrining fase: ").append(show(kk)).append(" kg K2O/ha\n");
        s.append("Rumus skrining: ").append(AgronomyEngine.nutrientFormulaText()).append("\n");
        s.append("PENTING: bukan QUEFTS/STCR penuh. Gunakan persamaan STCR/PUTS lokal bila tersedia.\n");
        s.append("\n4. KALKULASI PRODUK PUPUK YANG DIPILIH\n");
        s.append(calculateFertilizerPlan(prefs.getString("fertilizer_plan", "NPK 15-15-15|15|15|15"), nn, pp, kk,
                num(prefs.getString("farm_plant_density_ha", isPalm(crop) ? "143" : ""))));
        s.append("\nKebutuhan pupuk dihitung dari target skrining N, P₂O₅, K₂O dan komposisi produk yang dimasukkan; dosis yang menimbulkan kelebihan hara ditandai sebagai perkiraan dan perlu ditinjau agronom.\n");
        s.append("\n5. KEBUTUHAN DOLOMIT BILA pH MASAM\n");
        s.append(calculateDolomite(ph, lime, num(prefs.getString("farm_plant_density_ha", isPalm(crop) ? "143" : ""))));
        s.append("\n6. CUACA, VPD, AIR & EKOLOGI\n");
        s.append("Suhu ").append(show(temp)).append(" °C -> ").append(AgronomyEngine.temperatureStatus(temp,crop)).append("; kisaran profil ").append(show(cp.tempMin)).append("–").append(show(cp.tempMax)).append(" °C\n");
        s.append("RH ").append(show(rh)).append(" %; hujan ").append(show(rain)).append(" mm/hari; ET0 ").append(show(et0)).append(" mm/hari\n");
        s.append("VPD ").append(show(vpd)).append(" kPa -> ").append(AgronomyEngine.classifyVpd(vpd)).append("\n");
        s.append(AgronomyEngine.vpdCombinedStatus(vpd,moist,fc,pwp,depth,et0,crop)).append("\n");
        s.append("Angin ").append(pref("om_wind_direction","--")).append(" • ").append(pref("om_wind_speed","--")).append(" m/s • gust ").append(pref("om_wind_gust","--")).append(" m/s\n");
        s.append("Terasa ").append(pref("om_apparent_temp","--")).append(" °C • titik embun ").append(pref("om_dewpoint","--")).append(" °C • awan ").append(pref("om_cloud_cover","--")).append(" % • visibilitas ").append(pref("om_visibility","--")).append(" m • UV ").append(pref("om_uv","--")).append("\n");
        s.append("Radiasi ").append(pref("om_radiation","--")).append(" W/m² • sunshine ").append(pref("om_sun_hours","--")).append(" jam/hari\n");        s.append("PAR estimasi ").append(pref("om_par","--")).append(" W/m² • PPFD estimasi ").append(pref("om_ppfd","--")).append(" µmol/m²/s\n");
        s.append("Forecast 7 hari: ").append(pref("om_forecast_7d","belum ada")).append("\n");
        s.append("\n7. PREDIKSI POTENSI OPT — RISK SCREENING\n");
        s.append(AgronomyEngine.optRisk(crop,temp,rh,rain,num(pref("om_wind_speed","")),recent("opt_history",6),hst)).append("\n");
        s.append("\n8. REKOMENDASI TEKNIS\n");
        addRecommendations(s,crop,mode,ph,ec,ece,moist,fc,pwp,vpd,temp,rh,rain,et0,hst,method);
        String palmAdvice = PalmOilAdvisor.build(crop,hst,n,p,k,ec,ph,moist,rain,rh);
        if (!palmAdvice.isEmpty()) s.append("\n9. DATABASE & REKOMENDASI KHUSUS KELAPA SAWIT\n").append(palmAdvice).append("\n");
        s.append("\n10. CATATAN ILMIAH\n").append(AgronomyEngine.evidenceBrief()).append("\n");
        // Histori terpadu disimpan untuk laporan cetak/PDF, tetapi tidak ditampilkan pada layar analisis utama.
        String compact = compactReport(s.toString());
        report.setText(compact); prefs.edit().putString("last_agronomy_analysis",compact).apply();
    }

    private boolean isPalm(String crop) {
        String c = crop == null ? "" : crop.toLowerCase(Locale.US);
        return c.contains("sawit") || c.contains("oil palm");
    }

    private String calculateFertilizerPlan(String plan, double nNeed, double pNeed, double kNeed, double plantsPerHa) {
        StringBuilder out = new StringBuilder();
        if (!Double.isFinite(plantsPerHa) || plantsPerHa <= 0) {
            out.append("Isi populasi tanaman (pokok/ha) pada Catatan Lapangan untuk menghitung dosis per pokok.\n");
        }
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        java.util.ArrayList<double[]> comps = new java.util.ArrayList<>();
        if (plan != null) for (String row : plan.split("[;\\n]+")) {
            String[] v = row.trim().split("\\|");
            if (v.length < 4) continue;
            try {
                double a = Double.parseDouble(v[1].trim().replace(',', '.'));
                double b = Double.parseDouble(v[2].trim().replace(',', '.'));
                double c = Double.parseDouble(v[3].trim().replace(',', '.'));
                if (a < 0 || b < 0 || c < 0 || a > 100 || b > 100 || c > 100 || a+b+c <= 0) continue;
                names.add(v[0].trim()); comps.add(new double[]{a/100.0,b/100.0,c/100.0});
            } catch (Exception ignored) {}
        }
        if (comps.isEmpty()) return out.append("Belum ada komposisi valid. Format setiap baris: Nama|N%|P₂O₅%|K₂O%. Contoh NPK 15-15-15|15|15|15.\n").toString();
        double[] rem = {Math.max(0, finiteOrZero(nNeed)), Math.max(0, finiteOrZero(pNeed)), Math.max(0, finiteOrZero(kNeed))};
        double[] supplied = new double[3]; double[] dose = new double[comps.size()];
        for (int pass=0; pass<3; pass++) {
            int nutrient = -1; double largest = 0;
            for (int j=0;j<3;j++) if (rem[j] > largest) { largest=rem[j]; nutrient=j; }
            if (nutrient < 0 || largest < 0.01) break;
            int best=-1; double bestPct=0;
            for (int i=0;i<comps.size();i++) if (comps.get(i)[nutrient] > bestPct) { bestPct=comps.get(i)[nutrient]; best=i; }
            if (best < 0 || bestPct <= 0) break;
            double kgHa=rem[nutrient]/bestPct; dose[best]+=kgHa;
            for (int j=0;j<3;j++) { double add=kgHa*comps.get(best)[j]; supplied[j]+=add; rem[j]=Math.max(0,rem[j]-add); }
        }
        out.append("Target skrining: N ").append(show(nNeed)).append(" kg N/ha/tahun; P₂O₅ ").append(show(pNeed)).append(" kg/ha/tahun; K₂O ").append(show(kNeed)).append(" kg/ha/tahun.\n");
        boolean any=false;
        for (int i=0;i<dose.length;i++) if (dose[i] > 0.001) {
            any=true; out.append("• ").append(names.get(i)).append(" (N-P₂O₅-K₂O ").append(show(comps.get(i)[0]*100)).append("-").append(show(comps.get(i)[1]*100)).append("-").append(show(comps.get(i)[2]*100)).append("%): ").append(show(dose[i])).append(" kg/ha/tahun");
            if (Double.isFinite(plantsPerHa) && plantsPerHa > 0) out.append(" = ").append(show(dose[i]/plantsPerHa)).append(" kg/pokok/tahun");
            out.append("\n");
        }
        if (!any) out.append("Tidak dapat menghitung dosis: target hara atau komposisi pupuk belum memadai.\n");
        out.append("Estimasi hara dari campuran yang dihitung: N ").append(show(supplied[0])).append("; P₂O₅ ").append(show(supplied[1])).append("; K₂O ").append(show(supplied[2])).append(" kg/ha/tahun.\n");
        if (rem[0]+rem[1]+rem[2] > 1.0) out.append("Catatan: komposisi yang dimasukkan belum mencukupi semua target; tambahkan pupuk tunggal/produk lain.\n");
        out.append("Urutan input tidak mengubah kandungan yang tercatat; algoritme memilih produk dengan kadar tertinggi untuk hara yang paling belum terpenuhi. Angka merupakan estimasi model skrining, bukan rekomendasi laboratorium final.\n");
        return out.toString();
    }

    private String calculateDolomite(double ph, double labLimeKgHa, double plantsPerHa) {
        if (!Double.isFinite(ph)) return "pH belum tersedia; kebutuhan dolomit belum bisa dihitung.\n";
        if (ph >= 5.5) return "pH "+show(ph)+"; tidak diklasifikasikan masam oleh skrining ini (ambang <5,5). Jangan menambahkan dolomit tanpa kebutuhan yang terverifikasi.\n";
        double limeKgHa;
        String basis;
        if (Double.isFinite(labLimeKgHa) && labLimeKgHa > 0) { limeKgHa=labLimeKgHa/0.90; basis="kebutuhan kapur laboratorium dikoreksi efisiensi dolomit asumsi 90%"; }
        else { limeKgHa = ph < 4.5 ? 2000 : ph < 5.0 ? 1500 : 1000; basis="estimasi awal berbasis kelas pH saja"; }
        double perPalm=Double.isFinite(plantsPerHa)&&plantsPerHa>0 ? limeKgHa/plantsPerHa : Double.NaN;
        StringBuilder b = new StringBuilder();
        b.append("pH ").append(show(ph)).append(" (masam). Kebutuhan dolomit skrining: ").append(show(limeKgHa)).append(" kg/ha/tahun (asumsi 1 aplikasi/tahun; ").append(basis).append(").\n");
        if (Double.isFinite(perPalm)) b.append("• ").append(show(perPalm)).append(" kg/pokok/pemupukan; ").append(show(perPalm)).append(" kg/pokok/tahun pada 1 aplikasi/tahun.\n");
        else b.append("Isi populasi tanaman (pokok/ha) untuk mendapatkan kg/pokok/pemupukan dan kg/pokok/tahun.\n");
        if (!Double.isFinite(labLimeKgHa) || labLimeKgHa <= 0) b.append("PENTING: ini hanya perkiraan kasar untuk perencanaan; pH tunggal tidak cukup menentukan kebutuhan kapur. Verifikasi dengan pH-buffer/Al-dd/CEC atau rekomendasi laboratorium, serta cek nilai netralisasi dan Mg produk dolomit.\n");
        return b.toString();
    }

    private double finiteOrZero(double v) { return Double.isFinite(v) ? v : 0.0; }

    private String compactReport(String text){
        String[] lines=text.split("\\n"); StringBuilder out=new StringBuilder(); boolean skip=false;
        for(String line:lines){String t=line.trim();
            if(t.startsWith("10. CATATAN ILMIAH")){skip=true;continue;}
            if(skip){
                if(t.startsWith("8. HISTORI TERPADU")) skip=false;
                else continue;
            }
            if(t.startsWith("URUTAN ANALISIS:" ) || t.startsWith("Rumus skrining:" ) || t.startsWith("PENTING:" ) || t.startsWith("Stok = konsentrasi")) continue;
            if(t.startsWith("SELESAI ANALISIS AWAL")) continue;
            if(t.startsWith("Catatan: parameter yang tidak memiliki ambang universal")) continue;
            if(t.contains("tidak dikonversi otomatis") && t.startsWith("ECe lab:")) continue;
            out.append(line).append("\n");
        }
        return out.toString().trim();
    }

    private void showNotes(){
        ScrollView root=new ScrollView(this); int pad=(int)(16*getResources().getDisplayMetrics().density); root.setPadding(pad,pad,pad,pad);
        LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        TextView tv=new TextView(this); tv.setTextColor(getResources().getColor(R.color.text_main)); tv.setTextSize(14);
        tv.setText("NOTE — INFORMASI ILMIAH, METODE & SUMBER\n\n"+
                "ALUR ANALISIS\n"+
                "1) Baca semua data terukur dari ThingSpeak/Open-Meteo/manual.\n"+
                "2) Klasifikasikan tiap parameter sesuai rentang/ambang yang relevan.\n"+
                "3) Hubungkan parameter dan tentukan faktor pembatas utama.\n"+
                "4) Baru susun rekomendasi teknis berdasarkan bukti dan fase tanaman.\n\n"+
                "STOK HARA TANAH\n"+
                "Stok unsur pada lapisan tanah dihitung sebagai pendekatan massa tanah: konsentrasi (mg/kg) × bulk density (g/cm³) × kedalaman (cm) × 0,10 = kg/ha. Nilai ini adalah stok teoritis pada lapisan, bukan serapan atau ketersediaan langsung tanaman.\n\n"+
                "KEBUTUHAN HARA / SKRINING\n"+
                "Kebutuhan fase memakai kebutuhan musim komoditas × fraksi fase × skala target hasil × faktor dukungan soil-test, kemudian dikoreksi efisiensi pemulihan dan kredit pupuk. Ini model skrining/STCR-style, bukan QUEFTS atau STCR yang terkalibrasi lokal. Gunakan PUTS/STCR lokal bila tersedia.\n\n"+
                "pH & KAPUR\n"+
                "pH rendah tidak boleh langsung diubah menjadi dosis dolomit hanya dari pH. Kebutuhan pengapuran lebih kuat bila tersedia pH-buffer, Al-dd/H-dd, CEC/KTK, bahan organik, atau kebutuhan kapur laboratorium.\n\n"+
                "EC vs ECe\n"+
                "EC sensor adalah parameter lapangan dalam µS/cm; hubungan satuan: µS/cm ÷ 1000 = dS/m. ECe laboratorium tidak boleh dipaksakan dari satu faktor konversi universal karena bergantung metode, ekstraksi, kadar air, suhu, tekstur dan kondisi tanah.\n\n"+
                "AIR TANAH\n"+
                "Open-Meteo soil moisture dinyatakan sebagai m³/m³ dan untuk tampilan persentase volume dikalikan 100. FC/PWP digunakan bila tersedia untuk menilai FTSW/air tersedia.\n\n"+
                "VPD & ET₀\n"+
                "VPD dibaca bersama suhu/RH dan kondisi air tanah. ET₀ Open-Meteo menggunakan referensi FAO-56 Penman-Monteith pada produk yang tersedia. ET₀ adalah kebutuhan referensi, bukan kebutuhan irigasi komoditas secara langsung.\n\n"+
                "PAR / PPFD\n"+
                "PAR estimasi = 0,45 × shortwave; PPFD estimasi = PAR × 4,57 µmol/J. Ini estimasi broadband, bukan pengukuran quantum sensor.\n\n"+
                "OPT\n"+
                "Daftar OPT di mesin lokal hanya screening. AI dapat mencari OPT tambahan berdasarkan komoditas, fase, lokasi, gejala, riwayat, dan cuaca; hasil tetap merupakan risk screening, bukan diagnosis pasti.\n\n"+
                "MODE ORGANIK\n"+
                "Rekomendasi harus menggunakan input/proses yang diizinkan sistem organik yang berlaku dan tidak boleh menganggap semua input kimia setara dengan input organik.\n\n"+
                "SUMBER UTAMA\n"+
                "• Open-Meteo Historical Weather API — data historis reanalysis, variabel cuaca, tanah, ET₀, VPD dan radiasi.\n"+
                "• OpenAlex — pencarian literatur ilmiah.\n"+
                "• AgronomyEngine / NitrogenInference — model skrining lokal aplikasi.\n"+
                "• Referensi EC/ECe: Pedosphere 32(6), 2022, DOI 10.1016/j.pedsph.2022.06.023; Journal of the Saudi Society of Agricultural Sciences 23(4), 2024, DOI 10.1016/j.jssas.2023.12.005.\n"+
                "• Rujukan PPFD: Thimijan & Heins (1983), HortScience 18(6):818–822, DOI 10.21273/HORTSCI.18.6.818.");
        content.addView(tv,new LinearLayout.LayoutParams(-1,-2)); root.addView(content,new ScrollView.LayoutParams(-1,-2)); new AlertDialog.Builder(this).setTitle("ⓘ INFORMASI ILMIAH").setView(root).setPositiveButton("TUTUP",null).show();
    }

    private void addRecommendations(StringBuilder s,String crop,String mode,double ph,double ec,double ece,double moist,double fc,double pwp,double vpd,double t,double rh,double rain,double et0,int hst,String method){
        AgronomyEngine.CropProfile p=AgronomyEngine.profile(crop);
        if(Double.isFinite(ph)&&ph<p.phMin) s.append("• pH rendah: lakukan uji pH-buffer/Al-dd/H-dd/CEC atau gunakan kebutuhan kapur laboratorium; jangan hitung dolomit dari pH saja.\n");
        if(Double.isFinite(ph)&&ph>p.phMax) s.append("• pH tinggi: stop sementara kapur/dolomit; periksa alkalinitas air dan dasar kebutuhan pengasaman.\n");
        if(Double.isFinite(ec)&&ec/1000.0>p.ecThresholdDsM) s.append("• EC sensor tinggi: kurangi pemupukan pekat, cek air/drainase, dan pantau tren. Konfirmasi dengan ECe bila salinitas dicurigai.\n");
        if(Double.isFinite(ece)&&ece>=4) s.append("• ECe menunjukkan salinitas sedikit sampai sangat tinggi menurut kelas USDA-NRCS; pilih tindakan berdasarkan toleransi komoditas dan pemeriksaan zona akar.\n");
        String water=AgronomyEngine.soilWaterAssessment(moist,fc,pwp,20,et0,crop);
        if(water.startsWith("KURANG")) s.append("• Air tanah kurang: cek zona akar dan kebutuhan irigasi sebelum menambah pupuk larut.\n");
        if(Double.isFinite(vpd)&&vpd>2) s.append("• VPD tinggi: pantau layu/kerontokan bunga atau gejala kehilangan air; kombinasi dengan tanah kering lebih serius.\n");
        if(Double.isFinite(t)&& (t<p.tempMin||t>p.tempMax)) s.append("• Suhu di luar kisaran ekologis profil: kurangi pekerjaan stres pada tanaman dan pantau gejala.\n");
        if(Double.isFinite(rh)&&rh>90&&Double.isFinite(rain)&&rain>=5) s.append("• RH + hujan tinggi: tingkatkan scouting penyakit, sanitasi dan sirkulasi udara.\n");
        if(mode.toLowerCase(Locale.US).contains("organik")) s.append("• ORGANIK: gunakan input/proses yang diizinkan standar organik yang berlaku; prioritaskan bahan organik matang, sanitasi, varietas toleran, mekanis dan agen hayati. Verifikasi status input sebelum aplikasi.\n");
        else s.append("• KONVENSIONAL/PHT: gunakan pemupukan berimbang, monitoring ambang tindakan, rotasi bahan aktif dan patuhi label/interval pra-panen.\n");
        s.append("• Fase: ").append(AgronomyEngine.phase(hst,crop)).append(". Prioritaskan tindakan berdasarkan fase dan data lapang, bukan umur saja.\n");
    }

    private double[] fertCredit(String crop){double n=0,p=0,k=0;try{JSONArray a=new JSONArray(prefs.getString("fert_history","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null||!same(o.optString("crop",""),crop)||!"kg/ha".equalsIgnoreCase(o.optString("unit","kg/ha")))continue;double d=o.optDouble("dose",0);n+=d*o.optDouble("nPct",0)/100;p+=d*o.optDouble("pPct",0)/100;k+=d*o.optDouble("kPct",0)/100;}}catch(Exception ignored){}return new double[]{n,p,k};}
    private boolean same(String a,String b){return AgronomyEngine.normalizeCrop(a).equalsIgnoreCase(AgronomyEngine.normalizeCrop(b));}
    private String recent(String key,int max){try{JSONArray a=new JSONArray(prefs.getString(key,"[]"));StringBuilder s=new StringBuilder();for(int i=Math.max(0,a.length()-max);i<a.length();i++)s.append(a.optJSONObject(i)).append("\n");return s.toString().trim().isEmpty()?"(tidak ada)":s.toString();}catch(Exception e){return "(tidak ada)";}}
    private void requestAi(){
        final String provider=prefs.getString("ai_provider","OpenAI");
        final String key="Google Gemini".equals(provider)?prefs.getString("gemini_api_key","").trim():("OpenRouter".equals(provider)?prefs.getString("openrouter_api_key","").trim():prefs.getString("ai_api_key",DEFAULT_AI_KEY).trim());
        if(key.isEmpty()){aiStatus.setText("Masukkan API key "+provider+" di Pengaturan.");return;}
        final TextView button=findViewById(R.id.agroAiButton); button.setEnabled(false); aiStatus.setText("Menganalisis dengan "+provider+", mencari OPT dan menyusun rekomendasi AI...");
        final String model="Google Gemini".equals(provider)?prefs.getString("gemini_model","gemini-2.5-flash").trim():("OpenRouter".equals(provider)?prefs.getString("openrouter_model","openai/gpt-oss-20b:free").trim():prefs.getString("ai_model","gpt-6-luna").trim());
        final double aiLat=prefs.getFloat("latitude",Float.NaN);
        final double aiLon=prefs.getFloat("longitude",Float.NaN);
        final String context="LOKASI GPS: "+(Double.isFinite(aiLat)?String.format(Locale.US,"%.6f",aiLat):"--")+", "+(Double.isFinite(aiLon)?String.format(Locale.US,"%.6f",aiLon):"--")+"\n"+report.getText().toString();
        net.execute(()->{try{String lit=fetchOpenAlex(prefs.getString("crop","Tanaman pertanian"), context);String ans=callAi(provider,key,model,context,lit);prefs.edit().putString("last_ai_advice",ans).putLong("last_ai_advice_epoch",System.currentTimeMillis()).apply();runOnUiThread(()->{aiStatus.setText(ans);button.setEnabled(true);});}catch(Exception e){runOnUiThread(()->{aiStatus.setText("AI gagal: "+msg(e));button.setEnabled(true);});}});
    }
    private String fetchOpenAlex(String crop,String context)throws Exception{
        String q=""+crop+" agriculture soil fertility pest disease weather forecasting";
        String url="https://api.openalex.org/works?search="+URLEncoder.encode(q,"UTF-8")+"&filter=from_publication_date:2018-01-01&per-page=6&sort=relevance_score:desc";
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setRequestMethod("GET");if(c.getResponseCode()!=200)throw new Exception("OpenAlex HTTP "+c.getResponseCode());JSONObject r=new JSONObject(readAll(c.getInputStream()));JSONArray a=r.optJSONArray("results");StringBuilder out=new StringBuilder();if(a!=null)for(int i=0;i<a.length();i++){JSONObject w=a.optJSONObject(i);if(w==null)continue;out.append(i+1).append(") ").append(w.optString("display_name",""));out.append(" | year=").append(w.optInt("publication_year",0));String doi=w.optString("doi","");if(!doi.isEmpty())out.append(" | ").append(doi);out.append("\n");}return out.length()==0?"Tidak ada hasil OpenAlex yang cocok.":out.toString();
    }
    private String callAi(String provider,String key,String model,String ctx,String lit)throws Exception{
        if ("Google Gemini".equals(provider)) return callGemini(key,model,ctx,lit);
        if ("OpenRouter".equals(provider)) return callOpenRouter(key,model,ctx,lit);
        return callOpenAi(key,model,ctx,lit);
    }

    private String promptText(String ctx,String lit){
        return "DATA DAN ANALISIS AWAL:\n"+ctx+
                "\n\nHASIL PUSTAKA OPENALEX:\n"+lit+
                "\n\nGunakan sumber web bila provider mendukung pencarian web. Untuk kelapa sawit, prioritaskan panduan resmi PPKS/IOPRI. Untuk OPT, lakukan risk screening berdasarkan komoditas, fase, lokasi, gejala dan cuaca; jangan mengarang diagnosis atau dosis pestisida. Pisahkan fakta terukur, hasil screening, dan rekomendasi.";
    }

    private String systemInstruction(){
        return "Anda adalah agronom pendamping petani Indonesia. Ubah data menjadi keputusan dan tindakan. WAJIB keluarkan: 1) RINGKASAN KONDISI, 2) FAKTOR PEMBATAS UTAMA, 3) REKOMENDASI 0-24 JAM, 4) REKOMENDASI 1-7 HARI, 5) REKOMENDASI PEMUPUKAN, 6) REKOMENDASI AIR, 7) PREDIKSI POTENSI OPT, 8) TINDAKAN PHT/OPT, 9) PRIORITAS TINDAKAN, 10) DATA YANG MASIH PERLU DIUKUR. Gunakan bahasa Indonesia yang mudah dipahami petani tetapi tetap ilmiah. Jangan mengarang data. Untuk sawit, dosis PPKS/IOPRI harus dianggap resmi hanya jika sumbernya terverifikasi; sensor USB 8-in-1/7-in-1 adalah skrining, bukan pengganti analisis laboratorium. Jangan mengubah mg/kg sensor menjadi kg pupuk/pokok secara langsung. EC sensor tidak otomatis sama dengan ECe. Untuk pH, jangan menghitung kapur/dolomit dari pH tunggal. Jika data kurang, tetap beri gambaran dengan asumsi yang ditandai jelas.";
    }

    private String callOpenAi(String key,String model,String ctx,String lit)throws Exception{
        JSONObject p=new JSONObject(); p.put("model",model); p.put("store",false); p.put("max_output_tokens",5000);
        JSONArray tools=new JSONArray(); JSONObject webSearch=new JSONObject(); webSearch.put("type","web_search"); tools.put(webSearch); p.put("tools",tools); p.put("tool_choice","required");
        p.put("instructions",systemInstruction()); p.put("input",promptText(ctx,lit));
        HttpURLConnection c=(HttpURLConnection)new URL("https://api.openai.com/v1/responses").openConnection();
        c.setRequestMethod("POST"); c.setConnectTimeout(12000); c.setReadTimeout(60000); c.setDoOutput(true); c.setRequestProperty("Authorization","Bearer "+key); c.setRequestProperty("Content-Type","application/json; charset=UTF-8");
        byte[] b=p.toString().getBytes(StandardCharsets.UTF_8); c.setFixedLengthStreamingMode(b.length); try(OutputStream o=c.getOutputStream()){o.write(b);} int code=c.getResponseCode();
        if(code<200||code>=300) throw httpError("OpenAI",code,c);
        JSONObject r=new JSONObject(readAll(c.getInputStream())); String t=r.optString("output_text","").trim(); if(!t.isEmpty())return t;
        JSONArray out=r.optJSONArray("output"); if(out!=null){StringBuilder z=new StringBuilder(); for(int i=0;i<out.length();i++){JSONObject it=out.optJSONObject(i); if(it==null)continue; JSONArray cc=it.optJSONArray("content"); if(cc==null)continue; for(int j=0;j<cc.length();j++){JSONObject part=cc.optJSONObject(j); if(part!=null&&"output_text".equals(part.optString("type")))z.append(part.optString("text","")).append("\n");}} if(z.length()>0)return z.toString().trim();}
        throw new Exception("Respons OpenAI tidak berisi teks.");
    }

    private String callGemini(String key,String model,String ctx,String lit)throws Exception{
        JSONObject root=new JSONObject(); JSONArray contents=new JSONArray(); JSONObject content=new JSONObject(); content.put("role","user"); JSONArray parts=new JSONArray(); JSONObject part=new JSONObject(); part.put("text",systemInstruction()+"\n\n"+promptText(ctx,lit)); parts.put(part); content.put("parts",parts); contents.put(content); root.put("contents",contents);
        JSONObject gen=new JSONObject(); gen.put("temperature",0.2); gen.put("maxOutputTokens",5000); root.put("generationConfig",gen);
        String m=model.trim().isEmpty()?"gemini-2.5-flash":model.trim(); String url="https://generativelanguage.googleapis.com/v1beta/models/"+URLEncoder.encode(m,"UTF-8")+":generateContent?key="+URLEncoder.encode(key,"UTF-8");
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setRequestMethod("POST"); c.setConnectTimeout(12000); c.setReadTimeout(60000); c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json; charset=UTF-8");
        byte[] b=root.toString().getBytes(StandardCharsets.UTF_8); c.setFixedLengthStreamingMode(b.length); try(OutputStream o=c.getOutputStream()){o.write(b);} int code=c.getResponseCode(); if(code<200||code>=300)throw httpError("Gemini",code,c);
        JSONObject r=new JSONObject(readAll(c.getInputStream())); JSONArray candidates=r.optJSONArray("candidates"); if(candidates!=null&&candidates.length()>0){JSONObject co=candidates.optJSONObject(0); JSONObject cc=co==null?null:co.optJSONObject("content"); JSONArray ps=cc==null?null:cc.optJSONArray("parts"); if(ps!=null){StringBuilder z=new StringBuilder(); for(int i=0;i<ps.length();i++){JSONObject q=ps.optJSONObject(i); if(q!=null)z.append(q.optString("text","")).append("\n");} if(z.length()>0)return z.toString().trim();}}
        throw new Exception("Respons Gemini tidak berisi teks.");
    }

    private String callOpenRouter(String key,String model,String ctx,String lit)throws Exception{
        JSONObject root=new JSONObject(); root.put("model",model.trim().isEmpty()?"openai/gpt-oss-20b:free":model.trim()); root.put("temperature",0.2); root.put("max_tokens",5000);
        JSONArray messages=new JSONArray(); JSONObject sys=new JSONObject(); sys.put("role","system"); sys.put("content",systemInstruction()); messages.put(sys); JSONObject user=new JSONObject(); user.put("role","user"); user.put("content",promptText(ctx,lit)); messages.put(user); root.put("messages",messages);
        HttpURLConnection c=(HttpURLConnection)new URL("https://openrouter.ai/api/v1/chat/completions").openConnection(); c.setRequestMethod("POST"); c.setConnectTimeout(12000); c.setReadTimeout(60000); c.setDoOutput(true); c.setRequestProperty("Authorization","Bearer "+key); c.setRequestProperty("Content-Type","application/json; charset=UTF-8"); c.setRequestProperty("HTTP-Referer","https://github.com/ganicahyo-sketch/AWS_SOIL_AI"); c.setRequestProperty("X-Title","SIM-TANI v1.5.8");
        byte[] b=root.toString().getBytes(StandardCharsets.UTF_8); c.setFixedLengthStreamingMode(b.length); try(OutputStream o=c.getOutputStream()){o.write(b);} int code=c.getResponseCode(); if(code<200||code>=300)throw httpError("OpenRouter",code,c);
        JSONObject r=new JSONObject(readAll(c.getInputStream())); JSONArray choices=r.optJSONArray("choices"); if(choices!=null&&choices.length()>0){JSONObject ch=choices.optJSONObject(0); JSONObject msg=ch==null?null:ch.optJSONObject("message"); String t=msg==null?"":msg.optString("content","").trim(); if(!t.isEmpty())return t;}
        throw new Exception("Respons OpenRouter tidak berisi teks.");
    }

    private Exception httpError(String name,int code,HttpURLConnection c)throws Exception{
        String er=readAll(c.getErrorStream()); return new Exception(name+" HTTP "+code+(er.isEmpty()?"":" — "+er.substring(0,Math.min(600,er.length()))));
    }
    private String pref(String k,String d){return prefs.getString(k,d);}
    private double num(String s){try{return s==null||s.trim().isEmpty()?Double.NaN:Double.parseDouble(s.trim().replace(',','.'));}catch(Exception e){return Double.NaN;}}
    private String show(double v){return Double.isFinite(v)?String.format(Locale.US,"%.2f",v):"--";}
    private String readAll(InputStream in)throws Exception{if(in==null)return "";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String l;while((l=r.readLine())!=null)b.append(l);}return b.toString();}
    private String msg(Exception e){return e.getMessage()==null?"kesalahan tidak diketahui":e.getMessage();}
}
