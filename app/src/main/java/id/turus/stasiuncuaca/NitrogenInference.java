package id.turus.stasiuncuaca;

import java.util.Locale;

/**
 * Screening estimate of total soil N from sensor-available N.
 * The app deliberately shows one midpoint estimate (3% of total N) rather than
 * a very wide 1–5% derived range, because the sensor is not a calibrated laboratory method.
 */
public final class NitrogenInference {
    private NitrogenInference() {}

    /** Midpoint assumption: available N is approximately 3% of total N. */
    public static double estimateTotalNPercent(double availableMgKg) {
        if (!Double.isFinite(availableMgKg) || availableMgKg < 0) return Double.NaN;
        return availableMgKg / 300.0;
    }

    public static String estimateTotalN(double availableMgKg) {
        double midPct = estimateTotalNPercent(availableMgKg);
        if (!Double.isFinite(midPct)) return "N tersedia belum ada.";
        return String.format(Locale.US, "≈ %.3f%% N-total (estimasi titik tengah; asumsi N tersedia ≈ 3%% N-total)", midPct);
    }

    /** One class only, based on the midpoint estimate; no overlapping 3-range output. */
    public static String classifyRange(double availableMgKg) {
        double midPct = estimateTotalNPercent(availableMgKg);
        if (!Double.isFinite(midPct)) return "Belum dapat diprediksi";
        if (midPct < 0.10) return "Sangat Rendah";
        if (midPct < 0.20) return "Rendah";
        if (midPct < 0.50) return "Sedang";
        if (midPct < 0.75) return "Tinggi";
        return "Sangat Tinggi";
    }

    /**
     * Conservative supplementary N screening factor. It is intentionally not a
     * direct conversion from mg/kg to fertilizer dose.
     */
    public static double supportFactor(double availableMgKg) {
        String c = classifyRange(availableMgKg);
        if ("Sangat Rendah".equals(c)) return 0.00;
        if ("Rendah".equals(c)) return 0.20;
        if ("Sedang".equals(c)) return 0.50;
        if ("Tinggi".equals(c)) return 0.80;
        if ("Sangat Tinggi".equals(c)) return 1.00;
        return Double.NaN;
    }
}
