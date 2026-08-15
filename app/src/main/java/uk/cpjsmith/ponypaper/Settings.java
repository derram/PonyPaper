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
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;

public class Settings extends PreferenceActivity {
    
    static final int SELECT_BACKGROUND = 0;
    static final int SELECT_CUSTOM = 1;
    static final int EXPORT_LIBRARY = 2;
    static final int IMPORT_LIBRARY = 3;
    static final int PICK_LIBRARY_FOLDER = 4;

    private static final String URL_THIS_FORK = "https://github.com/derram/PonyPaper";
    private static final String URL_RELEASES = "https://github.com/derram/PonyPaper/releases";
    private static final String URL_UPSTREAM = "https://github.com/Smithers888/PonyPaper";
    private static final String URL_AUTHOR = "http://cpjsmith.uk";
    private static final String URL_DESKTOP_PONIES = "https://github.com/RoosterDragon/Desktop-Ponies";
    private static final String URL_DP_TEAM = "http://desktop-pony-team.deviantart.com/";
    private static final String URL_GROK_BUILD = "https://x.ai/cli";
    private static final String URL_LEXEND = "https://github.com/googlefonts/lexend";
    private static final String URL_OFL_SITE = "https://openfontlicense.org";
    private static final String URL_CC_BY_NC_SA = "http://creativecommons.org/licenses/by-nc-sa/3.0/";
    private static final String OFL_ASSET_PATH = "font/OFL.txt";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(null);
        addPreferencesFromResource(R.xml.preferences);
        
        File[] customFiles = CustomStorage.listCustomXml(this);
        ensureCustomCheckboxes(customFiles);
        refreshWaifuList(customFiles);
        updateLibraryFolderSummary();
        
        findPreference("pref_add_custom").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(intent, "Select Custom Pony"), SELECT_CUSTOM);
                return true;
            }
        });

        findPreference("pref_export_library").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                startExportLibrary();
                return true;
            }
        });

        findPreference("pref_import_library").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("application/zip");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(intent, getString(R.string.pref_import_library_title)),
                        IMPORT_LIBRARY);
                return true;
            }
        });

        findPreference("pref_library_folder").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                onLibraryFolderClicked();
                return true;
            }
        });
        
        findPreference("pref_background").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if ((Boolean)newValue) {
                    File filesDir = CustomStorage.localDir(Settings.this);
                    if (filesDir == null) {
                        showAlertDialog("Background unavailable",
                                "App storage is not available on this device right now.");
                        return false;
                    }
                    if (!new File(filesDir, CustomStorage.BACKGROUND_NAME).exists()) {
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

    @Override
    protected void onResume() {
        super.onResume();
        startLibrarySync(false);
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
        bindUrlPreference("pref_link_grok_build", URL_GROK_BUILD);
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
                if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                    importBackground(data.getData());
                }
                break;

            case SELECT_CUSTOM:
                if (resultCode == RESULT_OK && data != null) {
                    handlePickedContent(collectUris(data));
                }
                break;

            case IMPORT_LIBRARY:
                if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                    importLibraryZip(data.getData());
                }
                break;

            case EXPORT_LIBRARY:
                if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                    exportLibraryZip(data.getData());
                }
                break;

            case PICK_LIBRARY_FOLDER:
                if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                    connectLibraryFolder(data.getData(), data.getFlags());
                }
                break;
        }
    }
    
    private boolean storageBusy;
    private boolean pendingLibrarySync;
    private boolean pendingLibrarySyncShow;

    private void ensureCustomCheckboxes(File[] customFiles) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        PreferenceCategory customCat = (PreferenceCategory) findPreference("pref_custom");
        if (customCat == null || customFiles == null) return;
        for (int i = 0; i < customFiles.length; i++) {
            String fileName = customFiles[i].getName();
            String prefKey = "pref_custom_" + fileName;
            if (!prefs.contains(prefKey)) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(prefKey, true);
                editor.commit();
            }
            if (findPreference(prefKey) == null) {
                CheckBoxPreference checkbox = new CheckBoxPreference(this);
                checkbox.setKey(prefKey);
                checkbox.setTitle(fileName);
                customCat.addPreference(checkbox);
            }
        }
    }

    private void refreshCustomPoniesUi() {
        File[] files = CustomStorage.listCustomXml(this);
        ensureCustomCheckboxes(files);
        refreshWaifuList(files);
    }

    private void startExportLibrary() {
        if (!CustomStorage.hasExportableFiles(this)) {
            showAlertDialog(getString(R.string.library_export_empty_title),
                    getString(R.string.library_export_empty_message));
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, defaultExportFileName());
        try {
            startActivityForResult(intent, EXPORT_LIBRARY);
        } catch (Exception e) {
            showAlertDialog(getString(R.string.library_export_failed_title),
                    getString(R.string.library_pick_failed_message));
        }
    }

    private static String defaultExportFileName() {
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US);
        return "PonyPaper-library-" + fmt.format(new java.util.Date()) + ".zip";
    }

    private void exportLibraryZip(final Uri dest) {
        if (!beginStorageWork()) return;
        new Thread(new Runnable() {
            public void run() {
                String error = null;
                try {
                    CustomStorage.exportZip(Settings.this, dest);
                } catch (Exception e) {
                    error = e.getMessage();
                }
                final String fail = error;
                runOnUiThread(new Runnable() {
                    public void run() {
                        storageBusy = false;
                        if (fail != null) {
                            showAlertDialog(getString(R.string.library_export_failed_title), fail);
                        } else {
                            showAlertDialog(getString(R.string.library_export_ok_title),
                                    getString(R.string.library_export_ok_message));
                        }
                    }
                });
            }
        }, "ponypaper-export").start();
    }

    private void importLibraryZip(final Uri source) {
        if (!beginStorageWork()) return;
        new Thread(new Runnable() {
            public void run() {
                final CustomStorage.ZipImportResult result = CustomStorage.importZip(Settings.this, source);
                if (result.error == null && (result.poniesAdded > 0 || result.backgroundImported)) {
                    CustomStorage.SyncResult sync = CustomStorage.syncLibrary(Settings.this);
                    if (sync.permissionLost) {
                        // Keep imported local files; reconnect is separate.
                    }
                }
                runOnUiThread(new Runnable() {
                    public void run() {
                        storageBusy = false;
                        showZipImportResult(result);
                        refreshCustomPoniesUi();
                        if (result.poniesAdded > 0 || result.backgroundImported) {
                            CustomStorage.bumpGeneration(Settings.this);
                        }
                    }
                });
            }
        }, "ponypaper-import").start();
    }

    private void showZipImportResult(CustomStorage.ZipImportResult result) {
        if (result.error != null) {
            showAlertDialog(getString(R.string.library_import_failed_title), result.error);
            return;
        }
        if (result.poniesAdded == 0 && !result.backgroundImported) {
            showAlertDialog(getString(R.string.library_import_ok_title),
                    getString(R.string.library_import_nothing));
            return;
        }
        String ponyWord = result.poniesAdded == 1
                ? getString(R.string.library_import_pony_one)
                : getString(R.string.library_import_pony_many);
        String bg = result.backgroundImported ? getString(R.string.library_import_background) : "";
        String skipped = result.skipped > 0
                ? getString(R.string.library_import_skipped, result.skipped)
                : "";
        showAlertDialog(getString(R.string.library_import_ok_title),
                getString(R.string.library_import_ok_message, result.poniesAdded, ponyWord, bg, skipped));
    }

    private void handlePickedContent(Uri[] uris) {
        if (uris == null || uris.length == 0) return;
        if (uris.length == 1 && CustomStorage.looksLikeZip(getFileName(uris[0]),
                getContentResolver().getType(uris[0]))) {
            importLibraryZip(uris[0]);
            return;
        }
        boolean anyZip = false;
        for (int i = 0; i < uris.length; i++) {
            if (CustomStorage.looksLikeZip(getFileName(uris[i]), getContentResolver().getType(uris[i]))) {
                anyZip = true;
                importLibraryZip(uris[i]);
            } else {
                importSinglePony(uris[i]);
            }
        }
        if (!anyZip) {
            refreshCustomPoniesUi();
        }
    }

    private void importBackground(Uri imageUri) {
        try {
            String hash = CustomStorage.copyUriToLocal(this, imageUri, CustomStorage.BACKGROUND_NAME);
            CustomStorage.writeThroughToLibrary(this, CustomStorage.localFile(this, CustomStorage.BACKGROUND_NAME));
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
            editor.putString("pref_select_background", hash);
            editor.commit();
        } catch (IOException e) {
            showAlertDialog("Failed to set background", "An I/O error occurred.");
        }
    }

    private void importSinglePony(Uri ponyUri) {
        if (!CustomStorage.isValidCustomPonyUri(this, ponyUri)) {
            showAlertDialog("Failed to add pony", "Selected file was not a valid custom pony definition.");
            return;
        }
        try {
            String fileName = CustomStorage.sanitizeCustomPonyFileName(getFileName(ponyUri));
            if (fileName == null) {
                showAlertDialog("Failed to add pony", "Could not determine a safe file name for the selected pony.");
                return;
            }
            String hash = CustomStorage.copyUriToLocal(this, ponyUri, fileName);
            CustomStorage.writeThroughToLibrary(this, CustomStorage.localFile(this, fileName));
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
            String prefKey = "pref_custom_" + fileName;
            editor.putBoolean(prefKey, true);
            editor.putString("pref_add_custom", hash);
            editor.commit();
            ensureCustomCheckboxes(new File[] { CustomStorage.localFile(this, fileName) });
            refreshWaifuList(CustomStorage.listCustomXml(this));
        } catch (IOException e) {
            showAlertDialog("Failed to add pony", "An I/O error occurred.");
        }
    }

    private Uri[] collectUris(Intent data) {
        ClipData clip = data.getClipData();
        if (clip != null && clip.getItemCount() > 0) {
            ArrayList<Uri> list = new ArrayList<Uri>(clip.getItemCount());
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null) list.add(uri);
            }
            return list.toArray(new Uri[list.size()]);
        }
        if (data.getData() != null) {
            return new Uri[] { data.getData() };
        }
        return new Uri[0];
    }

    private void onLibraryFolderClicked() {
        if (!CustomStorage.hasLibraryFolder(this)) {
            pickLibraryFolder();
            return;
        }
        if (!CustomStorage.canAccessLibrary(this)) {
            showAlertDialog(getString(R.string.library_sync_lost_title),
                    getString(R.string.library_sync_lost_message));
            pickLibraryFolder();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.library_folder_dialog_title);
        CharSequence[] items = new CharSequence[] {
                getString(R.string.library_folder_sync),
                getString(R.string.library_folder_change),
                getString(R.string.library_folder_disconnect)
        };
        builder.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    startLibrarySync(true);
                } else if (which == 1) {
                    pickLibraryFolder();
                } else if (which == 2) {
                    CustomStorage.releaseLibraryTree(Settings.this);
                    updateLibraryFolderSummary();
                    showAlertDialog(getString(R.string.library_disconnected_title),
                            getString(R.string.library_disconnected_message));
                }
            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, null);
        builder.create().show();
    }

    private void pickLibraryFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            startActivityForResult(intent, PICK_LIBRARY_FOLDER);
        } catch (Exception e) {
            showAlertDialog(getString(R.string.library_pick_failed_title),
                    getString(R.string.library_pick_failed_message));
        }
    }

    private void connectLibraryFolder(final Uri treeUri, final int flags) {
        try {
            CustomStorage.setLibraryTreeUri(this, treeUri, flags);
        } catch (SecurityException e) {
            showAlertDialog(getString(R.string.library_sync_failed_title),
                    "Could not keep access to that folder.");
            return;
        }
        updateLibraryFolderSummary();
        showAlertDialog(getString(R.string.library_connected_title),
                getString(R.string.library_connected_message));
        startLibrarySync(false);
    }

    private void startLibrarySync(final boolean showResult) {
        if (!CustomStorage.hasLibraryFolder(this)) {
            updateLibraryFolderSummary();
            return;
        }
        if (storageBusy) {
            pendingLibrarySync = true;
            pendingLibrarySyncShow |= showResult;
            return;
        }
        storageBusy = true;
        new Thread(new Runnable() {
            public void run() {
                final CustomStorage.SyncResult result = CustomStorage.syncLibrary(Settings.this);
                runOnUiThread(new Runnable() {
                    public void run() {
                        storageBusy = false;
                        updateLibraryFolderSummary();
                        if (result.permissionLost) {
                            showAlertDialog(getString(R.string.library_sync_lost_title),
                                    getString(R.string.library_sync_lost_message));
                        } else if (result.error != null) {
                            if (showResult) {
                                showAlertDialog(getString(R.string.library_sync_failed_title), result.error);
                            }
                        } else {
                            if (result.changed) {
                                refreshCustomPoniesUi();
                                CustomStorage.bumpGeneration(Settings.this);
                            }
                            if (showResult) {
                                if (result.changed) {
                                    showAlertDialog(getString(R.string.library_sync_ok_title),
                                            getString(R.string.library_sync_ok_message,
                                                    result.pulled, result.pushed));
                                } else {
                                    showAlertDialog(getString(R.string.library_sync_ok_title),
                                            getString(R.string.library_sync_none_message));
                                }
                            }
                        }
                        if (pendingLibrarySync) {
                            boolean show = pendingLibrarySyncShow;
                            pendingLibrarySync = false;
                            pendingLibrarySyncShow = false;
                            startLibrarySync(show);
                        }
                    }
                });
            }
        }, "ponypaper-libsync").start();
    }

    private void updateLibraryFolderSummary() {
        Preference pref = findPreference("pref_library_folder");
        if (pref == null) return;
        if (!CustomStorage.hasLibraryFolder(this)) {
            pref.setSummary(R.string.pref_library_folder_summary_unset);
            return;
        }
        if (!CustomStorage.canAccessLibrary(this)) {
            pref.setSummary(R.string.pref_library_folder_summary_lost);
            return;
        }
        String label = CustomStorage.libraryFolderLabel(this);
        if (label == null || label.length() == 0) label = "folder";
        pref.setSummary(getString(R.string.pref_library_folder_summary_set, label));
    }

    private boolean beginStorageWork() {
        if (storageBusy) {
            showAlertDialog(getString(R.string.library_busy_title),
                    getString(R.string.library_busy_message));
            return false;
        }
        storageBusy = true;
        return true;
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
