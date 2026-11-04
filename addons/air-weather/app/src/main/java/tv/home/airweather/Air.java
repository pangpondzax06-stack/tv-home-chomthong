package tv.home.airweather;

/**
 * Thailand's PM2.5 bands (Pollution Control Department, in force since June 2023) and the distance to an Air4Thai
 * station. No Android code here, so src/test checks it on the plain JVM.
 */
final class Air {
    /** Upper bound of the first four bands in µg/m³ (24-hour scale, as Air4Thai colours its hourly readings). */
    static final double[] PM25_MAX = {15.0, 25.0, 37.5, 75.0};
    /**
     * Air4Thai's colours: very good, good, moderate, starting to affect health, affects health. The yellow is a little
     * softer than Air4Thai's pure #FFFF00, which glares on a TV.
     */
    private static final int[] COLOUR = {0xFF3BCCFF, 0xFF92D050, 0xFFFFE14D, 0xFFFFA200, 0xFFF04646};

    private Air() { }

    /** 0 (very good) to 4 (affects health). */
    static int band(double pm25) {
        int b = 0;
        while (b < PM25_MAX.length && pm25 > PM25_MAX[b]) b++;
        return b;
    }

    static int colour(double pm25) {
        return COLOUR[band(pm25)];
    }

    /** Dark text on the four light bands, white on the red one. */
    static int textColour(double pm25) {
        return band(pm25) == 4 ? 0xFFFFFFFF : 0xFF1A1A1A;
    }

    /** Correction factors outside this range mean a broken reading, not a model that far off. */
    static final double MIN_FACTOR = 0.25, MAX_FACTOR = 4.0;
    /** Hours for the correction to fade halfway back to 1: a station's lead over the model lasts hours, not days. */
    static final double FADE_HALF_LIFE_H = 12.0;

    /**
     * How far the CAMS model is off where the station is, as a factor: (station + 2) / (model + 2) at the station's
     * hour, kept between {@link #MIN_FACTOR} and {@link #MAX_FACTOR}. The +2 µg/m³ keeps a near-zero model hour
     * from blowing the factor up. CAMS reads low over Thai cities, most in the burning season, so this is usually 1.5-3.
     */
    static double correction(double station, double model) {
        double f = (station + 2) / (model + 2);
        return Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, f));
    }

    /**
     * The model's PM2.5 for an hour ahead, scaled by the factor; the factor fades halfway back to 1 every
     * {@link #FADE_HALF_LIFE_H} hours, so the next hour follows the station and the far end leans on the model.
     */
    static double adjust(double model, double factor, int hoursAhead) {
        double kept = Math.pow(0.5, Math.max(0, hoursAhead) / FADE_HALF_LIFE_H);
        return model * (1 + (factor - 1) * kept);
    }

    /** Great-circle distance in km. */
    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** A box around Thailand: only there is Air4Thai worth asking. The 50 km station limit does the rest. */
    static boolean inThailand(double lat, double lon) {
        return lat >= 5.4 && lat <= 20.6 && lon >= 97.2 && lon <= 105.8;
    }

    /** "18.7" from Air4Thai, or NaN for its "-1", "-999", "N/A" and empty values. */
    static double reading(String value) {
        try {
            double v = Double.parseDouble(value.trim());
            return v >= 0 ? v : Double.NaN;
        } catch (NumberFormatException | NullPointerException e) {
            return Double.NaN;
        }
    }
}
