package id.turus.stasiuncuaca;

/** Konversi estimasi radiasi shortwave global menjadi PAR dan PPFD. */
public final class LightConversion {
    private LightConversion() {}
    public static double shortwaveToParWm2(double shortwaveWm2) {
        if (!Double.isFinite(shortwaveWm2) || shortwaveWm2 < 0) return Double.NaN;
        return shortwaveWm2 * 0.45;
    }
    public static double parToPpfd(double parWm2) {
        if (!Double.isFinite(parWm2) || parWm2 < 0) return Double.NaN;
        return parWm2 * 4.57;
    }
    public static double shortwaveToPpfd(double shortwaveWm2) {
        return parToPpfd(shortwaveToParWm2(shortwaveWm2));
    }
}
