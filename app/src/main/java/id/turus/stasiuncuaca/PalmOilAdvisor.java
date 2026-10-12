package id.turus.stasiuncuaca;

import java.util.Locale;

/** Oil-palm-specific advisory layer. Sensor results are screening inputs, not a PPKS lab prescription. */
public final class PalmOilAdvisor {
    private PalmOilAdvisor() {}

    public static boolean isOilPalm(String crop) {
        String c = crop == null ? "" : crop.toLowerCase(Locale.ROOT);
        return c.contains("sawit") || c.contains("kelapa sawit") || c.contains("oil palm");
    }

    public static String build(String crop, int hst, double n, double p, double k,
                               double ecUsCm, double ph, double moisturePct,
                               double rainMm, double rhPct) {
        if (!isOilPalm(crop)) return "";
        StringBuilder s = new StringBuilder();
        String phase = hst < 0 ? "umur belum diisi" : hst < 365 ? "TBM 0–1 tahun" : hst < 1095 ? "TBM 1–3 tahun" : "TM / menghasilkan";
        s.append("KELAPA SAWIT — REKOMENDASI SPESIFIK KOMODITAS\n");
        s.append("Kelompok fase: ").append(phase).append(". Sesuaikan kembali dengan umur setelah tanam, jumlah pokok hidup, jenis tanah, produktivitas, dan riwayat pemupukan.\n");
        s.append("Interpretasi sensor (skrining, bukan rekomendasi dosis resmi PPKS):\n");
        appendNutrient(s, "N tersedia", n);
        appendNutrient(s, "P tersedia", p);
        appendNutrient(s, "K tersedia", k);
        if (Double.isFinite(ph)) {
            if (ph < 4.0) s.append("• pH ").append(fmt(ph)).append(" sangat masam: verifikasi dengan metode laboratorium; cek Al-dd, H-dd, KTK, Ca/Mg dan kebutuhan kapur sebelum menetapkan dolomit.\n");
            else if (ph < 4.5) s.append("• pH ").append(fmt(ph)).append(" masam: evaluasi kemasaman dan status Ca/Mg; jangan menentukan dosis dolomit dari pH saja.\n");
            else if (ph <= 6.0) s.append("• pH ").append(fmt(ph)).append(" berada pada kisaran masam yang lazim dijumpai di kebun sawit; nilai kecukupan hara tetap ditentukan bersama hasil daun/tanah dan pertumbuhan.\n");
            else if (ph <= 6.5) s.append("• pH ").append(fmt(ph)).append(" agak tinggi untuk sebagian tanah kebun sawit; periksa ketersediaan mikrohara dan jangan menambah kapur tanpa dasar.\n");
            else s.append("• pH ").append(fmt(ph)).append(" tinggi untuk banyak tanah kebun sawit: konfirmasi pengukuran, alkalinitas dan ketersediaan mikrohara sebelum tindakan koreksi.\n");
        } else s.append("• pH belum tersedia; ukur/konfirmasi sebelum keputusan koreksi kemasaman.\n");
        if (Double.isFinite(ecUsCm)) {
            if (ecUsCm >= 4000) s.append("• EC ").append(fmt(ecUsCm)).append(" µS/cm: sinyal EC tinggi pada pembacaan sensor; ulangi pada kelembapan dan prosedur yang konsisten, periksa kualitas air/drainase dan konfirmasi ECe laboratorium sebelum menyimpulkan salinitas.\n");
            else if (ecUsCm >= 2000) s.append("• EC ").append(fmt(ecUsCm)).append(" µS/cm: pantau tren dan hindari pupuk pekat sekaligus; EC sensor tidak setara langsung dengan ECe.\n");
            else s.append("• EC ").append(fmt(ecUsCm)).append(" µS/cm: tetap catat tren; EC rendah tidak otomatis berarti NPK cukup.\n");
        } else s.append("• EC belum tersedia; isi EC dalam µS/cm bila sensor mendukung.\n");
        s.append("Prioritas pemupukan: gunakan status N, P, K sensor untuk menentukan unsur yang perlu diperiksa lebih lanjut, bukan mengonversi langsung mg/kg menjadi kg pupuk/pokok. NPK sensor USB 8-in-1 dan 7-in-1 CWT dapat berbeda metode/kalibrasi; simpan merek/model, satuan, kedalaman, kelembapan dan lokasi sampel.\n");
        s.append("Untuk dosis pupuk kelapa sawit yang dapat dipertanggungjawabkan, gabungkan analisis daun (terutama status hara), analisis tanah, umur/fase, target hasil, jumlah pokok/ha, jenis tanah, produktivitas dan riwayat pupuk. Rekomendasi layanan PPKS mencakup sampel tanah dan daun serta evaluasi pertumbuhan/kultur teknis; angka sensor saja tidak cukup untuk menyebut dosis sebagai rekomendasi resmi PPKS.\n");
        s.append("Tindakan teknis: (1) petakan blok dan ambil sampel representatif; (2) catat jumlah pokok hidup dan gejala defisiensi; (3) cek pelepah, pertumbuhan, tandan dan riwayat hasil; (4) pertahankan piringan dan jalur panen sesuai praktik setempat tanpa merusak akar; (5) kelola pelepah/sisa organik dan penutup tanah; (6) periksa drainase/genangan serta konservasi air; (7) pecah aplikasi pupuk sesuai rekomendasi tervalidasi dan prakiraan hujan, hindari aplikasi menjelang hujan lebat atau pada tanah tergenang.\n");
        if (Double.isFinite(moisturePct) && moisturePct < 20) s.append("• Kelembapan sensor rendah (").append(fmt(moisturePct)).append("%): periksa kelembapan zona akar dan kondisi musim sebelum aplikasi pupuk.\n");
        if (Double.isFinite(moisturePct) && moisturePct > 85) s.append("• Kelembapan sensor tinggi (").append(fmt(moisturePct)).append("%): cek genangan/drainase dan tunda aplikasi bila berisiko hanyut/tercuci.\n");
        if (Double.isFinite(rainMm) && rainMm >= 20) s.append("• Hujan ").append(fmt(rainMm)).append(" mm/hari: evaluasi tunda pemupukan sampai kondisi lapangan aman untuk mengurangi kehilangan hara.\n");
        if (Double.isFinite(rhPct) && rhPct >= 90) s.append("• RH tinggi: lakukan inspeksi rutin daun, pucuk, pangkal pelepah dan tandan; cuaca saja tidak cukup untuk mendiagnosis OPT.\n");
        s.append("Dosis numerik final: perlu data analisis daun/tanah, jumlah pokok/ha dan rekomendasi PPKS/spesifik lokasi. Jangan menafsirkan skrining sensor sebagai resep pupuk resmi.\n");
        s.append("Acuan: PPKS/IOPRI — layanan Rekomendasi Pemupukan TBM dan TM; layanan tersebut menggunakan analisis sampel tanah dan daun serta evaluasi pertumbuhan dan kultur teknis.");
        return s.toString();
    }

    private static void appendNutrient(StringBuilder s, String name, double v) {
        s.append("• ").append(name).append(": ");
        if (!Double.isFinite(v) || v < 0) { s.append("belum diisi.\n"); return; }
        s.append(fmt(v)).append(" mg/kg; gunakan sebagai indikasi relatif. ");
        if (v < 10) s.append("prioritaskan konfirmasi status hara dan gejala defisiensi.");
        else if (v < 25) s.append("bandingkan dengan metode uji, jenis tanah dan hasil daun sebelum menentukan tambahan pupuk.");
        else s.append("jangan menambah dosis hanya dari angka ini; cek kebutuhan tanaman dan metode pengukuran.");
        s.append("\n");
    }
    private static String fmt(double v) { return String.format(Locale.US, "%.2f", v); }
}
