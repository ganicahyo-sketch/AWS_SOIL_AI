package id.turus.stasiuncuaca;

import java.util.Locale;

/** Estimasi N-total dari N tersedia sensor dengan asumsi N tersedia 1–5% N-total. */
public final class NitrogenInference {
    private NitrogenInference() {}
    public static String estimateTotalN(double availableMgKg) {
        if (!Double.isFinite(availableMgKg) || availableMgKg < 0) return "N tersedia belum ada.";
        double minPct = availableMgKg / 500.0;
        double maxPct = availableMgKg / 100.0;
        double midPct = availableMgKg / 300.0;
        return String.format(Locale.US,"%.3f–%.3f%% N-total; titik tengah asumsi 3%% ≈ %.3f%%.",minPct,maxPct,midPct);
    }
    public static String classifyRange(double availableMgKg) {
        if (!Double.isFinite(availableMgKg) || availableMgKg < 0) return "Belum dapat diprediksi";
        double minPct = availableMgKg / 500.0;
        double maxPct = availableMgKg / 100.0;
        StringBuilder s = new StringBuilder();
        if (minPct < 0.10) s.append("Sangat Rendah");
        if (maxPct >= 0.10 && minPct <= 0.20) append(s,"Rendah");
        if (maxPct >= 0.21 && minPct <= 0.50) append(s,"Sedang");
        if (maxPct >= 0.51 && minPct <= 0.75) append(s,"Tinggi");
        if (maxPct > 0.75) append(s,"Sangat Tinggi");
        return s.length()==0?"Di luar rentang kelas":s.toString();
    }
    private static void append(StringBuilder b,String v){if(b.length()>0)b.append(" / ");b.append(v);}
}
