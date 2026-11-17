package com.josxtm.autocontrol.community.storage;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class AndroidCsvImportTest extends CsvImportScenarios {
    @Override protected Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
}
