package uk.cpjsmith.ponypaper;

import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.provider.OpenableColumns;
import android.util.TypedValue;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;

public class Settings extends PreferenceActivity {
    
    static final int SELECT_BACKGROUND = 0;
    static final int SELECT_CUSTOM = 1;

    private static final String URL_THIS_FORK = "https://github.com/derram/PonyPaper";
    private static final String URL_RELEASES = "https://github.com/derram/PonyPaper/releases";
    private static final String URL_UPSTREAM = "https://github.com/Smithers888/PonyPaper";
    private static final String URL_AUTHOR = "http://cpjsmith.uk";
    private static final String URL_DESKTOP_PONIES = "https://github.com/RoosterDragon/Desktop-Ponies";
    private static final String URL_DP_TEAM = "http://desktop-pony-team.deviantart.com/";
    private static final String URL_LEXEND = "https://github.com/googlefonts/lexend";
    private static final String URL_OFL_SITE = "https://openfontlicense.org";
    private static final String URL_CC_BY_NC_SA = "http://creativecommons.org/licenses/by-nc-sa/3.0/";
    private static final String OFL_ASSET_PATH = "font/OFL.txt";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(null);
        addPreferencesFromResource(R.xml.preferences);
        
        File dir = getExternalFilesDir(null);
        File[] customFiles = new File[0];
        if (dir != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            
            File[] files = dir.listFiles(AllPonies.xmlFilter);
            if (files == null) files = new File[0];
            Arrays.sort(files);
            customFiles = files;
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
        
        refreshWaifuList(customFiles);
        
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

        Preference openLiveWallpaper = findPreference("pref_open_live_wallpaper");
        if (openLiveWallpaper != null) {
            openLiveWallpaper.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    openSystemLiveWallpaperSettings();
                    return true;
                }
            });
        }

        Preference openScreensaver = findPreference("pref_open_screensaver");
        if (openScreensaver != null) {
            openScreensaver.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    openSystemDreamSettings();
                    return true;
                }
            });
        }

        setupAboutAndLicenses();
    }

    /**
     * Wires About / Licenses preferences: app blurb, version line, project
     * URLs (browser with clipboard fallback), and scrollable license text.
     */
    private void setupAboutAndLicenses() {
        Preference aboutApp = findPreference("pref_about_app");
        if (aboutApp != null) {
            aboutApp.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    showScrollableTextDialog(
                            getString(R.string.about_app_title),
                            getString(R.string.about_app_message));
                    return true;
                }
            });
        }

        Preference versionPref = findPreference("pref_about_version");
        if (versionPref != null) {
            versionPref.setSummary(getAppVersionLabel());
        }

        bindUrlPreference("pref_link_this_fork", URL_THIS_FORK);
        bindUrlPreference("pref_link_releases", URL_RELEASES);
        bindUrlPreference("pref_link_upstream", URL_UPSTREAM);
        bindUrlPreference("pref_link_author", URL_AUTHOR);
        bindUrlPreference("pref_link_desktop_ponies", URL_DESKTOP_PONIES);
        bindUrlPreference("pref_link_dp_team", URL_DP_TEAM);
        bindUrlPreference("pref_link_lexend", URL_LEXEND);
        bindUrlPreference("pref_link_ofl_site", URL_OFL_SITE);
        bindUrlPreference("pref_link_cc", URL_CC_BY_NC_SA);

        Preference artLicense = findPreference("pref_license_art");
        if (artLicense != null) {
            artLicense.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    showScrollableTextDialog(
                            getString(R.string.license_art_title),
                            getString(R.string.license_art_message));
                    return true;
                }
            });
        }

        Preference lexendLicense = findPreference("pref_license_lexend");
        if (lexendLicense != null) {
            lexendLicense.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    showLexendOflDialog();
                    return true;
                }
            });
        }
    }

    private void bindUrlPreference(String key, final String url) {
        Preference pref = findPreference(key);
        if (pref == null) return;
        pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                openUrlOrCopy(url);
                return true;
            }
        });
    }

    /**
     * Opens {@code url} in a browser. If no activity can handle the intent,
     * copies the URL to the clipboard and shows a short notice.
     */
    private void openUrlOrCopy(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            startActivity(intent);
            return;
        } catch (Exception ignored) {
        }
        copyTextToClipboard("url", url);
        showAlertDialog(
                getString(R.string.url_copied_title),
                getString(R.string.url_copied_message, url));
    }

    private void copyTextToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
        }
    }

    private void showLexendOflDialog() {
        try {
            String ofl = readAssetText(OFL_ASSET_PATH);
            showScrollableTextDialog(
                    getString(R.string.pref_license_lexend_title),
                    getString(R.string.license_lexend_header) + ofl);
        } catch (IOException e) {
            showAlertDialog(
                    getString(R.string.license_load_error_title),
                    getString(R.string.license_load_error_message));
        }
    }

    private String readAssetText(String assetPath) throws IOException {
        InputStream in = getAssets().open(assetPath);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
            // OFL.txt is plain ASCII; UTF-8 is a safe superset.
            return new String(out.toByteArray(), Charset.forName("UTF-8"));
        } finally {
            in.close();
        }
    }

    private String getAppVersionLabel() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            String name = info.versionName != null ? info.versionName : "?";
            return name + " (" + info.versionCode + ")";
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private void showScrollableTextDialog(String title, String message) {
        int pad = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16f, getResources().getDisplayMetrics());
        TextView textView = new TextView(this);
        textView.setText(message);
        textView.setTextIsSelectable(true);
        textView.setPadding(pad, pad, pad, pad);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(textView);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setView(scrollView);
        builder.setCancelable(true);
        builder.setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.create().show();
    }

    /**
     * Opens the system live wallpaper picker, preferring a direct preview for
     * {@link PonyWallpaper}. Path and availability vary by OEM; fall back to a
     * short notice if no known intent can be resolved.
     */
    private void openSystemLiveWallpaperSettings() {
        Intent change = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        change.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(this, PonyWallpaper.class));
        try {
            startActivity(change);
            return;
        } catch (Exception ignored) {
        }

        Intent chooser = new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER);
        try {
            startActivity(chooser);
            return;
        } catch (Exception ignored) {
        }

        showAlertDialog("Live wallpaper settings",
                "Open system Settings → Wallpaper (or Display → Wallpaper; wording varies by device) and choose Pony Paper as a live wallpaper.");
    }

    /**
     * Opens the system screen saver / Daydream picker when available.
     * Path and availability vary by OEM; fall back to a short notice if the
     * intent cannot be resolved.
     */
    private void openSystemDreamSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_DREAM_SETTINGS);
        try {
            startActivity(intent);
        } catch (Exception e) {
            showAlertDialog("Screen saver settings",
                    "Open system Settings → Display → Screen saver (wording varies by device) and choose Pony Paper.");
        }
    }
    
    /**
     * Rebuilds the Favorite (waifu) list from built-in arrays plus any custom
     * pony XML basenames currently on disk.
     */
    private void refreshWaifuList(File[] customFiles) {
        ListPreference waifu = (ListPreference)findPreference("pref_waifu");
        if (waifu == null) return;
        
        CharSequence[] baseEntries = getResources().getTextArray(R.array.pref_waifu_entries);
        CharSequence[] baseValues = getResources().getTextArray(R.array.pref_waifu_values);
        
        if (customFiles == null) customFiles = new File[0];
        
        ArrayList<CharSequence> entries = new ArrayList<CharSequence>(baseEntries.length + customFiles.length);
        ArrayList<CharSequence> values = new ArrayList<CharSequence>(baseValues.length + customFiles.length);
        for (int i = 0; i < baseEntries.length; i++) {
            entries.add(baseEntries[i]);
            values.add(baseValues[i]);
        }
        for (int i = 0; i < customFiles.length; i++) {
            String fileName = customFiles[i].getName();
            entries.add(fileName);
            values.add("pref_custom_" + fileName);
        }
        
        CharSequence[] entryArr = entries.toArray(new CharSequence[entries.size()]);
        CharSequence[] valueArr = values.toArray(new CharSequence[values.size()]);
        waifu.setEntries(entryArr);
        waifu.setEntryValues(valueArr);
        
        // Keep summary correct when the stored value is still valid.
        String current = waifu.getValue();
        if (current == null) current = "";
        boolean known = false;
        for (int i = 0; i < valueArr.length; i++) {
            if (current.equals(valueArr[i].toString())) {
                known = true;
                break;
            }
        }
        if (!known) {
            waifu.setValue("");
        }
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
                        File dirAfter = getExternalFilesDir(null);
                        File[] filesAfter = dirAfter != null ? dirAfter.listFiles(AllPonies.xmlFilter) : null;
                        if (filesAfter != null) Arrays.sort(filesAfter);
                        refreshWaifuList(filesAfter != null ? filesAfter : new File[0]);
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
