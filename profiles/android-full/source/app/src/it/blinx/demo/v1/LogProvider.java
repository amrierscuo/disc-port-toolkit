package it.blinx.demo.v1;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public final class LogProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private File resolve(Uri uri) throws FileNotFoundException {
        if (!"it.blinx.demo.v1.logs".equals(uri.getAuthority()) || !"/diagnostics.txt".equals(uri.getPath()) || uri.getQuery() != null)
            throw new FileNotFoundException("File non condivisibile.");
        try {
            File root = getContext().getCacheDir().getCanonicalFile();
            File file = new File(root, "diagnostics.txt").getCanonicalFile();
            if (!file.getParentFile().equals(root) || !file.isFile()) throw new FileNotFoundException("Log non disponibile.");
            return file;
        } catch (IOException error) { throw new FileNotFoundException(error.toString()); }
    }
    @Override public String getType(Uri uri) { return "text/plain"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Accesso in sola lettura.");
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        try {
            File file = resolve(uri);
            String[] columns = projection == null ? new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE } : projection;
            MatrixCursor result = new MatrixCursor(columns, 1);
            Object[] values = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i] = "Blinx_Demo_V1_diagnostics.txt";
                else if (OpenableColumns.SIZE.equals(columns[i])) values[i] = file.length();
            }
            result.addRow(values); return result;
        } catch (FileNotFoundException error) { return null; }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException("Read only"); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException("Read only"); }
}
