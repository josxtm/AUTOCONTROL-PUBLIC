package com.josxtm.autocontrol.community.storage;

import android.content.Context;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public final class NativeCsvImportTest extends CsvImportScenarios {
    @Override protected Context context() { return RuntimeEnvironment.getApplication(); }
}
