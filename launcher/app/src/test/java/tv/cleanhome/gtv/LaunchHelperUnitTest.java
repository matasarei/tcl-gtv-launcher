package tv.cleanhome.gtv;

import org.junit.Test;
import static org.junit.Assert.*;

public class LaunchHelperUnitTest {

    @Test
    public void testYouTubePackagesOrder() {
        assertNotNull(LaunchHelper.YOUTUBE_PACKAGES);
        assertTrue("At least one YouTube package", LaunchHelper.YOUTUBE_PACKAGES.length >= 4);
        assertEquals("com.google.android.youtube.tv", LaunchHelper.PKG_YOUTUBE);
        assertEquals("First priority should be official YouTube TV", "com.google.android.youtube.tv", LaunchHelper.YOUTUBE_PACKAGES[0]);
        assertEquals("Second priority should be SmartTube video manager", "com.liskovsoft.videomanager", LaunchHelper.YOUTUBE_PACKAGES[1]);
        assertEquals("Third priority should be SmartTube beta", "com.liskovsoft.smarttubetv.beta", LaunchHelper.YOUTUBE_PACKAGES[2]);
        assertEquals("Fourth priority should be SmartTube stable", "com.liskovsoft.smarttubetv", LaunchHelper.YOUTUBE_PACKAGES[3]);
    }
}
