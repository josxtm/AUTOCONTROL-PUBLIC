package com.josxtm.autocontrol.community.ocr;

import android.content.Context;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.AtomicFile;
import com.googlecode.tesseract.android.TessBaseAPI;
import java.io.*;
import java.security.MessageDigest;
import java.util.UUID;

/** No models or network clients bundled. Uses only a model explicitly imported by its owner. */
public final class LocalOcr {
    private final Context context;
    private final File root;
    public LocalOcr(Context context) { this.context = context.getApplicationContext(); root = new File(context.getFilesDir(), "ocr"); }
    private File model() { return new File(new File(root,"tessdata"),"model.traineddata"); }
    public synchronized boolean available() { return model().isFile(); }
    public synchronized String importModel(InputStream input) throws Exception {
        File stage = new File(context.getCacheDir(), "ocr-" + UUID.randomUUID());
        File folder = new File(stage,"tessdata");
        if (!folder.mkdirs()) throw new IOException("No se pudo crear almacenamiento local");
        File candidate = new File(folder,"model.traineddata");
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        TessBaseAPI engine = null;
        try {
            try (OutputStream output = new FileOutputStream(candidate)) {
                byte[] buffer = new byte[8192]; int count; long size = 0;
                while ((count = input.read(buffer)) != -1) {
                    if ((size += count) > 64 * 1024 * 1024) throw new IOException("Modelo demasiado grande");
                    hash.update(buffer,0,count); output.write(buffer,0,count);
                }
            }
            engine = new TessBaseAPI();
            if (!engine.init(stage.getAbsolutePath(),"model")) throw new IOException("Modelo OCR no compatible");
            engine.recycle(); engine = null;
            File targetFolder = model().getParentFile();
            if (!targetFolder.isDirectory() && !targetFolder.mkdirs()) throw new IOException("Sin espacio local");
            AtomicFile target = new AtomicFile(model()); FileOutputStream output = target.startWrite();
            try (InputStream source = new FileInputStream(candidate)) {
                byte[] buffer = new byte[8192]; int count;
                while ((count=source.read(buffer)) != -1) output.write(buffer,0,count);
                target.finishWrite(output);
            } catch (Exception error) { target.failWrite(output); throw error; }
            StringBuilder fingerprint = new StringBuilder();
            for (byte value : hash.digest()) fingerprint.append(String.format(java.util.Locale.ROOT,"%02x",value & 255));
            return fingerprint.toString();
        } finally { if (engine != null) engine.recycle(); candidate.delete(); folder.delete(); stage.delete(); }
    }
    public synchronized String recognize(ContentResolver resolver, Uri image) throws IOException {
        if (!available()) throw new IOException("Importa primero un modelo OCR compatible");
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds=true;
        try (InputStream input=resolver.openInputStream(image)) { if (input==null) throw new IOException(); BitmapFactory.decodeStream(input,null,options); }
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Imagen no válida");
        options.inSampleSize=1;
        while ((long)(options.outWidth/options.inSampleSize)*(options.outHeight/options.inSampleSize)>4000000
                || Math.max(options.outWidth,options.outHeight)/options.inSampleSize>2048) options.inSampleSize*=2;
        options.inJustDecodeBounds=false;
        Bitmap bitmap;
        try (InputStream input=resolver.openInputStream(image)) { if (input==null) throw new IOException(); bitmap=BitmapFactory.decodeStream(input,null,options); }
        if (bitmap==null) throw new IOException("Imagen no válida");
        TessBaseAPI engine = new TessBaseAPI();
        try {
            if (!engine.init(root.getAbsolutePath(),"model")) throw new IOException("Modelo OCR no disponible");
            engine.setDebug(false);
            engine.setImage(bitmap); String text=engine.getUTF8Text();
            if (text==null || text.length()>16000) throw new IOException("Resultado OCR demasiado grande");
            return text;
        } finally { engine.recycle(); bitmap.recycle(); }
    }
}
