package uk.cpjsmith.ponypaper;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;

public class Settings extends PreferenceActivity {
    
    static final int SELECT_BACKGROUND = 0;
    static final int SELECT_CUSTOM = 1;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        
        File dir = getExternalFilesDir(null);
        if (dir != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            
            File[] files = dir.listFiles(AllPonies.xmlFilter);
            if (files == null) files = new File[0];
            Arrays.sort(files);
            PreferenceCategory customCat = (PreferenceCategory)findPreference("pref_custom");
            for (int i = 0; i < files.length; i++) {
                String fileName = files[i].getName();
                String prefKey = "pref_custom_" + fileName;
                
                if (!prefs.contains(prefKey)) {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(prefKey, true);
                    editor.commit();
                }
                
                CheckBoxPreference checkbox = new CheckBoxPreference(this);
                checkbox.setKey(prefKey);
                checkbox.setTitle(fileName);
                customCat.addPreference(checkbox);
            }
        }
        
        findPreference("pref_add_custom").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                startActivityForResult(Intent.createChooser(intent, "Select Custom Pony"), SELECT_CUSTOM);
                return true;
            }
        });
        
        findPreference("pref_background").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if ((Boolean)newValue) {
                    File filesDir = getExternalFilesDir(null);
                    if (filesDir == null) {
                        showAlertDialog("Background unavailable",
                                "App storage is not available on this device right now.");
                        return false;
                    }
                    if (!new File(filesDir, "background").exists()) {
                        selectBackground();
                    }
                }
                return true;
            }
        });
        
        findPreference("pref_select_background").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                selectBackground();
                return true;
            }
        });
    }
    
    private void selectBackground() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Select Background"), SELECT_BACKGROUND);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SELECT_BACKGROUND:
                if (resultCode == RESULT_OK) {
                    Uri imageUri = data.getData();
                    
                    try {
                        String hash = copyToLocalAndGetHash(imageUri, "background");
                        
                        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
                        SharedPreferences.Editor editor = sp.edit();
                        editor.putString("pref_select_background", hash.toString());
                        editor.commit();
                    } catch (IOException e) {
                        showAlertDialog("Failed to set background", "An I/O error occurred.");
                    }
                }
                break;
                
            case SELECT_CUSTOM:
                if (resultCode == RESULT_OK) {
                    Uri ponyUri = data.getData();
                    
                    // Validate the pony before storing it.
                    try {
                        DocumentBuilder docBuilder = SecureXml.newDocumentBuilder();
                        InputStream in = getContentResolver().openInputStream(ponyUri);
                        Document document = docBuilder.parse(in);
                        if (in != null) in.close();
                        PonyDefinition definition = new PonyDefinition(document);
                        definition.validate();
                    } catch (Exception e) {
                        showAlertDialog("Failed to add pony", "Selected file was not a valid custom pony definition.");
                        break;
                    }
                    
                    try {
                        String fileName = sanitizeCustomPonyFileName(getFileName(ponyUri));
                        if (fileName == null) {
                            showAlertDialog("Failed to add pony", "Could not determine a safe file name for the selected pony.");
                            break;
                        }
                        
                        String hash = copyToLocalAndGetHash(ponyUri, fileName);
                        
                        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
                        SharedPreferences.Editor editor = sp.edit();
                        String prefKey = "pref_custom_" + fileName;
                        editor.putBoolean(prefKey, true);
                        editor.putString("pref_add_custom", hash);
                        editor.commit();
                        if (findPreference(prefKey) == null) {
                            CheckBoxPreference checkbox = new CheckBoxPreference(this);
                            checkbox.setKey(prefKey);
                            checkbox.setTitle(fileName);
                            ((PreferenceCategory)findPreference("pref_custom")).addPreference(checkbox);
                        }
                    } catch (IOException e) {
                        showAlertDialog("Failed to add pony", "An I/O error occurred.");
                    }
                }
                break;
        }
    }
    
    private String copyToLocalAndGetHash(Uri sourceUri, String destName) throws IOException {
        File dir = getExternalFilesDir(null);
        if (dir == null) {
            throw new IOException("External files directory unavailable");
        }
        // destName must already be a sanitized basename (no path separators).
        File dest = new File(dir, destName);
        if (!dest.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile())) {
            throw new IOException("Refusing to write outside app files directory");
        }
        
        MessageDigest digester;
        try {
            digester = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            digester = null;
        }
        
        InputStream in = getContentResolver().openInputStream(sourceUri);
        if (in == null) {
            throw new IOException("Could not open selected content");
        }
        try {
            OutputStream out = new FileOutputStream(dest);
            try {
                byte[] buffer = new byte[1024];
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, n);
                    if (digester != null) digester.update(buffer, 0, n);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
        
        byte[] digest;
        if (digester != null) {
            digest = digester.digest();
        } else {
            digest = new byte[20];
            new Random().nextBytes(digest);
        }
        StringBuilder hash = new StringBuilder();
        for (int i = 0; i < digest.length; i++)
            hash.append(String.format("%02x", (256 + digest[i]) % 256));
        return hash.toString();
    }
    
    /**
     * Produce a safe basename for custom pony XML under the app files directory.
     * Strips path segments, rejects {@code ..}, allows only {@code [A-Za-z0-9._-]},
     * and forces a {@code .xml} suffix.
     *
     * @return sanitized name, or null if nothing usable remains
     */
    static String sanitizeCustomPonyFileName(String raw) {
        if (raw == null) return null;
        String name = raw.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return null;
        }
        if (name.regionMatches(true, name.length() - 4, ".xml", 0, 4)) {
            name = name.substring(0, name.length() - 4);
        }
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if ((ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '.' || ch == '_' || ch == '-') {
                sb.append(ch);
            } else {
                sb.append('_');
            }
        }
        String base = sb.toString();
        while (base.startsWith(".")) {
            base = base.substring(1);
        }
        if (base.isEmpty() || base.equals(".") || base.equals("..")) {
            return null;
        }
        return base + ".xml";
    }
    
    private void showAlertDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setCancelable(true);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.create().show();
    }
    
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
            }
            if (cursor != null) cursor.close();
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }
    
}
