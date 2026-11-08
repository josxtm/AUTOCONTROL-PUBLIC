package com.josxtm.autocontrol.community;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import android.graphics.*;
import android.os.ParcelFileDescriptor;
import android.net.Uri;
import java.io.*;
import com.josxtm.autocontrol.community.ocr.LocalOcr;

@RunWith(AndroidJUnit4.class)
public final class LocalOcrTest {
    @Test public void userImportedLicensedModelRecognizesOnlyGeneratedImage() throws Exception {
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        android.content.Context context=instrumentation.getTargetContext(); LocalOcr ocr=new LocalOcr(context);
        // CI supplies a separately downloaded, checksum-pinned Apache-2.0 model, never in the APK.
        try (InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(instrumentation.getUiAutomation()
                .executeShellCommand("cat /data/local/tmp/synthetic-ocr-model.traineddata"))) {
            assertEquals(64,ocr.importModel(input).length());
        }
        assertTrue(ocr.available());
        try { ocr.importModel(new ByteArrayInputStream("Synthetic invalid model".getBytes(java.nio.charset.StandardCharsets.UTF_8))); fail(); }
        catch(IOException expected) { }
        Bitmap bitmap=Bitmap.createBitmap(1200,300,Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(bitmap); canvas.drawColor(Color.WHITE);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(Color.BLACK); paint.setTextSize(110); paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("SYNTHETIC 42",40,180,paint);
        File image=new File(context.getCacheDir(),"synthetic-ocr.png");
        try {
            try(OutputStream output=new FileOutputStream(image)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,output)); }
            String text=ocr.recognize(context.getContentResolver(),Uri.fromFile(image));
            assertTrue(text.contains("SYNTHETIC")); assertTrue(text.contains("42"));
        } finally { bitmap.recycle(); image.delete(); }
    }
}
