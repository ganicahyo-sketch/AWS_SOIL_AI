package id.turus.stasiuncuaca;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.View;
import android.view.Gravity;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
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
import java.util.ArrayList;
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
    private LinearLayout reportCards;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        setContentView(R.layout.activity_agronomy);
        report=findViewById(R.id.agroReport); aiStatus=findViewById(R.id.agroAiStatus);
        reportCards=findViewById(R.id.agroReportCards);
        if (report != null) { report.setVisibility(View.GONE); report.setTextSize(16); report.setLineSpacing(dp(5), 1.08f); }
        if (aiStatus != null) { aiStatus.setTextSize(16); aiStatus.setLineSpacing(dp(5), 1.08f); }
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
        s.append("Kelas N-total yang mungkin: ").append(NitrogenInference.classifyRange(n)).append("\n");
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
            s.append("Stok = konsentrasi × BD × kedalaman × 0,01; stok ≠ serapan langsung tanaman.\n");
        }
        s.append("\n2. ANALISIS RENTANG SEMUA PARAMETER\n");
        s.append(AgronomyEngine.comprehensiveRangeAnalysis(
                crop, ph, n, nLow, nHigh, p, k, ec, ece, moist, fc, pwp,
                num(prefs.getString("om_soil_moisture","")), num(prefs.getString("om_soil_temp","")), om, cec, bd, depth,
                temp, apparent, dew, rh, pressure, rain, et0, wind, gust, cloud, uv,
                visibility, num(prefs.getString("lux","")), shortwave, parEnergy, ppfd, sunshine, vpd, method));
        s.append("\n\n3. NPK & PREDIKSI KEBUTUHAN FASE\n");
        double target=num(pref("farm_target_yield_t_ha",""));
        double nn=AgronomyEngine.nutrientNeed(n,"N",crop,hst,fertCredit(crop)[0],target,method,depth,bd);
        double pp=AgronomyEngine.nutrientNeed(p,"P",crop,hst,fertCredit(crop)[1],target,method,depth,bd);
        double kk=AgronomyEngine.nutrientNeed(k,"K",crop,hst,fertCredit(crop)[2],target,method,depth,bd);
        s.append("N skrining fase: ").append(show(nn)).append(" kg N/ha\nP2O5 skrining fase: ").append(show(pp)).append(" kg P2O5/ha\nK2O skrining fase: ").append(show(kk)).append(" kg K2O/ha\n");
        s.append("Rumus skrining: ").append(AgronomyEngine.nutrientFormulaText()).append("\n");
        s.append("PENTING: bukan QUEFTS/STCR penuh. Gunakan persamaan STCR/PUTS lokal bila tersedia.\n");
        s.append("\n4. CUACA, VPD, AIR & EKOLOGI\n");
        s.append("Suhu ").append(show(temp)).append(" °C -> ").append(AgronomyEngine.temperatureStatus(temp,crop)).append("; kisaran profil ").append(show(cp.tempMin)).append("–").append(show(cp.tempMax)).append(" °C\n");
        s.append("RH ").append(show(rh)).append(" %; hujan ").append(show(rain)).append(" mm/hari; ET0 ").append(show(et0)).append(" mm/hari\n");
        s.append("VPD ").append(show(vpd)).append(" kPa -> ").append(AgronomyEngine.classifyVpd(vpd)).append("\n");
        s.append(AgronomyEngine.vpdCombinedStatus(vpd,moist,fc,pwp,depth,et0,crop)).append("\n");
        s.append("Angin ").append(pref("om_wind_direction","--")).append(" • ").append(pref("om_wind_speed","--")).append(" m/s • gust ").append(pref("om_wind_gust","--")).append(" m/s\n");
        s.append("Terasa ").append(pref("om_apparent_temp","--")).append(" °C • titik embun ").append(pref("om_dewpoint","--")).append(" °C • awan ").append(pref("om_cloud_cover","--")).append(" % • visibilitas ").append(pref("om_visibility","--")).append(" m • UV ").append(pref("om_uv","--")).append("\n");
        s.append("Radiasi ").append(pref("om_radiation","--")).append(" W/m² • sunshine ").append(pref("om_sun_hours","--")).append(" jam/hari\n");        s.append("PAR estimasi ").append(pref("om_par","--")).append(" W/m² • PPFD estimasi ").append(pref("om_ppfd","--")).append(" µmol/m²/s\n");
        s.append("Forecast 7 hari: ").append(pref("om_forecast_7d","belum ada")).append("\n");
        s.append("\n5. PREDIKSI POTENSI OPT — RISK SCREENING\n");
        s.append(AgronomyEngine.optRisk(crop,temp,rh,rain,num(pref("om_wind_speed","")),recent("opt_history",6),hst)).append("\n");
        s.append("\n6. REKOMENDASI TEKNIS\n");
        addRecommendations(s,crop,mode,ph,ec,ece,moist,fc,pwp,vpd,temp,rh,rain,et0,hst,method);
        String palmAdvice = PalmOilAdvisor.build(crop,hst,n,p,k,ec,ph,moist,rain,rh);
        if (!palmAdvice.isEmpty()) {
            s.append("\n7. DATA KEBUN & LAB KHUSUS KELAPA SAWIT v1.5.8\n").append(palmAdvice).append("\n");
            appendPalmFarmAndLab(s, crop);
            s.append("\n").append(PalmOilDoseModel.build(
                    num(pref("farm_actual_yield_t_ha", "")), num(pref("farm_target_yield_t_ha", "")),
                    num(pref("farm_palms_per_ha", "")), num(pref("palm_leaf_n_pct", "")),
                    num(pref("palm_leaf_p_pct", "")), num(pref("palm_leaf_k_pct", "")),
                    num(pref("palm_leaf_mg_pct", "")), n, p, k,
                    num(pref("palm_lab_soil_p_mg_kg", "")), num(pref("palm_lab_soil_k_cmol_kg", "")), hst,
                    num(pref("farm_area_ha", "")), prefs.getString("fert_history", "[]"), crop,
                    num(pref("soil_bulk_density_g_cm3", "1.30")))).append("\n");
        }
        s.append("\n8. CATATAN ILMIAH\n").append(AgronomyEngine.evidenceBrief()).append("\n");
        // Histori terpadu disimpan untuk laporan cetak/PDF, tetapi tidak ditampilkan pada layar analisis utama.
        String compact = compactReport(s.toString());
        if (PalmOilAdvisor.isOilPalm(crop)) compact = buildPalmExecutiveSummary(crop, hst) + "\n\n────────────────────────\nDETAIL ANALISIS\n\n" + compact;
        report.setText(compact); renderAgronomyReportCards(compact); prefs.edit().putString("last_agronomy_analysis",compact).apply();
    }

    /** Short first-screen summary; the full analysis remains available below it and in reports. */
    private String buildPalmExecutiveSummary(String crop, int hst) {
        double area = num(pref("farm_area_ha", ""));
        double density = num(pref("farm_palms_per_ha", ""));
        double actual = num(pref("farm_actual_yield_t_ha", ""));
        double target = num(pref("farm_target_yield_t_ha", ""));
        String phase = AgronomyEngine.phase(hst, crop);
        String leafN = pref("palm_leaf_n_pct", "");
        String leafP = pref("palm_leaf_p_pct", "");
        String leafK = pref("palm_leaf_k_pct", "");
        String soilN = pref("palm_lab_soil_n_pct", "");
        String soilP = pref("palm_lab_soil_p_mg_kg", "");
        String soilK = pref("palm_lab_soil_k_cmol_kg", "");
        StringBuilder out = new StringBuilder();
        out.append("RINGKASAN AWAL • KELAPA SAWIT\n");
        out.append("Fase: ").append(phase).append(" • Umur: ").append(hst < 0 ? "belum diisi" : hst + " HST").append("\n");
        out.append("Luas: ").append(show(area)).append(" ha • Pokok: ").append(show(density)).append(" pokok/ha");
        if (Double.isFinite(area) && area > 0 && Double.isFinite(density) && density > 0) out.append(" • Total ").append(show(area * density)).append(" pokok");
        out.append("\nHasil TBS: ").append(show(actual)).append(" ton/ha/tahun • Target: ").append(show(target)).append(" ton/ha/tahun\n");
        out.append("Data daun N/P/K: ").append(has(leafN) && has(leafP) && has(leafK) ? "tersedia" : "belum lengkap");
        out.append(" • Data tanah lab N/P/K: ").append(has(soilN) && has(soilP) && has(soilK) ? "tersedia" : "belum lengkap").append("\n");
        out.append("Fokus awal: cek data daun + tanah, populasi dan hasil aktual sebelum menetapkan dosis pupuk.\n");
        out.append("Catatan: angka ekspor hara panen di bawah adalah estimasi kehilangan melalui TBS, bukan dosis pupuk langsung.");
        return out.toString();
    }

    private boolean has(String value) { return value != null && !value.trim().isEmpty() && !"--".equals(value.trim()); }

    private void appendPalmFarmAndLab(StringBuilder s, String crop) {
        double area=num(pref("farm_area_ha",""));
        double density=num(pref("farm_palms_per_ha",""));
        double actual=num(pref("farm_actual_yield_t_ha",""));
        double target=num(pref("farm_target_yield_t_ha",""));
        double yieldTons=Double.isFinite(actual)&&actual>0?actual:target;
        s.append("POPULASI: ").append(show(density)).append(" pokok/ha; luas ").append(show(area)).append(" ha");
        if(Double.isFinite(area)&&area>0&&Double.isFinite(density)&&density>0) s.append("; estimasi total ").append(show(area*density)).append(" pokok");
        s.append("\nProduktivitas aktual TBS: ").append(show(actual)).append(" ton/ha/tahun; target: ").append(show(target)).append(" ton/ha/tahun.\n");
        s.append("ANALISIS DAUN (hasil lab; bukan nilai sensor): N ").append(pref("palm_leaf_n_pct","--")).append("%, P ").append(pref("palm_leaf_p_pct","--")).append("%, K ").append(pref("palm_leaf_k_pct","--")).append("%, Ca ").append(pref("palm_leaf_ca_pct","--")).append("%, Mg ").append(pref("palm_leaf_mg_pct","--")).append("%, B ").append(pref("palm_leaf_b_mg_kg","--")).append(" mg/kg; tanggal ").append(pref("palm_leaf_analysis_date","--")).append(".\n");
        s.append("ANALISIS TANAH LAB: N-total ").append(pref("palm_lab_soil_n_pct","--")).append("%; P tersedia ").append(pref("palm_lab_soil_p_mg_kg","--")).append(" mg/kg; K dapat ditukar ").append(pref("palm_lab_soil_k_cmol_kg","--")).append("; Ca ").append(pref("palm_lab_soil_ca_cmol_kg","--")).append("; Mg ").append(pref("palm_lab_soil_mg_cmol_kg","--")).append(" cmol(+)/kg; C-organik ").append(pref("palm_lab_soil_c_org_pct","--")).append("%; tanggal ").append(pref("palm_soil_analysis_date","--")).append(".\n");
        s.append("Interpretasi unsur daun/tanah perlu memakai metode laboratorium, umur/fase, jenis tanah dan norma setempat; nilai mentah tidak otomatis menentukan dosis.\n");
        if(Double.isFinite(yieldTons)&&yieldTons>0){
            double factor=yieldTons/25.0;
            s.append("PERKIRAAN EKSPOR HARA PANEN TBS (berdasarkan produktivitas ").append(show(yieldTons)).append(" t/ha/tahun; skala literatur dari 25 t TBS/ha/tahun): N ").append(show(93.5*factor)).append(" kg/ha/tahun; P2O5 ").append(show(11.0*factor)).append(" kg/ha/tahun; K2O ").append(show(92.7*factor)).append(" kg/ha/tahun; Ca ").append(show(20.3*factor)).append(" kg/ha/tahun; Mg ").append(show(19.3*factor)).append(" kg/ha/tahun.\n");
            if(Double.isFinite(density)&&density>0) s.append("Per pokok (indikatif, memakai kepadatan yang diinput): N ").append(show(93.5*factor/density*1000)).append(" g/pokok/tahun; P2O5 ").append(show(11.0*factor/density*1000)).append(" g/pokok/tahun; K2O ").append(show(92.7*factor/density*1000)).append(" g/pokok/tahun.\n");
            s.append("CATATAN PENTING: ini estimasi hara yang terangkut bersama hasil panen, BUKAN dosis pupuk. Dosis produk harus memperhitungkan suplai tanah/daun, sisa pupuk, efisiensi serapan, bahan organik/pelepah, umur, kondisi kebun dan rekomendasi resmi PPKS/IOPRI. Jangan mengonversi angka ini langsung menjadi kg pupuk.\n");
        } else {
            s.append("Produktivitas aktual belum diisi; isi ton TBS/ha/tahun untuk menghitung estimasi ekspor hara panen.\n");
        }
    }

    private String compactReport(String text){
        String[] lines=text.split("\\n"); StringBuilder out=new StringBuilder(); boolean skip=false;
        for(String line:lines){String t=line.trim();
            if(t.startsWith("7. CATATAN ILMIAH")){skip=true;continue;}
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

    /** Split the complete unchanged calculation report into spacious, plain-language cards. */
    private void renderAgronomyReportCards(String raw) {
        if (reportCards == null || raw == null) { if (report != null) report.setVisibility(View.VISIBLE); return; }
        reportCards.removeAllViews();
        reportCards.setVisibility(View.VISIBLE);
        String[] lines = raw.split("\\r?\\n");
        int cursor = 0;
        TextView mainTitle = new TextView(this);
        mainTitle.setText("HASIL ANALISIS KEBUN");
        mainTitle.setTextColor(0xFF29C6C7); mainTitle.setTextSize(20);
        mainTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD); mainTitle.setLineSpacing(dp(3), 1.06f);
        mainTitle.setPadding(dp(2), dp(4), dp(2), dp(6));
        reportCards.addView(mainTitle, fullWidthWrap());

        // In the oil-palm variant, retain and foreground the existing executive summary.
        if (lines.length > 0 && lines[0].toUpperCase(Locale.ROOT).startsWith("RINGKASAN AWAL")) {
            StringBuilder summary = new StringBuilder();
            cursor = 1;
            for (; cursor < lines.length; cursor++) {
                String t = lines[cursor].trim();
                if (t.equalsIgnoreCase("DETAIL ANALISIS") || t.startsWith("────")) { cursor++; break; }
                if (!t.isEmpty()) summary.append(friendlyAgronomyLine(t)).append('\n');
            }
            addAgronomyCard("RINGKASAN AWAL KEBUN", summary.toString().trim(), true, false);
        } else if (lines.length > 0) {
            cursor = 1;
        }

        StringBuilder metadata = new StringBuilder();
        for (; cursor < lines.length; cursor++) {
            String line = lines[cursor].trim();
            if (isAgronomyHeading(line)) break;
            if (line.isEmpty() || line.equalsIgnoreCase("DETAIL ANALISIS") || line.startsWith("────")) continue;
            // Skip duplicate technical report title after the executive summary.
            if (line.startsWith("ANALISIS AGRONOMI TERPADU")) continue;
            metadata.append(friendlyAgronomyLine(line)).append('\n');
        }
        if (metadata.length() > 0) addAgronomyCard("PROFIL TANAMAN DAN KEBUN", metadata.toString().trim(), false, false);
        addAgronomyNote("Cara membaca: saran tindakan ada di bagian atas. Angka sensor dan input ditampilkan sebagai data masukan; label ‘perkiraan’ berarti hitungan model, bukan hasil laboratorium.");

        java.util.LinkedHashMap<String, ArrayList<String>> sections = new java.util.LinkedHashMap<>();
        String heading = null; ArrayList<String> body = new ArrayList<>();
        for (; cursor < lines.length; cursor++) {
            String line = lines[cursor].trim();
            if (isAgronomyHeading(line)) {
                if (heading != null) sections.put(heading, body);
                heading = line; body = new ArrayList<>();
            } else if (!line.isEmpty()) body.add(friendlyAgronomyLine(line));
        }
        if (heading != null) sections.put(heading, body);
        ArrayList<String> ordered = new ArrayList<>(sections.keySet());
        java.util.Collections.sort(ordered, (a, b) -> Integer.compare(agronomySectionOrder(a), agronomySectionOrder(b)));
        for (String h : ordered) {
            String hu = h.toUpperCase(Locale.ROOT);
            addAgronomyCard(friendlyAgronomyHeading(h), joinFriendlyLines(sections.get(h)), hu.contains("REKOMENDASI"), hu.contains("CATATAN ILMIAH") || hu.contains("SUMBER"));
        }
    }

    private boolean isAgronomyHeading(String line) { return line != null && line.matches("^[1-9]\\. .+"); }

    private int agronomySectionOrder(String heading) {
        String h = heading.toUpperCase(Locale.ROOT);
        if (h.contains("REKOMENDASI")) return 0;
        if (h.contains("TANAH")) return 10;
        if (h.contains("NPK") || h.contains("KEBUTUHAN FASE")) return 20;
        if (h.contains("DATA KEBUN") || h.contains("LAB KHUSUS")) return 25;
        if (h.contains("CUACA") || h.contains("AIR,")) return 30;
        if (h.contains("OPT") || h.contains("HAMA") || h.contains("PENYAKIT")) return 40;
        if (h.contains("ANALISIS RENTANG")) return 50;
        if (h.contains("CATATAN ILMIAH") || h.contains("SUMBER")) return 90;
        return 60;
    }

    private String friendlyAgronomyHeading(String input) {
        String value = input.replaceFirst("^[0-9]+\\.\\s*", "").trim();
        String h = value.toUpperCase(Locale.ROOT);
        if (h.equals("TANAH") || h.contains("STATUS TANAH")) return "KONDISI TANAH";
        if (h.contains("ANALISIS RENTANG SEMUA PARAMETER")) return "PEMERIKSAAN DATA LAINNYA";
        if (h.contains("NPK") || h.contains("KEBUTUHAN FASE")) return "PERKIRAAN KEBUTUHAN PUPUK";
        if (h.contains("CUACA") || h.contains("VPD")) return "CUACA DAN KEBUTUHAN AIR";
        if (h.contains("PREDIKSI POTENSI OPT")) return "WASPADA HAMA DAN PENYAKIT";
        if (h.contains("REKOMENDASI TEKNIS")) return "SARAN TINDAKAN UNTUK KEBUN";
        if (h.contains("DATA KEBUN") || h.contains("LAB KHUSUS")) return "DATA TAMBAHAN KELAPA SAWIT";
        if (h.contains("CATATAN ILMIAH")) return "CATATAN DAN SUMBER PERHITUNGAN";
        return value;
    }

    private String friendlyAgronomyLine(String input) {
        if (input == null) return "";
        String value = input.trim();
        String[][] replacements = {
            {"ANALISIS AGRONOMI TERPADU v1.5.8", "HASIL ANALISIS KEBUN"},
            {"KOMODITAS:", "Tanaman:"}, {"MODE:", "Cara budidaya:"}, {"HST:", "Umur setelah tanam (hari):"},
            {"FASE:", "Tahap tanaman:"}, {"LUAS:", "Luas kebun:"}, {"TARGET HASIL:", "Target hasil:"},
            {"pH ", "pH tanah "}, {"Metode N/P/K:", "Metode pemeriksaan N/P/K:"}, {"Metode N/P/K:", "Metode pemeriksaan N/P/K:"},
            {"N tersedia", "Nitrogen (N) tersedia"}, {"P tersedia", "Fosfor (P) tersedia"}, {"K tersedia", "Kalium (K) tersedia"},
            {"N-total diprediksi dari N tersedia", "Perkiraan N total dari N tersedia (bukan hasil laboratorium)"},
            {"Kelas N-total yang mungkin", "Perkiraan kelompok N total"}, {"EC sensor", "EC dari sensor tanah"},
            {"ECe lab", "ECe hasil laboratorium"}, {"ECe laboratorium", "ECe hasil laboratorium"},
            {"Kelembapan tanah", "Kadar air tanah"}, {"bulk density", "berat isi tanah"}, {"Stok lapisan", "Perkiraan hara pada lapisan tanah"},
            {"N skrining fase:", "Perkiraan kebutuhan nitrogen (N) tahap ini:"}, {"P2O5 skrining fase:", "Perkiraan kebutuhan fosfor (P₂O₅) tahap ini:"},
            {"K2O skrining fase:", "Perkiraan kebutuhan kalium (K₂O) tahap ini:"}, {"Kebutuhan screening fase ini:", "Perkiraan kebutuhan hara tahap ini:"},
            {"P2O5", "P₂O₅"}, {"K2O", "K₂O"}, {"RH ", "Kelembapan udara (RH) "}, {"VPD", "tekanan udara kering bagi tanaman (VPD)"},
            {"ET0", "perkiraan kebutuhan air cuaca (ET0)"}, {"Forecast 7 hari:", "Prakiraan cuaca 7 hari:"},
            {"PREDIKSI POTENSI OPT", "PERINGATAN DINI HAMA/PENYAKIT"}, {"RISK SCREENING", "perkiraan risiko awal"},
            {"STCR-style screening", "perkiraan kebutuhan hara"}, {"screening", "perkiraan awal"}, {"PHT", "pengendalian hama terpadu"},
            {"kg/ha", "kg/ha (kilogram per hektare)"}, {"µS/cm", "µS/cm"}, {"; ", ";\n• "}, {"→", " — "}
        };
        for (String[] pair : replacements) value = value.replace(pair[0], pair[1]);
        return value;
    }

    private String joinFriendlyLines(ArrayList<String> lines) {
        StringBuilder out = new StringBuilder();
        if (lines != null) for (String line : lines) { if (line == null || line.trim().isEmpty()) continue; if (out.length() > 0) out.append('\n'); out.append(line.trim()); }
        return out.toString();
    }

    private void addAgronomyCard(String heading, String body, boolean priority, boolean technical) {
        int accent = priority ? 0xFFFFD479 : 0xFF29C6C7;
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(12), dp(13), dp(12));
        card.setBackground(roundBg(technical ? 0xFF102B3C : 0xFF122B3B, technical ? 0xFF244353 : 0xFF214457));
        LinearLayout.LayoutParams lp = fullWidthWrap(); lp.topMargin = dp(9); reportCards.addView(card, lp);
        TextView title = new TextView(this); title.setText(heading); title.setTextColor(accent); title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); title.setLineSpacing(dp(2), 1.04f); title.setPadding(0, 0, 0, dp(6)); card.addView(title, fullWidthWrap());
        if (technical) addAgronomyBody(card, "Bagian ini berisi rincian tambahan. Utamakan saran tindakan di bagian atas.", false);
        if (body == null || body.trim().isEmpty()) { addAgronomyBody(card, "Belum ada informasi yang cukup untuk bagian ini.", false); return; }
        for (String rawLine : body.split("\\n")) {
            String content = rawLine.trim(); if (content.isEmpty()) continue;
            boolean bullet = content.startsWith("•") || content.startsWith("-");
            if (bullet) content = content.replaceFirst("^[•-]\\s*", "");
            addAgronomyBody(card, content, bullet);
        }
    }

    private void addAgronomyNote(String text) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(roundBg(0xFF173546, 0xFF3E5A67)); LinearLayout.LayoutParams lp = fullWidthWrap(); lp.topMargin = dp(7); reportCards.addView(card, lp);
        TextView title = new TextView(this); title.setText("CATATAN PENTING"); title.setTextColor(0xFFFFD479); title.setTextSize(14); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); card.addView(title, fullWidthWrap());
        addAgronomyBody(card, text, false);
    }

    private void addAgronomyBody(LinearLayout parent, String text, boolean bullet) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams rowLp = fullWidthWrap(); rowLp.topMargin = dp(3); rowLp.bottomMargin = dp(5); parent.addView(row, rowLp);
        if (bullet) { TextView dot = new TextView(this); dot.setText("•"); dot.setTextColor(0xFF45D483); dot.setTextSize(17); dot.setTypeface(Typeface.DEFAULT, Typeface.BOLD); dot.setPadding(0, 0, dp(8), 0); row.addView(dot, new LinearLayout.LayoutParams(dp(18), -2)); }
        TextView textView = new TextView(this); textView.setText(text); textView.setTextColor(0xFFF0F4F6); textView.setTextSize(15.5f); textView.setLineSpacing(dp(5), 1.08f); textView.setIncludeFontPadding(true);
        row.addView(textView, new LinearLayout.LayoutParams(0, -2, 1f));
    }

    private GradientDrawable roundBg(int color, int stroke) { GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(13)); bg.setStroke(dp(1), stroke); return bg; }
    private LinearLayout.LayoutParams fullWidthWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

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
    /** Improve AI answer readability while keeping the exact original answer in preferences. */
    private CharSequence styleAiResponse(String raw) {
        String cleaned = (raw == null ? "" : raw).replace("**", "").replace("###", "").replace("##", "");
        android.text.SpannableString styled = new android.text.SpannableString(cleaned);
        String all = styled.toString();
        int start = 0;
        while (start < all.length()) {
            int end = all.indexOf('\n', start); if (end < 0) end = all.length();
            String line = all.substring(start, end).trim();
            boolean numbered = line.matches("^[0-9]+[.)].*") || line.matches("^[-•]\\s+.+");
            boolean heading = line.length() > 4 && line.equals(line.toUpperCase(Locale.ROOT)) && !line.startsWith("http");
            if (!line.isEmpty() && (numbered || heading)) {
                styled.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                styled.setSpan(new android.text.style.ForegroundColorSpan(0xFF29C6C7), start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            start = end + 1;
        }
        return styled;
    }

    private void requestAi(){
        String key=prefs.getString("ai_api_key",DEFAULT_AI_KEY).trim(); if(key.isEmpty()){aiStatus.setText("Masukkan OpenAI API key di Pengaturan.");return;}
        final TextView button=findViewById(R.id.agroAiButton); button.setEnabled(false); aiStatus.setText("Menganalisis data, mencari OPT dan menyusun rekomendasi AI...");
        final String model=prefs.getString("ai_model","gpt-6-luna").trim().isEmpty()?"gpt-6-luna":prefs.getString("ai_model","gpt-6-luna").trim();
        final double aiLat=prefs.getFloat("latitude",Float.NaN);
        final double aiLon=prefs.getFloat("longitude",Float.NaN);
        final String context="LOKASI GPS: "+(Double.isFinite(aiLat)?String.format(Locale.US,"%.6f",aiLat):"--")+", "+(Double.isFinite(aiLon)?String.format(Locale.US,"%.6f",aiLon):"--")+"\n"+report.getText().toString();
        net.execute(()->{try{String lit=fetchOpenAlex(prefs.getString("crop","Tanaman pertanian"), context);String ans=callAi(key,model,context,lit);prefs.edit().putString("last_ai_advice",ans).putLong("last_ai_advice_epoch",System.currentTimeMillis()).apply();runOnUiThread(()->{aiStatus.setText(styleAiResponse(ans));button.setEnabled(true);});}catch(Exception e){runOnUiThread(()->{aiStatus.setText("AI gagal: "+msg(e));button.setEnabled(true);});}});
    }
    private String fetchOpenAlex(String crop,String context)throws Exception{
        String q=""+crop+" agriculture soil fertility pest disease weather forecasting";
        String url="https://api.openalex.org/works?search="+URLEncoder.encode(q,"UTF-8")+"&filter=from_publication_date:2018-01-01&per-page=6&sort=relevance_score:desc";
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setRequestMethod("GET");if(c.getResponseCode()!=200)throw new Exception("OpenAlex HTTP "+c.getResponseCode());JSONObject r=new JSONObject(readAll(c.getInputStream()));JSONArray a=r.optJSONArray("results");StringBuilder out=new StringBuilder();if(a!=null)for(int i=0;i<a.length();i++){JSONObject w=a.optJSONObject(i);if(w==null)continue;out.append(i+1).append(") ").append(w.optString("display_name",""));out.append(" | year=").append(w.optInt("publication_year",0));String doi=w.optString("doi","");if(!doi.isEmpty())out.append(" | ").append(doi);out.append("\n");}return out.length()==0?"Tidak ada hasil OpenAlex yang cocok.":out.toString();
    }
    private String callAi(String key,String model,String ctx,String lit)throws Exception{
        JSONObject p=new JSONObject();
        p.put("model",model);
        p.put("store",false);
        p.put("max_output_tokens",5000);
        JSONArray tools=new JSONArray();
        JSONObject webSearch=new JSONObject();
        webSearch.put("type","web_search");
        tools.put(webSearch);
        p.put("tools",tools);
        // OPT discovery is a mandatory web-search step for this agronomy advisor.
        p.put("tool_choice","required");
        p.put("instructions",
                "Anda adalah agronom pendamping petani Indonesia. Tujuan utama Anda bukan sekadar menjelaskan data, tetapi mengubah data menjadi KEPUTUSAN DAN REKOMENDASI TINDAKAN. " +
                "DATA ANALISIS AWAL sudah berisi klasifikasi parameter dari mesin agronomi; jangan mengulang seluruh parameter satu per satu. Fokus pada faktor pembatas dan tindakan. " +
                "WAJIB keluarkan bagian: 1) RINGKASAN KONDISI, 2) FAKTOR PEMBATAS UTAMA, 3) REKOMENDASI 0-24 JAM, 4) REKOMENDASI 1-7 HARI, 5) REKOMENDASI PEMUPUKAN, 6) REKOMENDASI AIR, 7) PREDIKSI POTENSI OPT, 8) TINDAKAN PHT/OPT, 9) PRIORITAS TINDAKAN, 10) DATA YANG MASIH PERLU DIUKUR. " +
                "Untuk OPT, JANGAN membatasi diri pada database lokal. Gunakan web search untuk mencari OPT yang relevan jika komoditas atau OPT tidak ada di aplikasi. Cari berdasarkan komoditas, fase tanaman, lokasi Indonesia/Jawa Tengah bila tersedia, gejala/riwayat lapangan, serta hubungan dengan suhu, RH, hujan, VPD dan kondisi lainnya. Prioritaskan sumber resmi pemerintah/universitas, extension service, jurnal ilmiah, atau organisasi pertanian terpercaya. " +
                "Sebutkan nama umum dan nama ilmiah bila dapat diverifikasi, tingkat potensi, alasan, gejala yang harus diperiksa dan sumber. Hasil OPT adalah risk screening, bukan diagnosis. Jangan membuat dosis pestisida baru; untuk pengendalian kimia rujuk label terdaftar dan aturan setempat. " +
                "Untuk kelapa sawit, gunakan basis data khusus TBM/TM dan fase umur. WAJIB lakukan web search untuk panduan resmi PPKS/IOPRI tentang rekomendasi pemupukan sawit; prioritaskan dokumen resmi. Jelaskan bahwa dosis resmi PPKS umumnya membutuhkan analisis daun dan tanah, evaluasi pertumbuhan/kultur teknis, umur, jumlah pokok/ha, jenis tanah, produktivitas aktual dan target, serta riwayat pupuk. Data khusus yang tersedia pada laporan harus digunakan bersama-sama. Bila produktivitas tersedia, gunakan hanya estimasi ekspor hara panen yang ditampilkan aplikasi sebagai konteks; jangan menyamakannya dengan dosis pupuk.  Gunakan NPK, EC (µS/cm) dan pH dari sensor USB 8-in-1 atau 7-in-1 CWT sebagai skrining, bukan sebagai angka laboratorium yang setara. Jangan mengarang tabel/dosis resmi PPKS atau mengubah mg/kg sensor menjadi kg pupuk/pokok tanpa metode tervalidasi. Jika data belum cukup, berikan prioritas unsur dan langkah sampling serta nyatakan data yang dibutuhkan sebelum dosis final. Berikan rekomendasi teknis sawit: drainase/air, konservasi tanah, penutup tanah, pengelolaan pelepah, piringan/jalur panen, monitoring pertumbuhan dan waktu aplikasi berdasarkan hujan. Untuk pH, jangan menghitung dosis kapur/dolomit dari pH saja. Untuk EC sensor, bedakan dari ECe dan jangan konversi otomatis menjadi ECe. Bedakan stok hara dari serapan tanaman. Untuk kebutuhan pupuk komoditas lain, nyatakan sebagai skrining bila tidak ada kalibrasi lokal/PUTS/STCR. Untuk mode organik, batasi input pada yang diizinkan sistem organik yang berlaku. " +
                "Gunakan bahasa Indonesia yang mudah dipahami petani tetapi tetap ilmiah. Jangan mengarang data yang tidak tersedia. Pisahkan fakta terukur, hasil model screening, dan rekomendasi. Jika data kurang, katakan apa yang perlu diukur. Selalu akhiri dengan PRIORITAS TINDAKAN yang konkret.");
        p.put("input",
                "DATA DAN ANALISIS AWAL:\n"+ctx+
                "\n\nHASIL PUSTAKA OPENALEX:\n"+lit+
                "\n\nGunakan web search khusus untuk memperluas pencarian OPT dan untuk mencari rekomendasi resmi PPKS/IOPRI jika komoditas kelapa sawit. Beri URL/sumber untuk klaim dosis resmi. Buat rekomendasi yang dapat ditindaklanjuti, bukan hanya deskripsi.");
        HttpURLConnection c=(HttpURLConnection)new URL("https://api.openai.com/v1/responses").openConnection();
        c.setRequestMethod("POST"); c.setConnectTimeout(12000); c.setReadTimeout(60000); c.setDoOutput(true);
        c.setRequestProperty("Authorization","Bearer "+key); c.setRequestProperty("Content-Type","application/json; charset=UTF-8");
        byte[] b=p.toString().getBytes(StandardCharsets.UTF_8); c.setFixedLengthStreamingMode(b.length);
        try(OutputStream o=c.getOutputStream()){o.write(b);} int code=c.getResponseCode();
        if(code<200||code>=300){String er=readAll(c.getErrorStream());throw new Exception("OpenAI HTTP "+code+(er.isEmpty()?"":" — "+er.substring(0,Math.min(500,er.length()))));}
        JSONObject r=new JSONObject(readAll(c.getInputStream())); String t=r.optString("output_text","").trim();
        if(!t.isEmpty()) return t;
        JSONArray out=r.optJSONArray("output"); if(out!=null){StringBuilder z=new StringBuilder(); for(int i=0;i<out.length();i++){JSONObject it=out.optJSONObject(i); if(it==null)continue; JSONArray cc=it.optJSONArray("content"); if(cc==null)continue; for(int j=0;j<cc.length();j++){JSONObject part=cc.optJSONObject(j); if(part!=null&&"output_text".equals(part.optString("type"))){z.append(part.optString("text","")).append("\n");}}} if(z.length()>0)return z.toString().trim();}
        throw new Exception("Respons AI tidak berisi teks.");
    }
    private String pref(String k,String d){return prefs.getString(k,d);}
    private double num(String s){try{return s==null||s.trim().isEmpty()?Double.NaN:Double.parseDouble(s.trim().replace(',','.'));}catch(Exception e){return Double.NaN;}}
    private String show(double v){return Double.isFinite(v)?String.format(Locale.US,"%.2f",v):"--";}
    private String readAll(InputStream in)throws Exception{if(in==null)return "";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String l;while((l=r.readLine())!=null)b.append(l);}return b.toString();}
    private String msg(Exception e){return e.getMessage()==null?"kesalahan tidak diketahui":e.getMessage();}
}
