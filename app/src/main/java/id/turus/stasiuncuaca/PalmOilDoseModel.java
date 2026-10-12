package id.turus.stasiuncuaca;

import java.util.Locale;

/**
 * Transparent oil-palm nutrient-budget screening model.
 * Literature coefficients estimate nutrient input requirement per tonne FFB for a stated yield target.
 * This is NOT a locally calibrated fertilizer prescription; soil/organic/fertilizer credits and recovery
 * efficiencies must be known before converting nutrient demand to product rates.
 */
public final class PalmOilDoseModel {
    private PalmOilDoseModel() {}

    // Farrasati et al. (2026), estimated nutrient input requirements (kg nutrient per Mg FFB)
    // for yields approaching 75% of yield potential: N, P, K and Mg. P/K are elemental here.
    private static final double N_PER_T_FFB = 4.82;
    private static final double P_PER_T_FFB = 0.53;
    private static final double K_PER_T_FFB = 5.95;
    private static final double MG_PER_T_FFB = 0.72;

    public static String build(double actualYield, double targetYield, double palmsPerHa,
                               double leafN, double leafP, double leafK, double leafMg,
                               double sensorN, double sensorP, double sensorK,
                               double soilLabP, double soilLabK, int hst) {
        return build(actualYield, targetYield, palmsPerHa, leafN, leafP, leafK, leafMg,
                sensorN, sensorP, sensorK, soilLabP, soilLabK, hst, Double.NaN, "[]", "Kelapa Sawit", 1.30);
    }

    /**
     * Budget estimate with fertilizer-history credits. If yield is missing, a clearly-labelled
     * 15 t FFB/ha/year scenario is used so smallholders still receive a rough planning estimate.
     * History credits are counted only for dated, kg/ha entries from the last 365 days with a
     * known nutrient grade; liquid products are excluded because density/concentration basis is unknown.
     */
    public static String build(double actualYield, double targetYield, double palmsPerHa,
                               double leafN, double leafP, double leafK, double leafMg,
                               double sensorN, double sensorP, double sensorK,
                               double soilLabP, double soilLabK, int hst, double areaHa,
                               String fertilizerHistoryJson, String cropName) {
        return build(actualYield, targetYield, palmsPerHa, leafN, leafP, leafK, leafMg,
                sensorN, sensorP, sensorK, soilLabP, soilLabK, hst, areaHa,
                fertilizerHistoryJson, cropName, 1.30);
    }

    /**
     * Extended screening model. Bulk density is used only as a weak soil-type hint
     * (low values suggest peat); users can improve it by entering a measured value.
     */
    public static String build(double actualYield, double targetYield, double palmsPerHa,
                               double leafN, double leafP, double leafK, double leafMg,
                               double sensorN, double sensorP, double sensorK,
                               double soilLabP, double soilLabK, int hst, double areaHa,
                               String fertilizerHistoryJson, String cropName, double bulkDensity) {
        // Prefer measured actual yield over a target/scenario. Use target only when actual yield is missing.
        boolean useActualYield = finitePositive(actualYield);
        boolean useTargetYield = !useActualYield && finitePositive(targetYield);
        double yield = useActualYield ? actualYield : (useTargetYield ? targetYield : Double.NaN);
        boolean assumedYield = !finitePositive(yield);
        if (assumedYield) yield = 15.0;

        boolean peat = Double.isFinite(bulkDensity) && bulkDensity > 0 && bulkDensity <= 0.80;
        // Conservative heuristic recovery fractions for planning, NOT universal measured constants.
        // Mineral reference: N 35%, P 20%, K 50%, Mg 30%; peat scenario: N 25%, P 15%, K 35%, Mg 25%.
        // Site trials show K/Mg recovery varies with soil, rainfall, slope and moisture deficit.
        double effN = peat ? 0.25 : 0.35;
        double effP = peat ? 0.15 : 0.20;
        double effK = peat ? 0.35 : 0.50;
        double effMg = peat ? 0.25 : 0.30;
        // Coefficients from literature are the starting input benchmark. To make the soil scenario
        // useful when no lab exists, apply a relative correction against the mineral reference.
        // This is explicitly a heuristic, not a PPKS rate; lower assumed peat recovery raises the
        // planning estimate while preserving the published coefficient as the reference baseline.
        double n = yield * N_PER_T_FFB * (0.35 / effN);
        double p2o5 = yield * P_PER_T_FFB * 2.291 * (0.20 / effP);
        double k2o = yield * K_PER_T_FFB * 1.205 * (0.50 / effK);
        double mgo = yield * MG_PER_T_FFB * 1.658 * (0.30 / effMg);
        double[] credit = historyCredits(fertilizerHistoryJson, cropName, effN, effP, effK, effMg);
        double nGap = Math.max(0, n - credit[0]);
        double pGap = Math.max(0, p2o5 - credit[1]);
        double kGap = Math.max(0, k2o - credit[2]);
        double mgGap = Math.max(0, mgo - credit[3]);
        StringBuilder out = new StringBuilder();
        out.append("MODEL KEBUTUHAN HARA SAWIT — ESTIMASI AWAL, DATA TERSEDIA\n");
        out.append("Basis hasil: ").append(fmt(yield)).append(" ton TBS/ha/tahun");
        if (assumedYield) out.append(" (ASUMSI SEMENTARA karena hasil aktual dan target belum diisi)");
        else if (useActualYield) out.append(" (DATA AKTUAL; diprioritaskan di atas target)");
        else out.append(" (target sementara karena hasil aktual belum tersedia)");
        out.append(".\n");
        if (assumedYield) out.append("Asumsi 15 ton/ha/tahun dipakai hanya agar ada gambaran awal; ganti dengan hasil kebun bila tersedia. Jika tanaman TBM, jangan gunakan skenario TM ini sebagai dosis TBM.\n");
        out.append("Estimasi kebutuhan/input hara bruto setelah koreksi efisiensi asumsi tanah: N ").append(fmt(n)).append(" kg N/ha/tahun; P₂O₅ ")
                .append(fmt(p2o5)).append("; K₂O ").append(fmt(k2o)).append("; MgO ").append(fmt(mgo)).append(" kg/ha/tahun.\n");
        out.append("JENIS TANAH UNTUK ESTIMASI: ").append(peat ? "gambut (indikasi BD ≤0,80 g/cm³)" : "mineral / default (BD >0,80 atau belum terukur)")
                .append("; BD input ").append(value(bulkDensity)).append(" g/cm³. Jenis tanah sebaiknya dikonfirmasi pengguna.\n");
        out.append("ASUMSI PEMULIHAN HARA PUPUK (bukan angka baku universal; estimasi produk dikoreksi relatif terhadap skenario referensi mineral): N ").append(fmt(effN*100))
                .append("%; P ").append(fmt(effP*100)).append("%; K ").append(fmt(effK*100)).append("%; Mg ").append(fmt(effMg*100))
                .append("%. Asumsi konservatif dibedakan untuk mineral/gambut; kondisi drainase, hujan, tekstur, lereng, cara dan waktu aplikasi dapat mengubah efisiensi.\n");
        out.append("KREDIT RIWAYAT PUPUK 365 HARI (hara efektif = dosis × kadar hara × asumsi pemulihan): N ")
                .append(fmt(credit[0])).append("; P₂O₅ ").append(fmt(credit[1])).append("; K₂O ").append(fmt(credit[2]))
                .append("; MgO ").append(fmt(credit[3])).append(" kg/ha.\n");
        if (credit[4] > 0) out.append("Catatan: kadar hara diperkirakan dari nama/grade produk pada ").append((int)credit[4])
                .append(" entri riwayat; cek label pupuk, angka ini bersifat asumsi.\n");
        out.append("PERKIRAAN KEBUTUHAN HARA BELUM TERCUKUPI SETELAH KREDIT RIWAYAT: N ").append(fmt(nGap))
                .append(" kg/ha; P₂O₅ ").append(fmt(pGap)).append(" kg/ha; K₂O ").append(fmt(kGap)).append(" kg/ha; MgO ")
                .append(fmt(mgGap)).append(" kg/ha.\n");
        out.append("Setara produk ilustratif, bila memakai pupuk berkadar standar: urea 46% ≈ ").append(fmt(nGap / 0.46))
                .append(" kg/ha; SP-36 ≈ ").append(fmt(pGap / 0.36)).append(" kg/ha; KCl 60% K₂O ≈ ")
                .append(fmt(kGap / 0.60)).append(" kg/ha; sumber Mg 27% MgO ≈ ").append(fmt(mgGap / 0.27))
                .append(" kg/ha. Ini pilihan ekuivalen masing-masing unsur, BUKAN untuk dijumlahkan/diaplikasikan sekaligus; pilih pupuk majemuk atau kombinasi agar seimbang.\n");        if (finitePositive(areaHa)) {
            out.append("Perkiraan setara untuk seluruh luas ").append(fmt(areaHa)).append(" ha: urea ").append(fmt(nGap / 0.46 * areaHa))
                    .append(" kg; SP-36 ").append(fmt(pGap / 0.36 * areaHa)).append(" kg; KCl ").append(fmt(kGap / 0.60 * areaHa))
                    .append(" kg; sumber Mg 27% MgO ").append(fmt(mgGap / 0.27 * areaHa)).append(" kg. Angka ini tetap ekuivalen terpisah, bukan paket aplikasi sekaligus.\n");
        } else out.append("Isi luas kebun (ha) untuk melihat ekuivalen jumlah produk bagi seluruh kebun.\n");

        if (finitePositive(palmsPerHa)) {
            out.append("Setara kebutuhan tersisa per pokok (asumsi ").append(fmt(palmsPerHa)).append(" pokok hidup/ha): N ")
                    .append(fmt(nGap * 1000 / palmsPerHa)).append(" g; P₂O₅ ").append(fmt(pGap * 1000 / palmsPerHa))
                    .append(" g; K₂O ").append(fmt(kGap * 1000 / palmsPerHa)).append(" g/pokok/tahun.\n");
        } else out.append("Isi jumlah pokok hidup/ha untuk mengubah estimasi menjadi per pokok.\n");

        out.append("Pemeriksaan daun (nilai lab % bahan kering; patokan awal bukan norma universal): ");
        boolean anyLeaf = false;
        anyLeaf |= leafStatus(out, "N", leafN, 2.50);
        anyLeaf |= leafStatus(out, "P", leafP, 0.15);
        anyLeaf |= leafStatus(out, "K", leafK, 1.00);
        anyLeaf |= leafStatus(out, "Mg", leafMg, Double.NaN);
        if (!anyLeaf) out.append("belum ada data daun; estimasi tetap berjalan dari hasil/asumsi dan riwayat pupuk.");
        out.append("\nPemeriksaan silang tanah: P tersedia lab ").append(value(soilLabP)).append(" mg/kg; K-dd ")
                .append(value(soilLabK)).append(" cmol(+)/kg. Tanpa metode uji dan kalibrasi lokal, nilai ini tidak dikurangi langsung dari kebutuhan.")
                .append("\nSensor N/P/K (N ").append(value(sensorN)).append("; P ").append(value(sensorP)).append("; K ").append(value(sensorK))
                .append(" mg/kg) digunakan sebagai petunjuk kasar/skrining, bukan konversi langsung menjadi kredit kg pupuk.\n");
        out.append("Fase: ").append(hst < 0 ? "umur belum diisi" : hst < 365 ? "TBM awal" : hst < 1095 ? "TBM lanjut" : "TM")
                .append(". Angka di atas berbasis hasil TBS dan lebih sesuai sebagai skenario tanaman menghasilkan (TM); kebutuhan TBM harus disesuaikan umur, ukuran tanaman dan rekomendasi setempat.\n");
        out.append("Cara membaca: riwayat pupuk bertanggal dalam 365 hari terakhir dihitung bila dosis kg/ha dan kadar hara diketahui atau bisa diperkirakan dari nama produk; hasilnya dikalikan asumsi pemulihan. Entri L/ha, tanpa tanggal, atau komoditas lain tidak dikreditkan. Pupuk organik/kompos, janjang kosong, abu, limbah PKS dan pupuk cair belum dikreditkan otomatis karena kadar, kadar air, laju mineralisasi dan dosis aktual belum tersedia. Jika datanya tersedia, masukkan sebagai produk/analisis hara yang terukur agar tidak mengarang kredit hara.\n");
        out.append("BEST PRACTICE: bagi pupuk menjadi 2–4 aplikasi/tahun sesuai hujan dan akses kebun; hindari aplikasi sebelum hujan sangat lebat atau saat genangan; sebar di zona akar aktif sesuai umur/pedoman lokal, jangan menumpuk di pangkal batang; pertahankan tanaman penutup tanah/legum, kendalikan erosi, kelola pelepah dan residu organik secara tepat, jaga drainase dan muka air gambut tanpa mengeringkan gambut, serta catat produk, tanggal, dosis, lokasi dan kondisi hujan. Dolomit tidak dihitung dari pH saja; gambut perlu perhatian khusus pada drainase, K, Cu/B dan keseimbangan hara.\n");
        out.append("DASAR ILMIAH: koefisien input hara N/P/K/Mg per ton TBS mengacu Farrasati et al. (2026), European Journal of Agronomy, artikel 128054. Perbedaan pemulihan K/Mg antarlokasi didukung Prabowo, Foster & Nelson (2023), Nutrient Cycling in Agroecosystems 126:263–278, DOI 10.1007/s10705-023-10289-7. Persentase pemulihan yang tampil di aplikasi adalah asumsi perencanaan konservatif untuk kondisi tanpa uji lokal, bukan angka yang diklaim berasal langsung dari kedua publikasi tersebut.\n");
        out.append("Peringatan: hasil ini bukan dosis resmi PPKS/IOPRI. Nilai tanah dan daun yang tersedia hanya dipakai sebagai pemeriksaan silang karena metode dan kalibrasi lokal belum tentu diketahui. Prioritaskan rekomendasi PPKS/IOPRI atau penyuluh bila tersedia; jangan menerapkan seluruh ekuivalen pupuk sekaligus.");
        return out.toString();
    }

    private static double[] historyCredits(String json, String cropName,
                                           double effN, double effP, double effK, double effMg) {
        double n = 0, p = 0, k = 0, mg = 0, inferredCount = 0;
        try {
            org.json.JSONArray a = new org.json.JSONArray(json == null ? "[]" : json);
            java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Jakarta"));
            java.time.LocalDate cutoff = today.minusDays(365);
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONObject o = a.optJSONObject(i);
                if (o == null || !"kg/ha".equalsIgnoreCase(o.optString("unit", ""))) continue;
                String savedCrop = o.optString("crop", "").trim();
                if (savedCrop.isEmpty() || cropName == null || cropName.trim().isEmpty() ||
                        !savedCrop.equalsIgnoreCase(cropName.trim())) continue;
                java.time.LocalDate date;
                try { date = java.time.LocalDate.parse(o.optString("date", "")); }
                catch (Exception ignored) { continue; }
                if (date.isBefore(cutoff) || date.isAfter(today)) continue;
                double dose = o.optDouble("dose", 0);
                if (!finitePositive(dose)) continue;
                double nPct = Math.max(0, o.optDouble("nPct", 0));
                double pPct = Math.max(0, o.optDouble("pPct", 0));
                double kPct = Math.max(0, o.optDouble("kPct", 0));
                double mgPct = Math.max(0, o.optDouble("mgPct", 0)); // expressed as MgO percent
                boolean inferred = nPct == 0 && pPct == 0 && kPct == 0 && mgPct == 0;
                if (inferred) {
                    double[] grade = inferGrade(o.optString("product", ""));
                    nPct = grade[0]; pPct = grade[1]; kPct = grade[2]; mgPct = grade[4];
                    if (grade[3] > 0) inferredCount++;
                }
                n += dose * nPct / 100.0 * effN;
                p += dose * pPct / 100.0 * effP;
                k += dose * kPct / 100.0 * effK;
                mg += dose * mgPct / 100.0 * effMg;
            }
        } catch (Exception ignored) { }
        return new double[]{n, p, k, mg, inferredCount};
    }

    /** Returns N%, P2O5%, K2O%, inferred flag, and MgO%; product grade is a rough label-based inference. */
    private static double[] inferGrade(String rawName) {
        String name = rawName == null ? "" : rawName.toLowerCase(Locale.ROOT).replace(',', '.');
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2})\\s*[-+/]\\s*(\\d{1,2})\\s*[-+/]\\s*(\\d{1,2})").matcher(name);
        if (m.find()) return new double[]{parseGrade(m.group(1)), parseGrade(m.group(2)), parseGrade(m.group(3)), 1, 0};
        if (name.contains("urea") || name.contains("ureum")) return new double[]{46, 0, 0, 1, 0};
        if (name.contains("sp-36") || name.contains("sp 36")) return new double[]{0, 36, 0, 1, 0};
        if (name.contains("tsp")) return new double[]{0, 46, 0, 1, 0};
        if (name.contains("kcl") || name.contains("mop") || name.contains("kalium klorida")) return new double[]{0, 0, 60, 1, 0};
        if (name.contains("za") || name.contains("ammonium sulfate") || name.contains("amonium sulfat")) return new double[]{21, 0, 0, 1, 0};
        if (name.contains("kieserite") || name.contains("kiserit")) return new double[]{0, 0, 0, 1, 27};
        if (name.contains("dolomit") || name.contains("dolomite")) return new double[]{0, 0, 0, 1, 18};
        return new double[]{0, 0, 0, 0, 0};
    }
    private static double parseGrade(String value) { try { return Double.parseDouble(value); } catch (Exception ignored) { return 0; } }

    private static boolean leafStatus(StringBuilder out, String name, double value, double provisionalCritical) {
        if (!Double.isFinite(value) || value <= 0) return false;
        if (out.charAt(out.length() - 1) != ' ') out.append("; ");
        out.append(name).append(' ').append(fmt(value)).append('%');
        if (Double.isFinite(provisionalCritical)) {
            out.append(value < provisionalCritical ? " di bawah patokan awal " : " dibanding patokan awal ")
                    .append(fmt(provisionalCritical)).append('%');
        } else out.append("; interpretasi perlu norma lab/lokasi");
        return true;
    }
    private static boolean finitePositive(double x) { return Double.isFinite(x) && x > 0; }
    private static String value(double x) { return Double.isFinite(x) ? fmt(x) : "belum diisi"; }
    private static String fmt(double x) { return String.format(Locale.US, "%.2f", x); }
}
