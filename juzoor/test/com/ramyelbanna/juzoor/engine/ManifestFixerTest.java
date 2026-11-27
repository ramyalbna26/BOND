package com.ramyelbanna.juzoor.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for the manifest repair pass. These values come from real binary
 * manifests: aapt2 rejects the numeric forms, so each case must be converted to
 * the symbolic Android form used by Android Studio.
 */
public class ManifestFixerTest {

    @Test
    public void configChangesFlagsAreNamed() {
        String out = ManifestFixer.fix("<activity android:configChanges=\"0x6a0\" />", null);
        assertEquals("<activity android:configChanges=\"keyboardHidden|orientation|uiMode|screenSize\" />", out);
    }

    @Test
    public void severalComponentsKeepTheirOwnFlags() {
        String xml = "<a android:configChanges=\"0x80\" /><b android:configChanges=\"0x200\" />";
        String out = ManifestFixer.fix(xml, null);
        assertTrue(out.contains("android:configChanges=\"orientation\""));
        assertTrue(out.contains("android:configChanges=\"uiMode\""));
    }

    @Test
    public void softInputAndGravityAreSymbolic() {
        String xml = "<activity android:windowSoftInputMode=\"0x10\" "
                + "android:gravity=\"0x11\" android:layout_width=\"-1\" "
                + "android:layout_height=\"0xffffffff\" />";
        String out = ManifestFixer.fix(xml, null);
        assertTrue(out.contains("windowSoftInputMode=\"stateUnspecified|adjustResize\""));
        assertTrue(out.contains("gravity=\"center\""));
        assertTrue(out.contains("layout_width=\"match_parent\""));
        assertTrue(out.contains("layout_height=\"match_parent\""));
    }

    @Test
    public void booleansAndEnumsAreNamed() {
        String xml = "<application android:allowBackup=\"0x0\" android:enabled=\"0x1\" />"
                + "<activity android:screenOrientation=\"0x1\" android:launchMode=\"0x2\" />";
        String out = ManifestFixer.fix(xml, null);
        assertTrue(out.contains("allowBackup=\"false\""));
        assertTrue(out.contains("enabled=\"true\""));
        assertTrue(out.contains("screenOrientation=\"portrait\""));
        assertTrue(out.contains("launchMode=\"singleTask\""));
    }

    @Test
    public void versionIsConsistent() {
        assertEquals("3.0.0", ConversionResult.GENERATOR_VERSION);
    }
}
