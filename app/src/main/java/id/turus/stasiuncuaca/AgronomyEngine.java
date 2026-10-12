package id.turus.stasiuncuaca;

import java.util.Locale;

/**
 * Mesin analisis agronomi berbasis aturan ilmiah + model neraca hara.
 *
 * Catatan penting:
 * - N/P/K CWT-7in1 diperlakukan sebagai nilai input yang benar sesuai proyek,
 *   tetapi klasifikasi N/P/K diberi label SCREENING karena ambang resmi sangat
 *   bergantung metode ekstraksi, jenis tanah, dan komoditas.
 * - EC sensor tidak disamakan mentah-mentah dengan ECe laboratorium. Nilai
 *   dS/m hanya screening untuk risiko salinitas dan harus dikonfirmasi bila
 *   mendekati ambang kritis.
 * - Model dosis menggunakan baseline kebutuhan musim + kontribusi soil-test +
 *   efisiensi pemupukan + kredit riwayat. Ini STCR-style, bukan persamaan STCR
 *   spesifik satu komoditas. Persamaan spesifik harus menggantikan koefisien
 *   jika tersedia dari penelitian/lokasi.
 */
public final class AgronomyEngine {
    private AgronomyEngine() {}

    public static final class CropProfile {
        public final String name;
        public final int cycleDays;
        public final double phMin, phMax;
        public final double tempMin, tempMax;
        public final double ecThresholdDsM;
        public final double nMin, nMax;
        public final double pMin, pMax;
        public final double kMin, kMax;
        public final double seasonalN, seasonalP, seasonalK;
        public final double tBase;
        public final String source;

        CropProfile(String name, int cycleDays, double phMin, double phMax,
                    double tempMin, double tempMax, double ecThresholdDsM,
                    double nMin, double nMax, double pMin, double pMax,
                    double kMin, double kMax, double seasonalN, double seasonalP,
                    double seasonalK, double tBase, String source) {
            this.name = name;
            this.cycleDays = cycleDays;
            this.phMin = phMin;
            this.phMax = phMax;
            this.tempMin = tempMin;
            this.tempMax = tempMax;
            this.ecThresholdDsM = ecThresholdDsM;
            this.nMin = nMin;
            this.nMax = nMax;
            this.pMin = pMin;
            this.pMax = pMax;
            this.kMin = kMin;
            this.kMax = kMax;
            this.seasonalN = seasonalN;
            this.seasonalP = seasonalP;
            this.seasonalK = seasonalK;
            this.tBase = tBase;
            this.source = source;
        }
    }

    public static CropProfile profile(String crop) {
        String c = norm(crop);
        String src = "FAO/Indonesia fertilizer baseline + crop ecology; verify local SOP.";
        if (c.contains("padi") || c.contains("rice"))
            return new CropProfile("Padi",115,5.0,7.0,20,32,3.0,20,40,8,15,80,120,80,45,20,10,src);
        if (c.contains("jagung") || c.contains("maize") || c.contains("corn"))
            return new CropProfile("Jagung",110,5.5,7.5,18,33,1.7,20,40,8,15,80,120,80,40,20,10,src);
        if (c.contains("kedelai") || c.contains("soy"))
            return new CropProfile("Kedelai",95,5.5,7.0,20,30,5.0,20,40,8,15,80,140,25,40,15,10,src);
        if (c.contains("cabai") || c.contains("chili") || c.contains("pepper"))
            return new CropProfile("Cabai",130,5.5,7.0,20,32,1.7,20,40,10,20,100,180,90,55,35,10,src);
        if (c.contains("tomat") || c.contains("tomato"))
            return new CropProfile("Tomat",110,5.5,7.0,18,30,2.5,20,40,10,20,100,180,90,60,40,10,src);
        if (c.contains("singkong") || c.contains("cassava"))
            return new CropProfile("Singkong",300,4.5,7.0,20,32,2.5,20,40,8,15,80,160,70,35,35,12,src);
        if (c.contains("ubi jalar") || c.contains("sweet potato"))
            return new CropProfile("Ubi jalar",120,5.5,6.8,20,30,1.5,20,40,8,15,80,140,60,30,30,12,src);
        if (c.contains("kentang") || c.contains("potato"))
            return new CropProfile("Kentang",100,5.0,6.5,15,25,1.7,20,40,10,20,100,180,105,70,35,7,src);
        if (c.contains("bawang merah") || c.contains("shallot") || c.contains("onion"))
            return new CropProfile("Bawang",90,5.5,6.8,15,30,1.2,20,40,10,20,80,160,100,55,35,8,src);
        if (c.contains("kelapa sawit") || c.contains("sawit") || c.contains("oil palm"))
            return new CropProfile("Kelapa sawit",3650,4.0,7.0,25,32,2.5,20,40,8,15,100,180,120,60,180,15,src);
        return new CropProfile("Komoditas umum",120,5.5,7.0,18,32,2.0,20,40,8,15,80,140,80,40,30,10,src);
    }

    public static String normalizeCrop(String crop) {
        String c = norm(crop);
        if (c.contains("padi") || c.contains("rice")) return "Padi";
        if (c.contains("jagung") || c.contains("maize") || c.contains("corn")) return "Jagung";
        if (c.contains("kedelai") || c.contains("soy")) return "Kedelai";
        if (c.contains("cabai") || c.contains("chili") || c.contains("pepper")) return "Cabai";
        if (c.contains("tomat") || c.contains("tomato")) return "Tomat";
        if (c.contains("singkong") || c.contains("cassava")) return "Singkong";
        if (c.contains("ubi jalar") || c.contains("sweet potato")) return "Ubi jalar";
        if (c.contains("kentang") || c.contains("potato")) return "Kentang";
        if (c.contains("bawang merah") || c.contains("shallot") || c.contains("onion")) return "Bawang";
        if (c.contains("kelapa sawit") || c.contains("sawit") || c.contains("oil palm")) return "Kelapa sawit";
        return crop == null || crop.trim().isEmpty() ? "Komoditas umum" : crop.trim();
    }

    public static String phase(int hst, String crop) {
        CropProfile p = profile(crop);
        if (hst < 0) return "Belum tanam / HST belum valid";
        if (p.name.equals("Kelapa sawit")) {
            if (hst < 365) return "TBM awal / pembentukan vegetatif";
            if (hst < 1095) return "TBM lanjut / vegetatif";
            return "TM / produksi";
        }
        if (hst <= Math.max(20, (int)(p.cycleDays * 0.18))) return "Perkecambahan / establishment";
        if (hst <= Math.max(35, (int)(p.cycleDays * 0.45))) return "Vegetatif";
        if (hst <= Math.max(60, (int)(p.cycleDays * 0.68))) return "Pembungaan / reproduktif";
        if (hst <= p.cycleDays) return "Pengisian hasil / pemasakan";
        return "Lewat umur normal panen — verifikasi varietas/rotasi";
    }

    public static double stageFraction(int hst, String crop) {
        CropProfile p = profile(crop);
        String ph = phase(hst, crop).toLowerCase(Locale.US);
        if (ph.contains("establishment")) return 0.15;
        if (ph.contains("vegetatif")) return 0.30;
        if (ph.contains("reproduktif") || ph.contains("pembungaan")) return 0.35;
        if (ph.contains("pengisian") || ph.contains("pemasakan")) return 0.20;
        return 0.25;
    }

    public static String classifyN(double value) {
        if (Double.isNaN(value)) return "Tidak ada data";
        return "Metode/ambang N belum diketahui";
    }

    public static String classifyN(double value, double low, double high) {
        if (Double.isNaN(value)) return "Tidak ada data";
        if (!Double.isFinite(low) || !Double.isFinite(high) || low < 0 || high <= low) return classifyN(value);
        if (value < low) return "Rendah";
        if (value <= high) return "Sedang";
        return "Tinggi";
    }

    public static String classifyP(double value) { return classifyP(value, ""); }

    /**
     * Five-class screening for P available in mg/kg.
     * Reference band used for the generic sensor-screening path:
     * <4.4, 4.4-6.5, 6.6-10.9, 11-15.3, >15.3 mg/kg.
     * HCl 25%, Bray-1 and Olsen retain their method label; the sensor value is
     * not silently converted to a laboratory method-equivalent.
     */
    public static String classifyP(double value, String method) {
        if (Double.isNaN(value)) return "Tidak ada data";
        String m = norm(method);
        String basis = "P tersedia screening";
        double a = 4.4, b = 6.5, c = 10.9, d = 15.3;
        if (m.contains("hcl")) {
            // P2O5 HCl 25% reference: <10, 10-20, 21-40, 41-60, >60 mg/100g.
            // Display/input remains mg/kg; ×10 is shown as an equivalent reference scale.
            a = 100; b = 200; c = 400; d = 600; basis = "P2O5-HCl 25% ekuivalen screening";
        } else if (m.contains("bray")) {
            a = 10; b = 15; c = 25; d = 35; basis = "P Bray-1 screening";
        } else if (m.contains("olsen")) {
            a = 10; b = 25; c = 45; d = 60; basis = "P Olsen screening";
        } else if (m.contains("mehlich")) {
            basis = "P tersedia screening (Mehlich-3; perlu kalibrasi lokal)";
        }
        return fiveClass(value, a, b, c, d, basis);
    }

    public static String classifyK(double value) { return classifyK(value, ""); }

    /**
     * Five-class screening for K in mg/kg.
     * NH4OAc reference uses exchangeable K class boundaries converted from
     * 0.10, 0.30, 0.50 and 1.00 cmol(+)/kg K to approximately
     * 39.1, 117.3, 195.5 and 391 mg/kg K.
     */
    public static String classifyK(double value, String method) {
        if (Double.isNaN(value)) return "Tidak ada data";
        String m = norm(method);
        double a = 39.1, b = 117.3, c = 195.5, d = 391.0;
        String basis = "K tersedia screening (ekuivalen K tertukar)";
        if (m.contains("hcl")) {
            // K2O HCl 25% reference: <10, 10-20, 21-40, 41-60, >60 mg/100g.
            a = 100; b = 200; c = 400; d = 600; basis = "K2O-HCl 25% ekuivalen screening";
        } else if (m.contains("nh4oac") || m.contains("nh4 oac") || m.contains("ammonium acetate")) {
            basis = "K tertukar NH4OAc ekuivalen screening";
        } else if (m.contains("mehlich")) {
            basis = "K tersedia screening (Mehlich-3; perlu kalibrasi lokal)";
        }
        return fiveClass(value, a, b, c, d, basis);
    }

    private static String fiveClass(double value, double a, double b, double c, double d, String basis) {
        if (!Double.isFinite(value)) return "Tidak ada data";
        String cls = value < a ? "Sangat Rendah" : value <= b ? "Rendah" : value <= c ? "Sedang" : value <= d ? "Tinggi" : "Sangat Tinggi";
        return cls + " (" + basis + ")";
    }

    /** Kelas pH air/H2O menurut rujukan Pusat Penelitian Tanah. */
    public static String classifyPHRange(double ph) {
        if (!Double.isFinite(ph)) return "Tidak ada data";
        if (ph < 4.5) return "Sangat masam (<4,5)";
        if (ph <= 5.5) return "Masam (4,5–5,5)";
        if (ph <= 6.5) return "Agak masam (5,6–6,5)";
        if (ph <= 7.5) return "Netral (6,6–7,5)";
        if (ph <= 8.5) return "Agak alkalis (7,6–8,5)";
        return "Alkalis (>8,5)";
    }

    public static String classifyPH(double ph, CropProfile p) {
        if (!Double.isFinite(ph)) return "Tidak ada data";
        String range = classifyPHRange(ph);
        if (p == null) return range;
        boolean ok = ph >= p.phMin && ph <= p.phMax;
        return range + (ok ? " • sesuai kisaran " : " • di luar kisaran ")
                + String.format(Locale.US, "%.1f–%.1f", p.phMin, p.phMax) + " untuk " + p.name;
    }

    /** Lima kelas screening DHL/EC tanah (dS/m); input sensor diterima dalam µS/cm. */
    public static String classifyECSensor(double usCm) {
        if (!Double.isFinite(usCm)) return "Tidak ada data";
        double ds = usCm / 1000.0;
        if (ds < 1.0) return "Sangat Rendah (<1,0 dS/m)";
        if (ds <= 2.0) return "Rendah (1,0–2,0 dS/m)";
        if (ds <= 3.0) return "Sedang (2,0–3,0 dS/m)";
        if (ds <= 4.0) return "Tinggi (3,0–4,0 dS/m)";
        return "Sangat Tinggi (>4,0 dS/m)";
    }

    public static String classifyEC(double usCm, CropProfile p) {
        if (!Double.isFinite(usCm)) return "Tidak ada data";
        String range = classifyECSensor(usCm);
        String cropPart = p == null ? "" : String.format(Locale.US,
                " • ambang profil %s %.1f dS/m", p.name, p.ecThresholdDsM);
        return range + cropPart + " • EC sensor (input utama), ECe lab opsional";
    }

    public static String classifyMoisture(double pct, String crop) {
        if (Double.isNaN(pct)) return "Tidak ada data";
        if (pct < 20) return "Kering";
        if (pct < 35) return "Cenderung kering";
        if (pct <= 70) return "Aman / cukup";
        if (pct <= 85) return "Lembap tinggi";
        return "Terlalu lembap / cek drainase";
    }

    public static String classifyMoisture(double pct) {
        return classifyMoisture(pct, "");
    }


    /**
     * Klasifikasi screening 5 tingkat untuk parameter lingkungan. Batas di bawah
     * dipakai sebagai indikator operasional, bukan pengganti ambang komoditas/lokal.
     */
    public static String classifyAirHumidity(double rh) {
        if (!Double.isFinite(rh)) return "Tidak ada data";
        if (rh < 40) return "Sangat rendah (<40%)";
        if (rh < 60) return "Rendah (40–<60%)";
        if (rh <= 80) return "Sedang/umumnya nyaman (60–80%)";
        if (rh <= 90) return "Tinggi (81–90%)";
        return "Sangat tinggi (>90%)";
    }

    public static String classifyAirTemperature(double tempC, CropProfile p) {
        if (!Double.isFinite(tempC)) return "Tidak ada data";
        if (p == null) return "Suhu terukur";
        double lowExtreme = p.tempMin - 5.0, highExtreme = p.tempMax + 5.0;
        if (tempC < lowExtreme) return "Sangat rendah dibanding profil (" + fmt1(lowExtreme) + "–" + fmt1(p.tempMin) + " °C batas bawah)";
        if (tempC < p.tempMin) return "Rendah / di bawah profil (" + fmt1(p.tempMin) + " °C)";
        if (tempC <= p.tempMax) return "Sesuai kisaran profil (" + fmt1(p.tempMin) + "–" + fmt1(p.tempMax) + " °C)";
        if (tempC <= highExtreme) return "Tinggi / di atas profil (" + fmt1(p.tempMax) + " °C)";
        return "Sangat tinggi dibanding profil (>" + fmt1(highExtreme) + " °C)";
    }

    public static String classifyPressure(double hpa) {
        if (!Double.isFinite(hpa)) return "Tidak ada data";
        if (hpa < 985) return "Sangat rendah (indikatif; koreksi ketinggian diperlukan)";
        if (hpa < 1005) return "Rendah (indikatif)";
        if (hpa <= 1025) return "Sedang/sekitar kisaran meteorologi umum";
        if (hpa <= 1040) return "Tinggi (indikatif)";
        return "Sangat tinggi (indikatif)";
    }

    public static String classifyRain(double mm) {
        if (!Double.isFinite(mm)) return "Tidak ada data";
        if (mm <= 0) return "Tidak ada hujan";
        if (mm < 5) return "Ringan (0–<5 mm/hari)";
        if (mm < 20) return "Sedang (5–<20 mm/hari)";
        if (mm < 50) return "Tinggi (20–<50 mm/hari)";
        return "Sangat tinggi (≥50 mm/hari)";
    }

    public static String classifyEt0(double mm) {
        if (!Double.isFinite(mm)) return "Tidak ada data";
        if (mm < 2) return "Sangat rendah / kebutuhan atmosfer rendah (<2 mm/hari)";
        if (mm < 4) return "Rendah–sedang (2–<4 mm/hari)";
        if (mm < 6) return "Sedang–tinggi (4–<6 mm/hari)";
        if (mm <= 8) return "Tinggi (6–8 mm/hari)";
        return "Sangat tinggi (>8 mm/hari)";
    }

    public static String classifyWind(double ms) {
        if (!Double.isFinite(ms)) return "Tidak ada data";
        if (ms < 1) return "Sangat lemah (<1 m/s)";
        if (ms < 3) return "Lemah (1–<3 m/s)";
        if (ms < 6) return "Sedang (3–<6 m/s)";
        if (ms <= 10) return "Kuat (6–10 m/s)";
        return "Sangat kuat (>10 m/s)";
    }

    public static String classifyCloud(double pct) {
        if (!Double.isFinite(pct)) return "Tidak ada data";
        if (pct <= 20) return "Sangat rendah (0–20%)";
        if (pct <= 40) return "Rendah (21–40%)";
        if (pct <= 60) return "Sedang (41–60%)";
        if (pct <= 80) return "Tinggi (61–80%)";
        return "Sangat tinggi (81–100%)";
    }

    public static String classifyUv(double uv) {
        if (!Double.isFinite(uv)) return "Tidak ada data";
        if (uv <= 2) return "Rendah (0–2)";
        if (uv <= 5) return "Sedang (3–5)";
        if (uv <= 7) return "Tinggi (6–7)";
        if (uv <= 10) return "Sangat tinggi (8–10)";
        return "Ekstrem (≥11)";
    }

    public static String classifyVisibility(double meters) {
        if (!Double.isFinite(meters)) return "Tidak ada data";
        double km = meters / 1000.0;
        if (km < 1) return "Sangat buruk (<1 km)";
        if (km < 4) return "Buruk (1–<4 km)";
        if (km < 10) return "Sedang (4–<10 km)";
        if (km <= 20) return "Baik (10–20 km)";
        return "Sangat baik (>20 km)";
    }

    public static String classifyLux(double lux) {
        if (!Double.isFinite(lux)) return "Tidak ada data";
        if (lux < 1000) return "Sangat rendah (<1.000 lux)";
        if (lux < 10000) return "Rendah (1.000–<10.000 lux)";
        if (lux < 30000) return "Sedang (10.000–<30.000 lux)";
        if (lux <= 60000) return "Tinggi (30.000–60.000 lux)";
        return "Sangat tinggi (>60.000 lux)";
    }

    public static String classifyShortwave(double wm2) {
        if (!Double.isFinite(wm2)) return "Tidak ada data";
        if (wm2 < 100) return "Sangat rendah (<100 W/m²)";
        if (wm2 < 300) return "Rendah (100–<300 W/m²)";
        if (wm2 < 600) return "Sedang (300–<600 W/m²)";
        if (wm2 <= 800) return "Tinggi (600–800 W/m²)";
        return "Sangat tinggi (>800 W/m²)";
    }

    public static String classifyParEnergy(double wm2) {
        if (!Double.isFinite(wm2)) return "Tidak ada data";
        if (wm2 < 45) return "Sangat rendah (<45 W/m² PAR)";
        if (wm2 < 135) return "Rendah (45–<135 W/m² PAR)";
        if (wm2 < 270) return "Sedang (135–<270 W/m² PAR)";
        if (wm2 <= 360) return "Tinggi (270–360 W/m² PAR)";
        return "Sangat tinggi (>360 W/m² PAR)";
    }

    public static String classifyPpfd(double ppfd) {
        if (!Double.isFinite(ppfd)) return "Tidak ada data";
        if (ppfd < 200) return "Sangat rendah (<200 µmol/m²/s)";
        if (ppfd < 400) return "Rendah (200–<400 µmol/m²/s)";
        if (ppfd < 700) return "Sedang (400–<700 µmol/m²/s)";
        if (ppfd <= 1000) return "Tinggi (700–1.000 µmol/m²/s)";
        return "Sangat tinggi (>1.000 µmol/m²/s)";
    }

    public static String classifySunshine(double hours) {
        if (!Double.isFinite(hours)) return "Tidak ada data";
        if (hours < 2) return "Sangat rendah (<2 jam/hari)";
        if (hours < 4) return "Rendah (2–<4 jam/hari)";
        if (hours < 6) return "Sedang (4–<6 jam/hari)";
        if (hours <= 8) return "Tinggi (6–8 jam/hari)";
        return "Sangat tinggi (>8 jam/hari)";
    }

    public static String classifySoilTemperature(double tempC) {
        if (!Double.isFinite(tempC)) return "Tidak ada data";
        if (tempC < 15) return "Sangat rendah (<15 °C)";
        if (tempC < 20) return "Rendah (15–<20 °C)";
        if (tempC <= 30) return "Sedang/favorable umum (20–30 °C)";
        if (tempC <= 35) return "Tinggi (30–35 °C)";
        return "Sangat tinggi (>35 °C)";
    }

    public static String classifyOrganicMatter(double pct) {
        if (!Double.isFinite(pct)) return "Tidak ada data";
        if (pct < 1.0) return "Sangat rendah (<1,0%)";
        if (pct <= 2.0) return "Rendah (1,0–2,0%)";
        if (pct <= 3.0) return "Sedang (2,1–3,0%)";
        if (pct <= 5.0) return "Tinggi (3,1–5,0%)";
        return "Sangat tinggi (>5,0%)";
    }

    public static String classifyCec(double cec) {
        if (!Double.isFinite(cec)) return "Tidak ada data";
        if (cec < 5) return "Sangat rendah (<5 cmol(+)/kg)";
        if (cec <= 16) return "Rendah (5–16 cmol(+)/kg)";
        if (cec <= 24) return "Sedang (17–24 cmol(+)/kg)";
        if (cec <= 40) return "Tinggi (25–40 cmol(+)/kg)";
        return "Sangat tinggi (>40 cmol(+)/kg)";
    }

    public static String classifyBulkDensity(double bd) {
        if (!Double.isFinite(bd)) return "Tidak ada data";
        if (bd < 0.8) return "Sangat rendah (<0,8 g/cm³; verifikasi bahan organik/tekstur)";
        if (bd < 1.0) return "Rendah (0,8–<1,0 g/cm³)";
        if (bd <= 1.4) return "Sedang (1,0–1,4 g/cm³)";
        if (bd <= 1.6) return "Tinggi (1,4–1,6 g/cm³)";
        return "Sangat tinggi (>1,6 g/cm³; indikasi pemadatan pada banyak tanah mineral)";
    }

    public static String classifyDepth(double depthCm) {
        if (!Double.isFinite(depthCm)) return "Tidak ada data";
        if (depthCm < 10) return "Sangat dangkal (<10 cm)";
        if (depthCm < 20) return "Dangkal (10–<20 cm)";
        if (depthCm <= 30) return "Sedang (20–30 cm)";
        if (depthCm <= 60) return "Dalam (31–60 cm)";
        return "Sangat dalam (>60 cm)";
    }

    public static String classifyDewPointSpread(double airTempC, double dewPointC) {
        if (!Double.isFinite(airTempC) || !Double.isFinite(dewPointC)) return "Tidak ada data";
        double spread = airTempC - dewPointC;
        if (spread <= 2) return "Sangat lembap / risiko kondensasi tinggi (T–Td ≤2 °C)";
        if (spread <= 5) return "Lembap (T–Td 2–5 °C)";
        if (spread <= 10) return "Sedang (T–Td 5–10 °C)";
        if (spread <= 15) return "Kering (T–Td 10–15 °C)";
        return "Sangat kering (T–Td >15 °C)";
    }

    public static String classifyApparentTemperature(double airTempC, double apparentC) {
        if (!Double.isFinite(airTempC) || !Double.isFinite(apparentC)) return "Tidak ada data";
        double d = apparentC - airTempC;
        if (d < -3) return "Terasa jauh lebih dingin dari suhu aktual";
        if (d < -1) return "Terasa sedikit lebih dingin";
        if (d <= 1) return "Hampir sama dengan suhu aktual";
        if (d <= 3) return "Terasa sedikit lebih panas";
        return "Terasa jauh lebih panas";
    }

    /**
     * Analisis awal seluruh parameter yang tersedia. Fungsi ini sengaja hanya
     * mengklasifikasikan rentang/status; keputusan/rekomendasi dilakukan setelahnya.
     */
    public static String comprehensiveRangeAnalysis(
            String crop,
            double ph, double n, double nLow, double nHigh, double p, double k,
            double ecUsCm, double eceDsM, double soilMoisturePct, double fcPct, double pwpPct,
            double openMeteoSoilMoistureM3m3, double soilTempC,
            double organicMatterPct, double cec, double bulkDensity, double depthCm,
            double airTempC, double apparentTempC, double dewPointC, double rhPct,
            double pressureHpa, double rainMm, double et0Mm, double windMs, double gustMs,
            double cloudPct, double uv, double visibilityM, double lux, double shortwaveWm2,
            double parWm2, double ppfd, double sunshineHours, double vpd, String soilMethod) {
        CropProfile cp = profile(crop);
        StringBuilder s = new StringBuilder();
        s.append("ANALISIS AWAL — SEMUA PARAMETER (rentang/status dulu)\n");
        if (Double.isFinite(ph)) s.append("pH: ").append(fmt2(ph)).append(" → ").append(classifyPH(ph, cp)).append("\n");
        if (Double.isFinite(n)) s.append("N tersedia: ").append(fmt2(n)).append(" mg/kg → ").append(classifyN(n,nLow,nHigh)).append("; N-total estimasi → ").append(NitrogenInference.classifyRange(n)).append("\n");
        if (Double.isFinite(p)) s.append("P: ").append(fmt2(p)).append(" mg/kg → ").append(classifyP(p, soilMethod)).append("\n");
        if (Double.isFinite(k)) s.append("K: ").append(fmt2(k)).append(" mg/kg → ").append(classifyK(k, soilMethod)).append("\n");
        if (Double.isFinite(ecUsCm)) s.append("EC sensor: ").append(fmt2(ecUsCm)).append(" µS/cm → ").append(classifyEC(ecUsCm, cp)).append("\n");
        if (Double.isFinite(eceDsM)) s.append("ECe lab: ").append(fmt2(eceDsM)).append(" dS/m → ").append(classifyECe(eceDsM)).append("\n");
        if (Double.isFinite(soilMoisturePct)) {
            s.append("Kelembapan tanah sensor: ").append(fmt2(soilMoisturePct)).append(" % → ").append(classifyMoisture(soilMoisturePct,crop)).append("\n");
            if (Double.isFinite(fcPct) && Double.isFinite(pwpPct) && fcPct > pwpPct)
                s.append("Air tanah relatif sensor terhadap FC/PWP: ").append(soilWaterAssessment(soilMoisturePct,fcPct,pwpPct,20,et0Mm,crop)).append("\n");
        }
        if (Double.isFinite(openMeteoSoilMoistureM3m3)) {
            double omPct = openMeteoSoilMoistureM3m3 * 100.0;
            s.append("Kelembapan tanah Open-Meteo 0–10 cm: ").append(fmt2(openMeteoSoilMoistureM3m3)).append(" m³/m³ (" ).append(fmt2(omPct)).append(" %vol)");
            if (Double.isFinite(fcPct) && Double.isFinite(pwpPct) && fcPct > pwpPct)
                s.append(" → ").append(soilWaterAssessment(omPct,fcPct,pwpPct,10,et0Mm,crop));
            else s.append(" → perlu FC/PWP untuk interpretasi kapasitas air tanah");
            s.append("\n");
        }
        if (Double.isFinite(soilTempC)) s.append("Suhu tanah: ").append(fmt2(soilTempC)).append(" °C → ").append(classifySoilTemperature(soilTempC)).append("\n");
        if (Double.isFinite(organicMatterPct)) s.append("Bahan organik: ").append(fmt2(organicMatterPct)).append(" % → ").append(classifyOrganicMatter(organicMatterPct)).append("\n");
        if (Double.isFinite(cec)) s.append("CEC/KTK: ").append(fmt2(cec)).append(" → ").append(classifyCec(cec)).append("\n");
        if (Double.isFinite(bulkDensity)) s.append("Bulk density: ").append(fmt2(bulkDensity)).append(" g/cm³ → ").append(classifyBulkDensity(bulkDensity)).append("\n");
        if (Double.isFinite(depthCm)) s.append("Kedalaman lapisan: ").append(fmt2(depthCm)).append(" cm → ").append(classifyDepth(depthCm)).append("\n");
        if (Double.isFinite(airTempC)) s.append("Suhu udara: ").append(fmt2(airTempC)).append(" °C → ").append(classifyAirTemperature(airTempC,cp)).append("\n");
        if (Double.isFinite(apparentTempC)) s.append("Suhu terasa: ").append(fmt2(apparentTempC)).append(" °C → ").append(classifyApparentTemperature(airTempC,apparentTempC)).append("\n");
        if (Double.isFinite(dewPointC)) s.append("Titik embun: ").append(fmt2(dewPointC)).append(" °C → ").append(classifyDewPointSpread(airTempC,dewPointC)).append("\n");
        if (Double.isFinite(rhPct)) s.append("RH: ").append(fmt2(rhPct)).append(" % → ").append(classifyAirHumidity(rhPct)).append("\n");
        if (Double.isFinite(pressureHpa)) s.append("Tekanan: ").append(fmt2(pressureHpa)).append(" hPa → ").append(classifyPressure(pressureHpa)).append("\n");
        if (Double.isFinite(rainMm)) s.append("Hujan: ").append(fmt2(rainMm)).append(" mm/hari → ").append(classifyRain(rainMm)).append("\n");
        if (Double.isFinite(et0Mm)) s.append("ET₀: ").append(fmt2(et0Mm)).append(" mm/hari → ").append(classifyEt0(et0Mm)).append("\n");
        if (Double.isFinite(windMs)) s.append("Angin: ").append(fmt2(windMs)).append(" m/s → ").append(classifyWind(windMs)).append("\n");
        if (Double.isFinite(gustMs)) s.append("Gust: ").append(fmt2(gustMs)).append(" m/s → ").append(classifyWind(gustMs)).append("\n");
        if (Double.isFinite(cloudPct)) s.append("Tutupan awan: ").append(fmt2(cloudPct)).append(" % → ").append(classifyCloud(cloudPct)).append("\n");
        if (Double.isFinite(uv)) s.append("UV index: ").append(fmt2(uv)).append(" → ").append(classifyUv(uv)).append("\n");
        if (Double.isFinite(visibilityM)) s.append("Visibilitas: ").append(fmt2(visibilityM/1000.0)).append(" km → ").append(classifyVisibility(visibilityM)).append("\n");
        if (Double.isFinite(lux)) s.append("Cahaya: ").append(fmt2(lux)).append(" lux → ").append(classifyLux(lux)).append("\n");
        if (Double.isFinite(shortwaveWm2)) s.append("Shortwave: ").append(fmt2(shortwaveWm2)).append(" W/m² → ").append(classifyShortwave(shortwaveWm2)).append("\n");
        if (Double.isFinite(parWm2)) s.append("PAR energi: ").append(fmt2(parWm2)).append(" W/m² → ").append(classifyParEnergy(parWm2)).append("\n");
        if (Double.isFinite(ppfd)) s.append("PPFD: ").append(fmt2(ppfd)).append(" µmol/m²/s → ").append(classifyPpfd(ppfd)).append("\n");
        if (Double.isFinite(sunshineHours)) s.append("Lama penyinaran: ").append(fmt2(sunshineHours)).append(" jam/hari → ").append(classifySunshine(sunshineHours)).append("\n");
        if (Double.isFinite(vpd)) s.append("VPD: ").append(fmt2(vpd)).append(" kPa → ").append(classifyVpd(vpd)).append("\n");
        s.append("Catatan: parameter yang tidak memiliki ambang universal (mis. tekanan absolut, FC/PWP, buffer pH, Al-dd/H-dd) harus dibaca bersama konteks lokasi, metode dan tren; jangan dipaksa menjadi dosis otomatis.\n");
        s.append("SELESAI ANALISIS AWAL → baru tentukan faktor pembatas/prioritas → baru susun rekomendasi.");
        return s.toString();
    }

    private static String fmt1(double v) { return String.format(Locale.US,"%.1f",v); }
    private static String fmt2(double v) { return String.format(Locale.US,"%.2f",v); }

    public static String classifyECe(double eceDsM) {
        if (Double.isNaN(eceDsM)) return "Tidak ada data";
        if (eceDsM < 2) return "Non-saline";
        if (eceDsM < 4) return "Sedikit salin";
        if (eceDsM < 8) return "Moderat salin";
        if (eceDsM < 16) return "Sangat salin";
        return "Ekstrem salin";
    }

    public static double soilStockKgHa(double concentrationMgKg, double bulkDensityGcm3, double depthCm) {
        if (!Double.isFinite(concentrationMgKg) || !Double.isFinite(bulkDensityGcm3) || !Double.isFinite(depthCm)) return Double.NaN;
        if (concentrationMgKg < 0 || bulkDensityGcm3 <= 0 || depthCm <= 0) return Double.NaN;
        // mg/kg × g/cm³ × cm × 0.10 = kg/ha.
        return concentrationMgKg * bulkDensityGcm3 * depthCm * 0.10;
    }

    public static String soilWaterAssessment(double moisturePct, double fcPct, double pwpPct, double depthCm, double et0Mm, String crop) {
        if (!Double.isFinite(moisturePct)) return "DATA AIR TANAH TIDAK ADA";
        if (Double.isFinite(fcPct) && Double.isFinite(pwpPct) && fcPct > pwpPct) {
            double ftsw=(moisturePct-pwpPct)/(fcPct-pwpPct);
            ftsw=Math.max(0,Math.min(1,ftsw));
            double rawFrac=0.5;
            if (Double.isFinite(et0Mm)) rawFrac = et0Mm >= 5 ? 0.3 : (et0Mm <= 2 ? 0.7 : 0.5);
            String state=ftsw<rawFrac?"KURANG / mendekati di bawah RAW":(ftsw>0.9?"TINGGI / dekat FC":"AMAN / dalam zona air tersedia");
            return String.format(Locale.US,"%s; FTSW %.2f; TAW lapisan %.1f mm; RAW perkiraan %.1f mm",state,ftsw,Math.max(0,(fcPct-pwpPct)*depthCm*0.1),Math.max(0,(fcPct-pwpPct)*depthCm*0.1*rawFrac));
        }
        return classifyMoisture(moisturePct,crop)+"; FC/PWP belum tersedia, jadi persentase sensor hanya screening.";
    }

    public static String vpdCombinedStatus(double vpd, double moisturePct, double fcPct, double pwpPct, double depthCm, double et0Mm, String crop) {
        if (!Double.isFinite(vpd)) return "VPD belum tersedia.";
        String water=soilWaterAssessment(moisturePct,fcPct,pwpPct,depthCm,et0Mm,crop);
        boolean high=vpd>2.0; boolean dry=water.startsWith("KURANG") || (!Double.isFinite(fcPct) && moisturePct<30);
        if (high && dry) return "PERHATIAN TINGGI: VPD tinggi + air tanah rendah dapat meningkatkan stres air tanaman.";
        if (high) return "WASPADA: VPD tinggi; pantau kehilangan air, layu, bunga/daun dan ketersediaan air.";
        return "Kombinasi VPD dan air tanah belum menunjukkan tekanan tinggi; tetap pantau tren.";
    }

    public static String temperatureStatus(double tempC, String crop) {
        if (!Double.isFinite(tempC)) return "Tidak ada data";
        CropProfile p=profile(crop);
        if (tempC<p.tempMin) return "Di bawah kisaran ekologis profil";
        if (tempC>p.tempMax) return "Di atas kisaran ekologis profil";
        return "Dalam kisaran ekologis profil";
    }

    public static String nutrientSufficiency(double soilValue, String nutrient, String crop, int hst) {
        if (Double.isNaN(soilValue)) return "Tidak dapat dinilai — data belum ada";
        String cls;
        if (nutrient.equals("N")) cls = classifyN(soilValue);
        else if (nutrient.equals("P")) cls = classifyP(soilValue);
        else cls = classifyK(soilValue);
        if ("Rendah".equals(cls)) return "BELUM CUKUP / berpotensi membatasi fase ini";
        if ("Sedang".equals(cls)) return "CUKUP SEMENTARA, tetapi sesuaikan dengan target hasil dan fase";
        return "CUKUP–TINGGI; jangan menambah rutin tanpa dasar kebutuhan";
    }

    public static String classifyVpd(double vpd) {
        if (Double.isNaN(vpd)) return "Tidak ada data";
        if (vpd < 0.4) return "Sangat rendah";
        if (vpd <= 1.5) return "Umumnya aman";
        if (vpd <= 2.0) return "Waspada";
        return "Tinggi / berpotensi stress air";
    }

    public static double vpd(double t, double rh) {
        if (Double.isNaN(t) || Double.isNaN(rh)) return Double.NaN;
        double es = 0.6108 * Math.exp((17.27 * t) / (t + 237.3));
        return Math.max(0, es * (1.0 - rh / 100.0));
    }

    public static double gdd(double tAvg, double tBase) {
        if (Double.isNaN(tAvg) || Double.isNaN(tBase)) return Double.NaN;
        return Math.max(0, tAvg - tBase);
    }

    public static String weatherStatus(double t, double rh, double rain, double et0, double vpd, String crop) {
        CropProfile p = profile(crop);
        StringBuilder s = new StringBuilder();
        boolean bad = false;
        if (!Double.isNaN(t)) {
            if (t < p.tempMin) { s.append("suhu di bawah kisaran "); bad = true; }
            else if (t > p.tempMax) { s.append("suhu di atas kisaran "); bad = true; }
        }
        if (!Double.isNaN(vpd) && vpd > 2.0) { if (s.length() > 0) s.append("; "); s.append("VPD tinggi"); bad = true; }
        if (!Double.isNaN(rain) && rain > 80) { if (s.length() > 0) s.append("; "); s.append("hujan sangat tinggi"); bad = true; }
        if (!Double.isNaN(rh) && rh > 90) { if (s.length() > 0) s.append("; "); s.append("RH sangat tinggi"); bad = true; }
        if (!Double.isNaN(et0) && !Double.isNaN(rain) && rain < et0) { if (s.length() > 0) s.append("; "); s.append("hujan < ET0"); bad = true; }
        if (!bad) return "CUACA CENDERUNG SESUAI untuk " + p.name;
        return "CUACA PERLU DIWASPADAI: " + s;
    }

    public static double nutrientNeed(double soilValue, String nutrient, String crop, int hst, double recentCreditKgHa, double yieldTargetTPerHa) {
        return nutrientNeed(soilValue,nutrient,crop,hst,recentCreditKgHa,yieldTargetTPerHa,"",20,1.30);
    }

    public static double nutrientNeed(double soilValue, String nutrient, String crop, int hst, double recentCreditKgHa, double yieldTargetTPerHa, String method, double depthCm, double bulkDensityGcm3) {
        CropProfile p=profile(crop);
        double seasonal=nutrient.equals("N")?p.seasonalN:(nutrient.equals("P")?p.seasonalP:p.seasonalK);
        double yieldScale=1.0;
        if (Double.isFinite(yieldTargetTPerHa) && yieldTargetTPerHa>0) yieldScale=Math.max(0.5,Math.min(1.8,yieldTargetTPerHa/Math.max(0.1,defaultYield(p))));
        double stageNeed=seasonal*stageFraction(hst,crop)*yieldScale;
        double support=0; String cls;
        if (nutrient.equals("N")) cls=classifyN(soilValue); else if (nutrient.equals("P")) cls=classifyP(soilValue,method); else cls=classifyK(soilValue,method);
        if ("Rendah".equals(cls)) support=0.20;
        else if ("Sedang".equals(cls)) support=0.50;
        else if ("Tinggi".equals(cls)) support=0.80;
        double gross=stageNeed*(1.0-support);
        double recovery=nutrient.equals("N")?0.50:(nutrient.equals("P")?0.30:0.60);
        double need=Math.max(0,gross/Math.max(0.1,recovery)-Math.max(0,recentCreditKgHa));
        // The stock is reported separately; it is not treated as fully plant-available uptake.
        return need;
    }

    public static String nutrientFormulaText() {
        return "Kebutuhan fase ≈ kebutuhan musim × fraksi fase × skala target hasil × (1−dukungan soil-test) ÷ efisiensi pemulihan − kredit pupuk. Stok tanah = konsentrasi × BD × kedalaman × 0,10 kg/ha; stok bukan serapan tanaman. Model ini screening/STCR-style, bukan QUEFTS atau STCR terkalibrasi.";
    }

    private static double defaultYield(CropProfile p) {
        if (p.name.equals("Padi")) return 6;
        if (p.name.equals("Jagung")) return 7;
        if (p.name.equals("Kedelai")) return 2.5;
        if (p.name.equals("Tomat")) return 40;
        if (p.name.equals("Cabai")) return 8;
        return 5;
    }

    public static String limeAdvice(double ph, CropProfile p, String cultivation, double phBuffer, double alDd, double hDd, double cec, double omPct, double labLimeKgHa) {
        if (!Double.isFinite(ph)) return "pH belum diisi.";
        boolean organic=cultivation!=null&&norm(cultivation).contains("organik");
        String tail=organic?" Mode organik: utamakan bahan organik matang dan input yang diizinkan; jangan menganggap bahan organik otomatis menggantikan kebutuhan kapur.":"";
        if (ph<p.phMin) {
            if (Double.isFinite(labLimeKgHa) && labLimeKgHa>0) return "pH rendah. Ikuti kebutuhan kapur/dolomit hasil laboratorium: "+String.format(Locale.US,"%.1f",labLimeKgHa)+" kg/ha, dengan pilihan bahan disesuaikan Ca/Mg dan SOP."+tail;
            if (Double.isFinite(phBuffer)||Double.isFinite(alDd)||Double.isFinite(hDd)||Double.isFinite(cec)) return "pH rendah. Parameter buffer/kemasaman/CEC sudah tersedia; gunakan hasil uji untuk menghitung kebutuhan kapur, jangan dari pH tunggal."+tail;
            return "pH rendah. Belum aman menghitung dolomit dari pH saja. Tambahkan pH-buffer, Al-dd/H-dd, CEC, atau kebutuhan kapur hasil laboratorium."+tail;
        }
        if (ph>p.phMax) return "pH tinggi. Hentikan koreksi dengan kapur/dolomit. Periksa air irigasi/alkalinitas dan lakukan pengasaman hanya bila kebutuhan telah dihitung."+tail;
        return "pH berada dalam kisaran target komoditas. Pertahankan bahan organik dan pantau tren pH."+tail;
    }

    public static String amendmentAdvice(double ph, CropProfile p, String cultivation) {
        return limeAdvice(ph,p,cultivation,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN);
    }

    public static String optRisk(String crop, double t, double rh, double rain, double wind, String historyText, int hst) {
        String base = optRisk(crop,t,rh,rain,wind,historyText);
        String ph = phase(hst,crop);
        if (hst >= 0 && ph != null && !ph.contains("Belum")) return base + "\nFase tanaman: " + ph + ". Gunakan fase ini untuk menentukan bagian tanaman yang harus dipantau.";
        return base;
    }

    public static String optRisk(String crop, double t, double rh, double rain, double wind, String historyText) {
        String c = norm(crop);
        StringBuilder s = new StringBuilder();
        double score;
        if (c.contains("padi") || c.contains("rice")) {
            score = 0;
            if (!Double.isNaN(t) && t >= 20 && t <= 28) score += 30;
            if (!Double.isNaN(rh) && rh >= 80) score += 30;
            if (!Double.isNaN(rain) && rain >= 5) score += 20;
            if (!Double.isNaN(wind) && wind < 3.5) score += 10;
            s.append("Padi — Pyricularia oryzae (blas): ").append(level(score)).append(". ");
            if (score >= 50) s.append("Cuaca mendukung kelembapan infeksi; periksa bercak daun/malai dan tingkatkan scouting.");
            else s.append("Pantau bercak daun terutama setelah periode lembap/hujan.");
            s.append("\nPadi — Nilaparvata lugens (wereng batang cokelat): risiko awal ")
                    .append(level((!Double.isNaN(t) && t >= 25 && t <= 32 ? 45 : 15) + (!Double.isNaN(rh) && rh >= 70 ? 30 : 0)))
                    .append("; validasi dengan jumlah wereng per rumpun dan keberadaan musuh alami.");
            s.append("\nPadi — penggerek batang: pantau sundep/beluk dan telur/larva; keputusan pengendalian tetap berdasarkan pengamatan populasi.");
        } else if (c.contains("jagung") || c.contains("maize") || c.contains("corn")) {
            double faw = (!Double.isNaN(t) && t >= 26 && t <= 31 ? 50 : 20) + (!Double.isNaN(rain) && rain >= 5 ? 10 : 0);
            s.append("Jagung — Spodoptera frugiperda (fall armyworm): ").append(level(faw)).append(". ");
            if (faw >= 50) s.append("Suhu mendukung perkembangan; periksa pucuk/whorl dan frass setiap scouting.");
            else s.append("Tetap lakukan scouting pucuk, terutama tanaman muda.");
            double dm = (!Double.isNaN(rh) && rh >= 80 ? 35 : 0) + (!Double.isNaN(rain) && rain >= 10 ? 35 : 0) + (!Double.isNaN(t) && t >= 18 && t <= 28 ? 20 : 0);
            s.append("\nJagung — Peronosclerospora spp. (bulai): ").append(level(dm)).append("; verifikasi gejala belang/klorosis.");
        } else if (c.contains("cabai") || c.contains("chili") || c.contains("pepper")) {
            double anth = (!Double.isNaN(rh) && rh >= 80 ? 30 : 0) + (!Double.isNaN(rain) && rain >= 5 ? 35 : 0) + (!Double.isNaN(t) && t >= 24 && t <= 30 ? 25 : 0);
            s.append("Cabai — Colletotrichum spp. (antraknosa): ").append(level(anth)).append(". Periksa buah bercak cekung dan busuk, terutama setelah hujan/kelembapan tinggi.");
            double thrips = (!Double.isNaN(t) && t >= 25 && t <= 33 ? 35 : 15) + (!Double.isNaN(rh) && rh < 75 ? 20 : 5);
            s.append("\nCabai — Thrips spp.: ").append(level(thrips)).append("; periksa pucuk dan daun muda.");
        } else if (c.contains("tomat") || c.contains("tomato")) {
            double lb = (!Double.isNaN(t) && t >= 15 && t <= 26 ? 30 : 0) + (!Double.isNaN(rh) && rh >= 90 ? 35 : 0) + (!Double.isNaN(rain) && rain >= 5 ? 30 : 0);
            s.append("Tomat — Phytophthora infestans (hawar daun/later blight): ").append(level(lb)).append(". Waspadai cuaca sejuk-basah dengan kelembapan tinggi dan periode daun basah.");
            double ab = (!Double.isNaN(rh) && rh >= 90 ? 25 : 0) + (!Double.isNaN(rain) && rain >= 5 ? 25 : 0) + (!Double.isNaN(t) && t >= 20 && t <= 30 ? 25 : 0);
            s.append("\nTomat — Alternaria solani (early blight): ").append(level(ab)).append("; pantau bercak konsentris pada daun tua.");
        } else if (c.contains("kedelai") || c.contains("soy")) {
            double rust = (!Double.isNaN(rh) && rh >= 80 ? 35 : 0) + (!Double.isNaN(rain) && rain >= 5 ? 30 : 0) + (!Double.isNaN(t) && t >= 20 && t <= 28 ? 20 : 0);
            s.append("Kedelai — Phakopsora pachyrhizi (karat kedelai): ").append(level(rust)).append("; periksa pustula di bawah daun setelah periode lembap.");
            s.append("\nKedelai — hama pengisap/pemakan polong: risiko tidak dapat dipastikan hanya dari cuaca; masukkan populasi dan stadia ke catatan OPT.");
        } else {
            s.append("Komoditas belum memiliki model OPT lokal di mesin. Jangan menebak nama OPT hanya dari cuaca. Masukkan nama OPT + gejala + populasi/luas serangan pada Catatan Lapangan agar AI dapat mencocokkan pustaka.");
        }
        if (historyText != null && !historyText.trim().isEmpty()) {
            s.append("\nRiwayat lapang: ").append(historyText.trim());
        }
        return s.toString();
    }

    private static String level(double score) {
        if (score >= 75) return "TINGGI";
        if (score >= 50) return "SEDANG-TINGGI";
        if (score >= 30) return "SEDANG";
        return "RENDAH";
    }

    public static String evidenceBrief() { return AgronomyEvidence.brief(); }
    public static String evidenceCitations() { return AgronomyEvidence.citations(); }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.US).trim();
    }
}
