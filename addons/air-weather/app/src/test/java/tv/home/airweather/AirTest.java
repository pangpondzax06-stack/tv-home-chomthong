package tv.home.airweather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AirTest {
    @Test
    public void bandsFollowThePcdScale() {
        assertEquals(0, Air.band(0));
        assertEquals(0, Air.band(15.0));
        assertEquals(1, Air.band(15.1));
        assertEquals(1, Air.band(18.7));     // Air4Thai coloured this reading "good" (color_id 2)
        assertEquals(2, Air.band(37.5));
        assertEquals(3, Air.band(37.6));
        assertEquals(3, Air.band(75.0));
        assertEquals(4, Air.band(75.1));
        assertEquals(4, Air.band(400));
    }

    @Test
    public void onlyTheRedBandHasWhiteText() {
        assertEquals(0xFF1A1A1A, Air.textColour(10));
        assertEquals(0xFF1A1A1A, Air.textColour(60));
        assertEquals(0xFFFFFFFF, Air.textColour(90));
    }

    @Test
    public void distanceBangkokToChiangMai() {
        double km = Air.distanceKm(13.7540, 100.5014, 18.7883, 98.9853);
        assertTrue(km > 570 && km < 590);
    }

    @Test
    public void thailandBox() {
        assertTrue(Air.inThailand(13.75, 100.50));     // Bangkok
        assertTrue(Air.inThailand(6.54, 101.28));      // Yala
        assertFalse(Air.inThailand(1.35, 103.82));     // Singapore
        assertFalse(Air.inThailand(35.68, 139.69));    // Tokyo
    }

    @Test
    public void correctionIsTheStationOverTheModel() {
        // Bangkok, 25 Oct 01:00: Phra Nakhon measured 18.7 where CAMS had 7.9.
        assertEquals(20.7 / 9.9, Air.correction(18.7, 7.9), 1e-9);
        assertEquals(1.0, Air.correction(10, 10), 1e-9);
        assertEquals(Air.MAX_FACTOR, Air.correction(300, 0.1), 1e-9);   // a station spike, not a 150x model error
        assertEquals(Air.MIN_FACTOR, Air.correction(0, 60), 1e-9);
    }

    @Test
    public void correctionFadesWithTheHours() {
        double f = 2.0;
        assertEquals(20.0, Air.adjust(10, f, 0), 1e-9);              // the station's hour: fully corrected
        assertEquals(15.0, Air.adjust(10, f, 12), 1e-9);             // half the correction left after 12 hours
        assertTrue(Air.adjust(10, f, 1) > Air.adjust(10, f, 6));
        assertEquals(10.0, Air.adjust(10, 1.0, 5), 1e-9);            // no correction: the model as it is
        assertEquals(20.0, Air.adjust(10, f, -3), 1e-9);             // hours before the station's count as zero
    }

    @Test
    public void air4ThaiMissingValues() {
        assertEquals(18.7, Air.reading("18.7"), 1e-9);
        assertTrue(Double.isNaN(Air.reading("-1")));
        assertTrue(Double.isNaN(Air.reading("-999")));
        assertTrue(Double.isNaN(Air.reading("N/A")));
        assertTrue(Double.isNaN(Air.reading("")));
        assertTrue(Double.isNaN(Air.reading(null)));
    }
}
