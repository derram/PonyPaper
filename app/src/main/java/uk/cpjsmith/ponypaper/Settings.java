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
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import android.provider.OpenableColumns;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Settings extends AppCompatActivity
        implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

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

    private final Handler settingsHandler = new Handler(Looper.getMainLooper());
    private DisplayManager.DisplayListener fpsDisplayListener;
    /** Timeout value to write after a successful device-admin grant; null if none. */
    private String pendingDreamIdleTimeout;
    /**
     * True after {@link DreamSleepAdmin#removeAdmin} until
     * {@link DreamSleepAdmin#isActive} reports false. {@code removeActiveAdmin}
     * is asynchronous, so an immediate refresh would still show the On summary.
     */
    private boolean pendingDreamAdminRevoke;
    private int dreamAdminRefreshAttempts;

    /** Preference hierarchy of the currently visible settings fragment. */
    private PreferenceFragmentCompat activePrefs;

    /** Categories for the in-flight export SAF create-document callback; null = all. */
    private CustomStorage.ExportOptions pendingExportOptions;

    private final Runnable dreamAdminRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!pendingDreamAdminRevoke) return;
            if (!DreamSleepAdmin.isActive(Settings.this)
                    || ++dreamAdminRefreshAttempts >= 40) {
                pendingDreamAdminRevoke = false;
                refreshDreamIdleSettings();
                return;
            }
            refreshDreamIdleSettings();
            settingsHandler.postDelayed(this, 250);
        }
    };

    private final ActivityResultLauncher<Intent> selectBackgroundLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            Intent data = result.getData();
                            if (result.getResultCode() == RESULT_OK
                                    && data != null
                                    && data.getData() != null) {
                                importBackground(data.getData());
                            }
                        }
                    });

    private final ActivityResultLauncher<Intent> selectCustomLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            Intent data = result.getData();
                            if (result.getResultCode() == RESULT_OK && data != null) {
                                handlePickedContent(collectUris(data));
                            }
                        }
                    });

    private final ActivityResultLauncher<Intent> importLibraryLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            Intent data = result.getData();
                            if (result.getResultCode() == RESULT_OK
                                    && data != null
                                    && data.getData() != null) {
                                importLibraryZip(data.getData());
                            }
                        }
                    });

    private final ActivityResultLauncher<Intent> exportLibraryLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            Intent data = result.getData();
                            if (result.getResultCode() == RESULT_OK
                                    && data != null
                                    && data.getData() != null) {
                                CustomStorage.ExportOptions options = pendingExportOptions;
                                pendingExportOptions = null;
                                if (options == null) options = CustomStorage.ExportOptions.all();
                                exportLibraryZip(data.getData(), options);
                            } else {
                                pendingExportOptions = null;
                            }
                        }
                    });

    private final ActivityResultLauncher<Intent> pickLibraryFolderLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            Intent data = result.getData();
                            if (result.getResultCode() == RESULT_OK
                                    && data != null
                                    && data.getData() != null) {
                                connectLibraryFolder(data.getData(), data.getFlags());
                            }
                        }
                    });

    private final ActivityResultLauncher<Intent> dreamLockAdminLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            applyPendingDreamIdleTimeout();
                            refreshDreamIdleSettings();
                        }
                    });

    private final SharedPreferences.OnSharedPreferenceChangeListener enableAllListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                    if (isLiveHerdPreferenceKey(key)) {
                        PonyMixes.noteManualHerdEdit(sharedPreferences);
                        refreshHerdScreenSummaries();
                    }
                    refreshEnableAllToggles();
                    if (key == null
                            || PonySceneController.PREF_TARGET_FPS.equals(key)
                            || PonySceneController.PREF_DREAM_TARGET_FPS.equals(key)) {
                        refreshTargetFpsList();
                    }
                    if (key == null
                            || PonySceneController.PREF_BACKGROUND.equals(key)
                            || PonySceneController.PREF_DREAM_BACKGROUND.equals(key)
                            || PonySceneController.PREF_DREAM_CUSTOM_DISPLAY.equals(key)) {
                        refreshSharedBackgroundControls();
                    }
                }
            };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_container);
        PonySize.ensureDefault(this);
        TargetFps.ensureDefault(this);
        PonySceneController.ensureIdleTimeoutDefault(this);
        SceneMode.migrate(this);
        PrefDefaults.apply(this);

        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(enableAllListener);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new RootPreferencesFragment())
                    .commit();
        }

        getSupportFragmentManager().addOnBackStackChangedListener(
                new FragmentManager.OnBackStackChangedListener() {
                    @Override
                    public void onBackStackChanged() {
                        updateUpNavigation();
                    }
                });
        updateUpNavigation();
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        Fragment fragment = getSupportFragmentManager().getFragmentFactory()
                .instantiate(getClassLoader(), pref.getFragment());
        fragment.setArguments(pref.getExtras());
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, fragment)
                .addToBackStack(pref.getKey())
                .commit();
        setTitle(pref.getTitle());
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (getSupportFragmentManager().popBackStackImmediate()) {
            return true;
        }
        return super.onSupportNavigateUp();
    }

    private void updateUpNavigation() {
        boolean canGoUp = getSupportFragmentManager().getBackStackEntryCount() > 0;
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(canGoUp);
        }
        if (!canGoUp) {
            setTitle(R.string.app_settings_name);
        }
    }

    Preference findPreference(CharSequence key) {
        if (activePrefs == null || !activePrefs.isAdded()) return null;
        return activePrefs.findPreference(key);
    }

    void bindRootPreferences(PreferenceFragmentCompat fragment) {
        activePrefs = fragment;
        Preference debug = findPreference("pref_screen_debug");
        if (debug != null && !BuildConfig.DEBUG) {
            PreferenceScreen screen = fragment.getPreferenceScreen();
            if (screen != null) {
                screen.removePreference(debug);
            }
        }
    }

    void bindDebugPreferences(PreferenceFragmentCompat fragment) {
        activePrefs = fragment;
    }

    void bindDisplayPreferences(PreferenceFragmentCompat fragment) {
        activePrefs = fragment;

        Preference background = findPreference("pref_background");
        if (background != null) {
            background.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    return onBackgroundEnableChanging((Boolean) newValue);
                }
            });
        }

        Preference dreamBackground = findPreference(PonySceneController.PREF_DREAM_BACKGROUND);
        if (dreamBackground != null) {
            dreamBackground.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    return onBackgroundEnableChanging((Boolean) newValue);
                }
            });
        }

        Preference dreamCustomDisplay = findPreference(PonySceneController.PREF_DREAM_CUSTOM_DISPLAY);
        if (dreamCustomDisplay != null) {
            dreamCustomDisplay.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (Boolean.TRUE.equals(newValue)) {
                        seedDreamDisplayOverridesIfNeeded();
                    }
                    return true;
                }
            });
        }

        Preference selectBackground = findPreference("pref_select_background");
        if (selectBackground != null) {
            selectBackground.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    selectBackground();
                    return true;
                }
            });
        }

        Preference clearBackground = findPreference("pref_clear_background");
        if (clearBackground != null) {
            clearBackground.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    onClearBackgroundClicked();
                    return true;
                }
            });
        }
        refreshSharedBackgroundControls();

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

        Preference dreamAdmin = findPreference("pref_dream_device_admin");
        if (dreamAdmin != null) {
            dreamAdmin.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    onDreamLockAdminClicked();
                    return true;
                }
            });
        }

        Preference dreamTimeout = findPreference(PonySceneController.PREF_DREAM_IDLE_TIMEOUT);
        if (dreamTimeout != null) {
            dreamTimeout.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    return onDreamIdleTimeoutChanging(newValue);
                }
            });
        }

        Preference sceneMode = findPreference(SceneMode.PREF_KEY);
        if (sceneMode != null) {
            sceneMode.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    refreshCharacterSizeAvailability(
                            newValue != null ? newValue.toString() : SceneMode.WANDER);
                    return true;
                }
            });
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean(PonySceneController.PREF_DREAM_CUSTOM_DISPLAY, false)) {
            seedDreamDisplayOverridesIfNeeded();
        }
    }

    void refreshDisplayScreen() {
        refreshTargetFpsList();
        refreshSharedBackgroundControls();
        refreshDreamIdleSettings();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        refreshCharacterSizeAvailability(SceneMode.mode(prefs));
    }

    /**
     * Scene-mode side effects on Display prefs. Character size is unused under
     * My ??? Pony; Tableau keeps it enabled (global size) but owns population
     * via slots + caps, so num-ponies pickers are disabled.
     * {@code mode} is the scene-mode value that will apply (may be the pending
     * preference change before SharedPreferences has stored it).
     */
    private void refreshCharacterSizeAvailability(String mode) {
        boolean random = SceneMode.MY_QUESTION.equals(mode);
        boolean tableau = SceneMode.TABLEAU.equals(mode);

        ListPreference size = (ListPreference) findPreference(PonySize.PREF_KEY);
        if (size != null) {
            size.setEnabled(!random);
            if (random) {
                size.setSummary(R.string.pref_pony_size_randomized_summary);
            } else {
                size.setSummary("%s");
            }
        }

        Preference numPonies = findPreference(PonySceneController.PREF_NUM_PONIES);
        if (numPonies != null) {
            numPonies.setEnabled(!tableau);
            if (tableau) {
                numPonies.setSummary(R.string.pref_num_ponies_tableau_summary);
            } else {
                numPonies.setSummary(null);
            }
        }
        Preference dreamNumPonies =
                findPreference(PonySceneController.PREF_DREAM_NUM_PONIES);
        if (dreamNumPonies != null) {
            // Parent dependency on pref_dream_custom_display still applies when
            // enabled; force off while Tableau owns population.
            dreamNumPonies.setEnabled(!tableau);
            if (tableau) {
                dreamNumPonies.setSummary(R.string.pref_num_ponies_tableau_summary);
            } else {
                dreamNumPonies.setSummary(null);
            }
        }
    }

    void bindPoniesPreferences(PreferenceFragmentCompat fragment) {
        activePrefs = fragment;
        File[] customFiles = CustomStorage.listCustomXml(this);
        ensureCustomPrefDefaults(customFiles);
        refreshWaifuList(customFiles);
        setupMixActions();
        refreshHerdScreenSummaries();
    }

    void refreshPoniesScreen() {
        File[] customFiles = CustomStorage.listCustomXml(this);
        ensureCustomPrefDefaults(customFiles);
        refreshWaifuList(customFiles);
        refreshHerdScreenSummaries();
    }

    void bindBuiltInPoniesPreferences(PreferenceFragmentCompat fragment) {
        activePrefs = fragment;
        setupEnableAllToggles();
    }

    void refreshBuiltInPoniesScreen() {
        refreshEnableAllToggles();
    }

    void bindCustomPoniesPreferences(PreferenceFragmentCompat fragment) {
        activePrefs = fragment;
        File[] customFiles = CustomStorage.listCustomXml(this);
        ensureCustomPrefDefaults(customFiles);
        ensureCustomCheckboxes(customFiles);
        setupEnableAllToggles();
    }

    void refreshCustomPoniesScreen() {
        refreshCustomPoniesUi();
    }

    void bindLibraryPreferences(PreferenceFragmentCompat fragment) {
        activePrefs = fragment;
        ensureCustomPrefDefaults(CustomStorage.listCustomXml(this));
        updateLibraryFolderSummary();

        Preference addCustom = findPreference("pref_add_custom");
        if (addCustom != null) {
            addCustom.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    selectCustomLauncher.launch(
                            Intent.createChooser(intent, "Select Custom Pony"));
                    return true;
                }
            });
        }

        Preference removeCustom = findPreference("pref_remove_custom");
        if (removeCustom != null) {
            removeCustom.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    onRemoveCustomClicked();
                    return true;
                }
            });
        }

        Preference exportLibrary = findPreference("pref_export_library");
        if (exportLibrary != null) {
            exportLibrary.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    startExportLibrary(CustomStorage.ExportOptions.all());
                    return true;
                }
            });
            if (exportLibrary instanceof LongClickPreference) {
                ((LongClickPreference) exportLibrary).setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        showExportChooseDialog();
                        return true;
                    }
                });
            }
        }

        Preference importLibrary = findPreference("pref_import_library");
        if (importLibrary != null) {
            importLibrary.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("application/zip");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    importLibraryLauncher.launch(Intent.createChooser(
                            intent, getString(R.string.pref_import_library_title)));
                    return true;
                }
            });
        }

        Preference libraryFolder = findPreference("pref_library_folder");
        if (libraryFolder != null) {
            libraryFolder.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    onLibraryFolderClicked();
                    return true;
                }
            });
        }
    }

    void refreshLibraryScreen() {
        ensureCustomPrefDefaults(CustomStorage.listCustomXml(this));
        updateLibraryFolderSummary();
    }

    void bindAboutPreferences(PreferenceFragmentCompat fragment) {
        activePrefs = fragment;
        setupAboutPreferences();
    }

    void bindLicensesPreferences(PreferenceFragmentCompat fragment) {
        activePrefs = fragment;
        setupLicensesPreferences();
    }

    @Override
    protected void onDestroy() {
        settingsHandler.removeCallbacks(dreamAdminRefreshRunnable);
        unregisterFpsDisplayListener();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(enableAllListener);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startLibrarySync(false);
        refreshTargetFpsList();
        refreshSharedBackgroundControls();
        refreshDreamIdleSettings();
        registerFpsDisplayListener();
    }

    @Override
    protected void onPause() {
        unregisterFpsDisplayListener();
        super.onPause();
    }

    private void setupEnableAllToggles() {
        Preference poniesToggle = findPreference("pref_ponies_toggle_all");
        if (poniesToggle != null) {
            poniesToggle.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    applyPoniesToggle();
                    return true;
                }
            });
        }
        Preference customToggle = findPreference("pref_custom_toggle_all");
        if (customToggle != null) {
            customToggle.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    applyCustomToggle();
                    return true;
                }
            });
        }
        refreshEnableAllToggles();
    }

    private void applyPoniesToggle() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        ArrayList<String> keys = builtInPonyKeys();
        PonyEnableAll.apply(prefs, keys, allHerdKeys());
        syncCheckboxWidgets(builtInPonyCategories());
        refreshEnableAllToggles();
        refreshHerdScreenSummaries();
    }

    private void applyCustomToggle() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        ArrayList<String> keys = customPonyKeys();
        PonyEnableAll.apply(prefs, keys, allHerdKeys());
        syncCheckboxWidgets(customPonyCategories());
        refreshEnableAllToggles();
        refreshHerdScreenSummaries();
    }

    private void refreshEnableAllToggles() {
        updatePoniesToggle();
        updateCustomToggle();
    }

    private void updatePoniesToggle() {
        Preference toggle = findPreference("pref_ponies_toggle_all");
        if (toggle == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        ArrayList<String> keys = builtInPonyKeys();
        PonyEnableAll.Action action = PonyEnableAll.nextAction(prefs, keys);
        switch (action) {
            case DISABLE_ALL:
                toggle.setTitle(R.string.pref_disable_all_title);
                toggle.setSummary(R.string.pref_ponies_disable_all_summary);
                break;
            case ENABLE_ALL:
                toggle.setTitle(R.string.pref_enable_all_title);
                toggle.setSummary(R.string.pref_ponies_enable_all_summary);
                break;
        }
    }

    private void updateCustomToggle() {
        Preference toggle = findPreference("pref_custom_toggle_all");
        if (toggle == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        ArrayList<String> keys = customPonyKeys();
        if (keys.isEmpty()) {
            toggle.setEnabled(false);
            toggle.setTitle(R.string.pref_disable_all_title);
            toggle.setSummary(R.string.pref_custom_toggle_empty_summary);
            return;
        }
        toggle.setEnabled(true);
        PonyEnableAll.Action action = PonyEnableAll.nextAction(prefs, keys);
        switch (action) {
            case DISABLE_ALL:
                toggle.setTitle(R.string.pref_disable_all_title);
                toggle.setSummary(R.string.pref_custom_disable_all_summary);
                break;
            case ENABLE_ALL:
                toggle.setTitle(R.string.pref_enable_all_title);
                toggle.setSummary(R.string.pref_custom_enable_all_summary);
                break;
        }
    }

    private PreferenceCategory[] builtInPonyCategories() {
        return new PreferenceCategory[] {
                (PreferenceCategory) findPreference("pref_mane6"),
                (PreferenceCategory) findPreference("pref_cmc"),
                (PreferenceCategory) findPreference("pref_royalty"),
                (PreferenceCategory) findPreference("pref_young6"),
                (PreferenceCategory) findPreference("pref_other")
        };
    }

    private PreferenceCategory[] customPonyCategories() {
        return new PreferenceCategory[] {
                (PreferenceCategory) findPreference("pref_custom")
        };
    }

    private ArrayList<String> builtInPonyKeys() {
        return AllPonies.builtInPrefKeys();
    }

    private ArrayList<String> customPonyKeys() {
        return AllPonies.customPrefKeys(this);
    }

    private void syncCheckboxWidgets(PreferenceCategory[] cats) {
        if (cats == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        PonyMixes.beginProgrammaticHerdChange();
        try {
            for (int c = 0; c < cats.length; c++) {
                PreferenceCategory cat = cats[c];
                if (cat == null) continue;
                for (int i = 0; i < cat.getPreferenceCount(); i++) {
                    Preference pref = cat.getPreference(i);
                    if (!(pref instanceof CheckBoxPreference)) continue;
                    String key = pref.getKey();
                    if (key == null) continue;
                    CheckBoxPreference checkbox = (CheckBoxPreference) pref;
                    boolean on = prefs.getBoolean(key, true);
                    if (checkbox.isChecked() != on) {
                        checkbox.setChecked(on);
                    }
                }
            }
        } finally {
            PonyMixes.endProgrammaticHerdChange();
        }
    }

    private ArrayList<String> allHerdKeys() {
        return AllPonies.allHerdKeys(this);
    }

    private void setupMixActions() {
        Preference save = findPreference("pref_save_mix");
        if (save != null) {
            save.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    showSaveMixDialog();
                    return true;
                }
            });
        }
        Preference load = findPreference("pref_load_mix");
        if (load != null) {
            load.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    showLoadMixDialog();
                    return true;
                }
            });
        }
    }

    private void showSaveMixDialog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> on = PonyMixes.captureKeys(prefs, allHerdKeys());
        int builtIn = PonyMixes.countBuiltIn(on);
        int custom = PonyMixes.countCustom(on);

        int pad = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16f, getResources().getDisplayMetrics());
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad / 2, pad, 0);

        TextView message = new TextView(this);
        message.setText(getString(R.string.pref_save_mix_message, builtIn, custom));
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        layout.addView(message);

        final EditText nameField = new EditText(this);
        nameField.setSingleLine(true);
        nameField.setHint(R.string.pref_save_mix_name_hint);
        nameField.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        nameField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        nameField.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(nameField);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.pref_save_mix_title);
        builder.setView(layout);
        builder.setNegativeButton(R.string.dialog_cancel, null);
        builder.setPositiveButton(R.string.dialog_save, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // Replaced after show so we can keep the dialog open on errors.
            }
        });
        final AlertDialog dialog = builder.create();
        dialog.show();
        android.view.View.OnClickListener saveClick = new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                onSaveMixNameEntered(dialog, nameField.getText().toString());
            }
        };
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(saveClick);
        nameField.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    onSaveMixNameEntered(dialog, nameField.getText().toString());
                    return true;
                }
                return false;
            }
        });
    }

    private void onSaveMixNameEntered(AlertDialog host, String rawName) {
        String name = PonyMixes.normalizeName(rawName);
        if (name == null) {
            showAlertDialog(getString(R.string.pref_save_mix_title),
                    getString(R.string.pref_save_mix_empty_name));
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (PonyMixes.hasName(prefs, name)) {
            confirmReplaceMix(host, name);
            return;
        }
        if (commitMix(name) && host != null) {
            host.dismiss();
        }
    }

    private void confirmReplaceMix(final AlertDialog host, final String name) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.pref_save_mix_overwrite_title);
        builder.setMessage(getString(R.string.pref_save_mix_overwrite_message, name));
        builder.setNegativeButton(R.string.dialog_cancel, null);
        builder.setPositiveButton(R.string.dialog_replace, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (commitMix(name) && host != null) {
                    host.dismiss();
                }
            }
        });
        builder.create().show();
    }

    private boolean commitMix(String name) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> on = PonyMixes.captureKeys(prefs, allHerdKeys());
        PonyMixes.SaveResult result = PonyMixes.save(prefs, name, on, PonyMixes.currentWaifu(prefs));
        if (result == PonyMixes.SaveResult.BAD_NAME) {
            showAlertDialog(getString(R.string.pref_save_mix_title),
                    getString(R.string.pref_save_mix_empty_name));
            return false;
        }
        if (result == PonyMixes.SaveResult.FULL) {
            showAlertDialog(getString(R.string.pref_save_mix_full_title),
                    getString(R.string.pref_save_mix_full_message, PonyMixes.MAX_USER_MIXES));
            return false;
        }
        int builtIn = PonyMixes.countBuiltIn(on);
        int custom = PonyMixes.countCustom(on);
        showAlertDialog(getString(R.string.pref_save_mix_ok_title),
                getString(R.string.pref_save_mix_ok_message, name, builtIn, custom));
        return true;
    }

    private void showLoadMixDialog() {
        final ArrayList<LoadMixItem> items = loadMixItems();
        CharSequence[] labels = new CharSequence[items.size()];
        for (int i = 0; i < items.size(); i++) {
            labels[i] = items.get(i).label;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.pref_load_mix_title);
        builder.setItems(labels, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                LoadMixItem item = items.get(which);
                if (item.deleteAction) {
                    showDeleteMixDialog();
                } else if (item.previousAction) {
                    applyPreviousHerd();
                } else if (item.stockKeys != null) {
                    applyStockMix(item.stockKeys);
                } else if (item.userMix != null) {
                    applyUserMix(item.userMix);
                }
            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, null);
        builder.create().show();
    }

    private void showDeleteMixDialog() {
        final List<PonyMixes.Mix> mixes = PonyMixes.loadUserMixes(
                PreferenceManager.getDefaultSharedPreferences(this));
        if (mixes.isEmpty()) {
            showAlertDialog(getString(R.string.pref_load_mix_delete_title),
                    getString(R.string.pref_load_mix_empty_delete));
            return;
        }
        CharSequence[] labels = new CharSequence[mixes.size()];
        for (int i = 0; i < mixes.size(); i++) {
            PonyMixes.Mix mix = mixes.get(i);
            labels[i] = getString(R.string.pref_load_mix_user_item, mix.name,
                    PonyMixes.countBuiltIn(mix.keys), PonyMixes.countCustom(mix.keys));
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.pref_load_mix_delete_title);
        builder.setItems(labels, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                PonyMixes.deleteById(
                        PreferenceManager.getDefaultSharedPreferences(Settings.this),
                        mixes.get(which).id);
            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, null);
        builder.create().show();
    }

    private ArrayList<LoadMixItem> loadMixItems() {
        ArrayList<LoadMixItem> items = new ArrayList<LoadMixItem>();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        ArrayList<String> herdKeys = allHerdKeys();
        if (PonyMixes.hasPreviousHerdDistinct(prefs, herdKeys)) {
            PonyMixes.Mix prev = PonyMixes.loadPreviousHerd(prefs);
            HashSet<String> live = new HashSet<String>();
            if (prev != null) {
                for (int i = 0; i < herdKeys.size(); i++) {
                    String key = herdKeys.get(i);
                    if (prev.keys.contains(key)) live.add(key);
                }
            }
            items.add(LoadMixItem.previous(getString(R.string.pref_load_mix_previous_item,
                    PonyMixes.countBuiltIn(live), PonyMixes.countCustom(live))));
        }
        AllPonies.StockGroup[] groups = AllPonies.stockGroups();
        for (int i = 0; i < groups.length; i++) {
            AllPonies.StockGroup group = groups[i];
            HashSet<String> keys = new HashSet<String>();
            for (int k = 0; k < group.keys.length; k++) {
                keys.add(group.keys[k]);
            }
            String label = getString(R.string.pref_load_mix_stock_item,
                    getString(group.titleRes));
            items.add(LoadMixItem.stock(label, keys));
        }
        List<PonyMixes.Mix> mixes = PonyMixes.loadUserMixes(prefs);
        for (int i = 0; i < mixes.size(); i++) {
            PonyMixes.Mix mix = mixes.get(i);
            String label = getString(R.string.pref_load_mix_user_item, mix.name,
                    PonyMixes.countBuiltIn(mix.keys), PonyMixes.countCustom(mix.keys));
            items.add(LoadMixItem.user(label, mix));
        }
        if (!mixes.isEmpty()) {
            items.add(LoadMixItem.delete(getString(R.string.pref_load_mix_delete_item)));
        }
        return items;
    }

    private void applyStockMix(Set<String> enabledBuiltIn) {
        PonyMixes.applyStockMix(this, enabledBuiltIn);
        syncCheckboxWidgets(builtInPonyCategories());
        refreshEnableAllToggles();
        refreshHerdScreenSummaries();
    }

    private void applyUserMix(PonyMixes.Mix mix) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        ArrayList<String> customKeys = customPonyKeys();
        int storedCustom = PonyMixes.countCustom(mix.keys);
        int liveCustom = PonyEnableAll.matchingCount(customKeys, mix.keys);
        int missing = storedCustom - liveCustom;
        PonyMixes.applyUserMix(this, mix);
        syncCheckboxWidgets(builtInPonyCategories());
        syncCheckboxWidgets(customPonyCategories());
        refreshWaifuValue();
        refreshEnableAllToggles();
        refreshHerdScreenSummaries();
        if (missing > 0) {
            showMissingCustomDialog(mix.name, missing);
        }
    }

    private void applyPreviousHerd() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        PonyMixes.Mix prev = PonyMixes.loadPreviousHerd(prefs);
        if (prev == null) return;
        ArrayList<String> customKeys = customPonyKeys();
        int storedCustom = PonyMixes.countCustom(prev.keys);
        int liveCustom = PonyEnableAll.matchingCount(customKeys, prev.keys);
        int missing = storedCustom - liveCustom;
        if (PonyMixes.applyPreviousHerd(this) == null) return;
        syncCheckboxWidgets(builtInPonyCategories());
        syncCheckboxWidgets(customPonyCategories());
        refreshWaifuValue();
        refreshEnableAllToggles();
        refreshHerdScreenSummaries();
        if (missing > 0) {
            showMissingCustomDialog(getString(R.string.pref_load_mix_previous_name), missing);
        }
    }

    private void showMissingCustomDialog(String mixName, int missing) {
        String ponyWord = missing == 1
                ? getString(R.string.pref_load_mix_missing_pony_one)
                : getString(R.string.pref_load_mix_missing_pony_many);
        showAlertDialog(getString(R.string.pref_load_mix_missing_title),
                getString(R.string.pref_load_mix_missing_message, mixName, missing, ponyWord));
    }

    private void refreshWaifuValue() {
        ListPreference waifu = (ListPreference) findPreference("pref_waifu");
        if (waifu == null) return;
        String value = PonyMixes.currentWaifu(PreferenceManager.getDefaultSharedPreferences(this));
        if (value == null) value = "";
        CharSequence[] values = waifu.getEntryValues();
        boolean known = false;
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                if (value.equals(values[i].toString())) {
                    known = true;
                    break;
                }
            }
        }
        PonyMixes.beginProgrammaticHerdChange();
        try {
            waifu.setValue(known ? value : "");
        } finally {
            PonyMixes.endProgrammaticHerdChange();
        }
    }

    private boolean isLiveHerdPreferenceKey(String key) {
        if (key == null) return false;
        if (PonyMixes.PREF_WAIFU.equals(key)) return true;
        if (key.startsWith(PonyMixes.CUSTOM_PREFIX)) return true;
        ArrayList<String> builtIn = builtInPonyKeys();
        return builtIn.contains(key);
    }

    private static final class LoadMixItem {
        final String label;
        final Set<String> stockKeys;
        final PonyMixes.Mix userMix;
        final boolean deleteAction;
        final boolean previousAction;

        private LoadMixItem(String label, Set<String> stockKeys, PonyMixes.Mix userMix,
                boolean deleteAction, boolean previousAction) {
            this.label = label;
            this.stockKeys = stockKeys;
            this.userMix = userMix;
            this.deleteAction = deleteAction;
            this.previousAction = previousAction;
        }

        static LoadMixItem stock(String label, Set<String> keys) {
            return new LoadMixItem(label, keys, null, false, false);
        }

        static LoadMixItem user(String label, PonyMixes.Mix mix) {
            return new LoadMixItem(label, null, mix, false, false);
        }

        static LoadMixItem delete(String label) {
            return new LoadMixItem(label, null, null, true, false);
        }

        static LoadMixItem previous(String label) {
            return new LoadMixItem(label, null, null, false, true);
        }
    }

    /**
     * Wires About / Licenses preferences: app blurb, version line, project
     * URLs (browser with clipboard fallback), and scrollable license text.
     */
    private void setupAboutPreferences() {
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
    }

    private void setupLicensesPreferences() {
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
            return name + " (" + PackageInfoCompat.getLongVersionCode(info) + ")";
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

    private boolean onDreamIdleTimeoutChanging(Object newValue) {
        int minutes = PonySceneController.parseDreamIdleMinutes(
                newValue != null ? newValue.toString() : null);
        if (minutes > 0 && DreamSleepAdmin.needsLockToSleep()
                && !isDreamAdminActiveForUi()) {
            showDreamLockRationale(newValue.toString());
            return false;
        }
        if (minutes == 0 && DreamSleepAdmin.needsLockToSleep()
                && isDreamAdminActiveForUi()) {
            showOptionalRevokeAdminDialog();
        }
        return true;
    }

    private void onDreamLockAdminClicked() {
        if (!DreamSleepAdmin.needsLockToSleep()) {
            return;
        }
        if (isDreamAdminActiveForUi()) {
            showRevokeAdminDialog();
            return;
        }
        showDreamLockRationale(null);
    }

    private void showDreamLockRationale(final String pendingTimeout) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.pref_dream_idle_timeout_rationale_title)
                .setMessage(R.string.pref_dream_idle_timeout_rationale_message)
                .setPositiveButton(R.string.dialog_continue, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        requestDreamLockAdmin(pendingTimeout);
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void requestDreamLockAdmin(String pendingTimeout) {
        pendingDreamIdleTimeout = pendingTimeout;
        try {
            dreamLockAdminLauncher.launch(DreamSleepAdmin.addAdminIntent(this));
        } catch (Exception e) {
            pendingDreamIdleTimeout = null;
            showAlertDialog(getString(R.string.pref_dream_device_admin_title),
                    getString(R.string.pref_dream_device_admin_summary_off));
        }
    }

    private void showRevokeAdminDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.pref_dream_device_admin_disable_title)
                .setMessage(R.string.pref_dream_device_admin_disable_message)
                .setPositiveButton(R.string.dialog_remove, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        revokeDreamLockAdmin();
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void showOptionalRevokeAdminDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.pref_dream_device_admin_disable_title)
                .setMessage(R.string.pref_dream_device_admin_revoke_unused_message)
                .setPositiveButton(R.string.dialog_remove, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        revokeDreamLockAdmin();
                    }
                })
                .setNegativeButton(R.string.dialog_keep, null)
                .show();
    }

    private void revokeDreamLockAdmin() {
        pendingDreamAdminRevoke = true;
        dreamAdminRefreshAttempts = 0;
        DreamSleepAdmin.removeAdmin(this);
        // removeActiveAdmin is async, so syncIdleTimeoutWithCapability may no-op
        // while isAdminActive is still true. Mirror the post-disable Never value now.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (PonySceneController.parseDreamIdleMinutes(
                prefs.getString(PonySceneController.PREF_DREAM_IDLE_TIMEOUT, null)) > 0) {
            prefs.edit()
                    .putString(PonySceneController.PREF_DREAM_IDLE_TIMEOUT,
                            PonySceneController.DREAM_IDLE_TIMEOUT_NEVER)
                    .commit();
        }
        refreshDreamIdleSettings();
        settingsHandler.removeCallbacks(dreamAdminRefreshRunnable);
        settingsHandler.postDelayed(dreamAdminRefreshRunnable, 250);
    }

    /**
     * Admin active state for settings UI. While a revoke is in flight,
     * {@link DreamSleepAdmin#isActive} can still be true; treat that as inactive
     * so the summary can flip to Off immediately.
     */
    private boolean isDreamAdminActiveForUi() {
        if (pendingDreamAdminRevoke) {
            if (!DreamSleepAdmin.isActive(this)) {
                pendingDreamAdminRevoke = false;
                settingsHandler.removeCallbacks(dreamAdminRefreshRunnable);
            } else {
                return false;
            }
        }
        return DreamSleepAdmin.isActive(this);
    }

    private void applyPendingDreamIdleTimeout() {
        if (pendingDreamIdleTimeout == null) return;
        if (!DreamSleepAdmin.isActive(this)) {
            pendingDreamIdleTimeout = null;
            return;
        }
        ListPreference timeout = (ListPreference) findPreference(
                PonySceneController.PREF_DREAM_IDLE_TIMEOUT);
        if (timeout != null) {
            timeout.setValue(pendingDreamIdleTimeout);
        } else {
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putString(PonySceneController.PREF_DREAM_IDLE_TIMEOUT,
                            pendingDreamIdleTimeout)
                    .commit();
        }
        pendingDreamIdleTimeout = null;
    }

    private void refreshDreamIdleSettings() {
        boolean adminActive = isDreamAdminActiveForUi();
        if (pendingDreamIdleTimeout == null && !pendingDreamAdminRevoke) {
            PonySceneController.syncIdleTimeoutWithCapability(this);
        }
        ListPreference timeout = (ListPreference) findPreference(
                PonySceneController.PREF_DREAM_IDLE_TIMEOUT);
        if (timeout != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String stored = prefs.getString(PonySceneController.PREF_DREAM_IDLE_TIMEOUT,
                    PonySceneController.DREAM_IDLE_TIMEOUT_NEVER);
            if (!stored.equals(timeout.getValue())) {
                timeout.setValue(stored);
            }
            if (DreamSleepAdmin.needsLockToSleep() && !adminActive) {
                timeout.setSummary(R.string.pref_dream_idle_timeout_summary_blocked);
            } else {
                timeout.setSummary("%s");
            }
        }
        Preference pref = findPreference("pref_dream_device_admin");
        if (pref == null) return;
        if (!DreamSleepAdmin.needsLockToSleep()) {
            pref.setSummary(R.string.pref_dream_device_admin_summary_pixel);
            pref.setEnabled(false);
            return;
        }
        pref.setEnabled(true);
        pref.setSummary(adminActive
                ? R.string.pref_dream_device_admin_summary_on
                : R.string.pref_dream_device_admin_summary_off);
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
     * Pixelation applies only while an image background is in use. Select stays
     * enabled so an imported or leftover file can be replaced without hunting
     * storage. Clear is enabled only when a background file exists.
     */
    private void refreshSharedBackgroundControls() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean wallpaper = prefs.getBoolean(PonySceneController.PREF_BACKGROUND, false);
        boolean dream = prefs.getBoolean(PonySceneController.PREF_DREAM_CUSTOM_DISPLAY, false)
                && prefs.getBoolean(PonySceneController.PREF_DREAM_BACKGROUND, false);
        boolean imageInUse = wallpaper || dream;
        Preference select = findPreference("pref_select_background");
        if (select != null) {
            select.setEnabled(true);
        }
        Preference clear = findPreference("pref_clear_background");
        if (clear != null) {
            clear.setEnabled(CustomStorage.hasLocalBackground(this));
        }
        Preference pixelation = findPreference("pref_pixelation");
        if (pixelation != null) {
            pixelation.setEnabled(imageInUse);
        }
    }

    /**
     * When turning a background-image checkbox on, refuse if app storage is
     * missing, and open the picker if the shared image file is not there yet.
     */
    private boolean onBackgroundEnableChanging(boolean enabling) {
        if (!enabling) return true;
        File filesDir = CustomStorage.localDir(this);
        if (filesDir == null) {
            showAlertDialog("Background unavailable",
                    "App storage is not available on this device right now.");
            return false;
        }
        if (!new File(filesDir, CustomStorage.BACKGROUND_NAME).exists()) {
            selectBackground();
        }
        return true;
    }

    /**
     * First time custom screen-saver display is enabled, copy the live-wallpaper
     * pony count, FPS, and background toggle so the pickers match. Later
     * re-enables keep the previous screen-saver values.
     */
    private void seedDreamDisplayOverridesIfNeeded() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        if (!prefs.contains(PonySceneController.PREF_DREAM_NUM_PONIES)) {
            editor.putInt(PonySceneController.PREF_DREAM_NUM_PONIES,
                    prefs.getInt(PonySceneController.PREF_NUM_PONIES,
                            PonySceneController.DEFAULT_NUM_PONIES));
        }
        if (!prefs.contains(PonySceneController.PREF_DREAM_TARGET_FPS)) {
            editor.putString(PonySceneController.PREF_DREAM_TARGET_FPS,
                    prefs.getString(PonySceneController.PREF_TARGET_FPS,
                            Integer.toString(PonySceneController.DEFAULT_TARGET_FPS)));
        }
        if (!prefs.contains(PonySceneController.PREF_DREAM_BACKGROUND)) {
            editor.putBoolean(PonySceneController.PREF_DREAM_BACKGROUND,
                    prefs.getBoolean(PonySceneController.PREF_BACKGROUND, false));
        }
        editor.commit();
        syncDreamDisplayWidgets();
    }

    private void syncDreamDisplayWidgets() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        NumberPickerPreference ponies =
                (NumberPickerPreference) findPreference(PonySceneController.PREF_DREAM_NUM_PONIES);
        if (ponies != null && prefs.contains(PonySceneController.PREF_DREAM_NUM_PONIES)) {
            ponies.reloadFromPersisted();
        }
        CheckBoxPreference background =
                (CheckBoxPreference) findPreference(PonySceneController.PREF_DREAM_BACKGROUND);
        if (background != null && prefs.contains(PonySceneController.PREF_DREAM_BACKGROUND)) {
            background.setChecked(prefs.getBoolean(PonySceneController.PREF_DREAM_BACKGROUND, false));
        }
        refreshTargetFpsList();
        ListPreference fps =
                (ListPreference) findPreference(PonySceneController.PREF_DREAM_TARGET_FPS);
        if (fps != null && prefs.contains(PonySceneController.PREF_DREAM_TARGET_FPS)) {
            String raw = prefs.getString(PonySceneController.PREF_DREAM_TARGET_FPS,
                    Integer.toString(PonySceneController.DEFAULT_TARGET_FPS));
            if (raw != null) {
                fps.setValue(raw);
            }
        }
        refreshTargetFpsList();
    }

    /**
     * Hides listed frame rates above this display's peak refresh. Does not
     * rewrite the stored preference; the engine clamps the effective rate.
     */
    private void refreshTargetFpsList() {
        refreshTargetFpsList((ListPreference) findPreference(PonySceneController.PREF_TARGET_FPS));
        refreshTargetFpsList((ListPreference) findPreference(PonySceneController.PREF_DREAM_TARGET_FPS));
    }

    private void refreshTargetFpsList(ListPreference pref) {
        if (pref == null) return;

        CharSequence[] entries = getResources().getTextArray(R.array.pref_target_fps_entries);
        CharSequence[] values = getResources().getTextArray(R.array.pref_target_fps_values);
        int maxFps = TargetFps.maxListedFps(this);

        ArrayList<CharSequence> outEntries = new ArrayList<CharSequence>(values.length);
        ArrayList<CharSequence> outValues = new ArrayList<CharSequence>(values.length);
        for (int i = 0; i < values.length && i < entries.length; i++) {
            int fps;
            try {
                fps = Integer.parseInt(values[i].toString().trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (TargetFps.isListedAllowed(fps, maxFps)) {
                outEntries.add(entries[i]);
                outValues.add(values[i]);
            }
        }
        if (outValues.isEmpty()) {
            outEntries.add(Integer.toString(TargetFps.LISTED[0]) + " FPS");
            outValues.add(Integer.toString(TargetFps.LISTED[0]));
        }
        pref.setEntries(outEntries.toArray(new CharSequence[outEntries.size()]));
        pref.setEntryValues(outValues.toArray(new CharSequence[outValues.size()]));

        // Numeric XML defaultValues compile as ints; ListPreference only reads
        // strings, so first attach can leave getValue() null even when the
        // SharedPreferences key is missing. Persist DEFAULT so the row and
        // dialog show 60 instead of the first entry (30).
        int chosen = TargetFps.DEFAULT;
        String raw = pref.getValue();
        if (raw == null || raw.trim().isEmpty()) {
            pref.setValue(Integer.toString(TargetFps.DEFAULT));
            raw = pref.getValue();
        }
        if (raw != null) {
            try {
                chosen = Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (chosen > maxFps) {
            pref.setSummary(getString(R.string.pref_target_fps_capped_summary, chosen, maxFps));
        } else {
            pref.setSummary("%s");
        }
    }

    private void registerFpsDisplayListener() {
        if (fpsDisplayListener != null) return;
        DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) return;
        fpsDisplayListener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                refreshTargetFpsList();
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                refreshTargetFpsList();
            }

            @Override
            public void onDisplayChanged(int displayId) {
                refreshTargetFpsList();
            }
        };
        dm.registerDisplayListener(fpsDisplayListener, settingsHandler);
    }

    private void unregisterFpsDisplayListener() {
        if (fpsDisplayListener == null) return;
        DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        if (dm != null) {
            dm.unregisterDisplayListener(fpsDisplayListener);
        }
        fpsDisplayListener = null;
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
            PonyMixes.beginProgrammaticHerdChange();
            try {
                waifu.setValue("");
            } finally {
                PonyMixes.endProgrammaticHerdChange();
            }
        }
    }
    
    private void selectBackground() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        selectBackgroundLauncher.launch(
                Intent.createChooser(intent, "Select Background"));
    }

    private boolean storageBusy;
    private boolean pendingLibrarySync;
    private boolean pendingLibrarySyncShow;

    /**
     * Writes missing {@code pref_custom_*} keys before any checkbox widgets
     * attach. Safe on every settings screen.
     */
    private void ensureCustomPrefDefaults(File[] customFiles) {
        if (customFiles == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        ArrayList<String> existing = new ArrayList<String>();
        for (int i = 0; i < customFiles.length; i++) {
            String prefKey = "pref_custom_" + customFiles[i].getName();
            if (prefs.contains(prefKey)) existing.add(prefKey);
        }
        boolean defaultOn = PonyEnableAll.defaultNewCustomEnabled(prefs, existing);
        SharedPreferences.Editor editor = null;
        for (int i = 0; i < customFiles.length; i++) {
            String prefKey = "pref_custom_" + customFiles[i].getName();
            if (!prefs.contains(prefKey)) {
                if (editor == null) editor = prefs.edit();
                editor.putBoolean(prefKey, defaultOn);
            }
        }
        if (editor != null) {
            PonyMixes.beginProgrammaticHerdChange();
            try {
                editor.commit();
            } finally {
                PonyMixes.endProgrammaticHerdChange();
            }
        }
    }

    private void ensureCustomCheckboxes(File[] customFiles) {
        PreferenceCategory customCat = (PreferenceCategory) findPreference("pref_custom");
        if (customCat == null || customFiles == null || activePrefs == null) return;
        // Commit before attaching widgets. A CheckBoxPreference with no XML
        // defaultValue starts unchecked and persists that false on attach,
        // which would overwrite defaultOn (and can win a later apply() race).
        ensureCustomPrefDefaults(customFiles);
        for (int i = 0; i < customFiles.length; i++) {
            String fileName = customFiles[i].getName();
            String prefKey = "pref_custom_" + fileName;
            if (findPreference(prefKey) == null) {
                CheckBoxPreference checkbox = new CheckBoxPreference(activePrefs.getContext());
                checkbox.setKey(prefKey);
                checkbox.setTitle(fileName);
                customCat.addPreference(checkbox);
            }
        }
    }

    private void refreshCustomPoniesUi() {
        File[] files = CustomStorage.listCustomXml(this);
        ensureCustomPrefDefaults(files);
        pruneCustomCheckboxes(files);
        ensureCustomCheckboxes(files);
        refreshWaifuList(files);
        refreshEnableAllToggles();
        refreshHerdScreenSummaries();
    }

    private void refreshHerdScreenSummaries() {
        Preference herd = findPreference("pref_herd_summary");
        Preference builtin = findPreference("pref_screen_builtin");
        Preference custom = findPreference("pref_screen_custom");
        if (herd == null && builtin == null && custom == null) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        ArrayList<String> builtInKeys = builtInPonyKeys();
        ArrayList<String> customKeys = customPonyKeys();
        int builtInOn = countEnabled(prefs, builtInKeys);
        int customOn = countEnabled(prefs, customKeys);

        if (builtin != null) {
            builtin.setSummary(getString(R.string.pref_screen_builtin_summary,
                    builtInOn, builtInKeys.size()));
        }
        if (custom != null) {
            if (customKeys.isEmpty()) {
                custom.setSummary(R.string.pref_screen_custom_summary_empty);
            } else {
                custom.setSummary(getString(R.string.pref_screen_custom_summary,
                        customOn, customKeys.size()));
            }
        }
        if (herd != null) {
            ArrayList<String> herdKeys = allHerdKeys();
            PonyMixes.Mix match = PonyMixes.matchingUserMix(prefs, herdKeys);
            if (match != null) {
                herd.setSummary(getString(R.string.pref_herd_summary_named,
                        match.name, builtInOn, customOn));
            } else if (prefs.getBoolean(PonyMixes.PREF_VIEWING_LOADED_MIX, false)) {
                AllPonies.StockGroup group = PonyMixes.matchingStockGroup(prefs);
                if (group != null) {
                    herd.setSummary(getString(R.string.pref_herd_summary_stock,
                            getString(group.titleRes), builtInOn, customOn));
                } else {
                    herd.setSummary(getString(R.string.pref_herd_summary_live,
                            builtInOn, customOn));
                }
            } else {
                herd.setSummary(getString(R.string.pref_herd_summary_live,
                        builtInOn, customOn));
            }
        }
    }

    private static int countEnabled(SharedPreferences prefs, ArrayList<String> keys) {
        if (prefs == null || keys == null) return 0;
        int n = 0;
        for (int i = 0; i < keys.size(); i++) {
            if (prefs.getBoolean(keys.get(i), true)) n++;
        }
        return n;
    }

    private void pruneCustomCheckboxes(File[] customFiles) {
        PreferenceCategory customCat = (PreferenceCategory) findPreference("pref_custom");
        if (customCat == null) return;
        HashSet<String> live = new HashSet<String>();
        if (customFiles != null) {
            for (int i = 0; i < customFiles.length; i++) {
                live.add("pref_custom_" + customFiles[i].getName());
            }
        }
        ArrayList<Preference> stale = new ArrayList<Preference>();
        for (int i = 0; i < customCat.getPreferenceCount(); i++) {
            Preference pref = customCat.getPreference(i);
            String key = pref.getKey();
            if (pref instanceof CheckBoxPreference && key != null
                    && key.startsWith("pref_custom_") && !live.contains(key)) {
                stale.add(pref);
            }
        }
        for (int i = 0; i < stale.size(); i++) {
            customCat.removePreference(stale.get(i));
        }
    }

    private void onRemoveCustomClicked() {
        File[] files = CustomStorage.listCustomXml(this);
        if (files == null || files.length == 0) {
            showAlertDialog(getString(R.string.library_remove_empty_title),
                    getString(R.string.library_remove_empty_message));
            return;
        }
        final String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            names[i] = files[i].getName();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.library_remove_title);
        builder.setItems(names, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                confirmRemoveCustom(names[which]);
            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, null);
        builder.create().show();
    }

    private void confirmRemoveCustom(final String destName) {
        boolean linked = CustomStorage.hasLibraryFolder(this);
        String message = linked
                ? getString(R.string.library_remove_confirm_linked, destName)
                : getString(R.string.library_remove_confirm_local, destName);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.library_remove_title);
        builder.setMessage(message);
        builder.setPositiveButton(R.string.library_remove_button, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                startRemoveCustom(destName);
            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, null);
        builder.create().show();
    }

    private void startRemoveCustom(final String destName) {
        if (!beginStorageWork()) return;
        new Thread(new Runnable() {
            public void run() {
                final CustomStorage.RemoveResult result = CustomStorage.removeCustomPony(
                        Settings.this, destName);
                runOnUiThread(new Runnable() {
                    public void run() {
                        storageBusy = false;
                        refreshCustomPoniesUi();
                        if (result.error != null) {
                            showAlertDialog(getString(R.string.library_remove_failed_title), result.error);
                        } else {
                            showAlertDialog(getString(R.string.library_remove_ok_title),
                                    getString(R.string.library_remove_ok_message, destName));
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
        }, "ponypaper-libremove").start();
    }

    private void startExportLibrary(CustomStorage.ExportOptions options) {
        if (options == null) options = CustomStorage.ExportOptions.all();
        if (!CustomStorage.hasExportableFiles(this, options)) {
            showAlertDialog(getString(R.string.library_export_empty_title),
                    getString(R.string.library_export_empty_message));
            return;
        }
        pendingExportOptions = options;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, defaultExportFileName());
        try {
            exportLibraryLauncher.launch(intent);
        } catch (Exception e) {
            pendingExportOptions = null;
            showAlertDialog(getString(R.string.library_export_failed_title),
                    getString(R.string.library_pick_failed_message));
        }
    }

    private void showExportChooseDialog() {
        final boolean hasPonies = CustomStorage.listCustomXml(this).length > 0;
        final boolean hasBackground = CustomStorage.hasLocalBackground(this);
        final int mixCount = PonyMixes.loadUserMixes(
                PreferenceManager.getDefaultSharedPreferences(this)).size();
        final boolean hasMixes = mixCount > 0;
        if (!hasPonies && !hasBackground && !hasMixes) {
            showAlertDialog(getString(R.string.library_export_empty_title),
                    getString(R.string.library_export_empty_message));
            return;
        }

        final ArrayList<String> labels = new ArrayList<String>();
        final ArrayList<Integer> kinds = new ArrayList<Integer>();
        if (hasPonies) {
            labels.add(getString(R.string.library_export_item_ponies,
                    CustomStorage.listCustomXml(this).length));
            kinds.add(0);
        }
        if (hasBackground) {
            labels.add(getString(R.string.library_export_item_background));
            kinds.add(1);
        }
        if (hasMixes) {
            labels.add(getString(R.string.library_export_item_mixes, mixCount));
            kinds.add(2);
        }
        final boolean[] checked = new boolean[labels.size()];
        for (int i = 0; i < checked.length; i++) checked[i] = true;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.library_export_choose_title);
        builder.setMultiChoiceItems(
                labels.toArray(new CharSequence[labels.size()]),
                checked,
                new DialogInterface.OnMultiChoiceClickListener() {
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                });
        builder.setPositiveButton(R.string.library_export_action, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                CustomStorage.ExportOptions options = new CustomStorage.ExportOptions();
                options.ponies = false;
                options.background = false;
                options.mixes = false;
                for (int i = 0; i < checked.length; i++) {
                    if (!checked[i]) continue;
                    int kind = kinds.get(i);
                    if (kind == 0) options.ponies = true;
                    else if (kind == 1) options.background = true;
                    else if (kind == 2) options.mixes = true;
                }
                if (!options.ponies && !options.background && !options.mixes) {
                    showAlertDialog(getString(R.string.library_export_nothing_selected_title),
                            getString(R.string.library_export_nothing_selected_message));
                    return;
                }
                startExportLibrary(options);
            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, null);
        builder.create().show();
    }

    private static String defaultExportFileName() {
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US);
        return "PonyPaper-library-" + fmt.format(new java.util.Date()) + ".zip";
    }

    private void exportLibraryZip(final Uri dest, final CustomStorage.ExportOptions options) {
        if (!beginStorageWork()) return;
        final CustomStorage.ExportOptions exportOptions =
                options != null ? options : CustomStorage.ExportOptions.all();
        new Thread(new Runnable() {
            public void run() {
                String error = null;
                try {
                    CustomStorage.exportZip(Settings.this, dest, exportOptions);
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
                                    getString(R.string.library_export_ok_message,
                                            formatLibraryCategoryList(exportOptions)));
                        }
                    }
                });
            }
        }, "ponypaper-export").start();
    }

    /** Human list of categories that an export options object asked for (and that exist). */
    private String formatLibraryCategoryList(CustomStorage.ExportOptions options) {
        ArrayList<String> parts = new ArrayList<String>();
        if (options.ponies) {
            int n = CustomStorage.listCustomXml(this).length;
            if (n > 0) {
                String ponyWord = n == 1
                        ? getString(R.string.library_import_pony_one)
                        : getString(R.string.library_import_pony_many);
                parts.add(getString(R.string.library_import_ponies, n, ponyWord));
            }
        }
        if (options.mixes) {
            int n = PonyMixes.loadUserMixes(
                    PreferenceManager.getDefaultSharedPreferences(this)).size();
            if (n > 0) {
                String mixWord = n == 1
                        ? getString(R.string.library_import_mix_one)
                        : getString(R.string.library_import_mix_many);
                parts.add(getString(R.string.library_import_mixes, n, mixWord));
            }
        }
        if (options.background && CustomStorage.hasLocalBackground(this)) {
            parts.add(getString(R.string.library_import_background));
        }
        return joinLibraryParts(parts);
    }

    private String joinLibraryParts(ArrayList<String> parts) {
        if (parts == null || parts.isEmpty()) {
            return getString(R.string.library_import_nothing);
        }
        if (parts.size() == 1) return parts.get(0);
        if (parts.size() == 2) {
            return getString(R.string.library_import_list_two, parts.get(0), parts.get(1));
        }
        return getString(R.string.library_import_list_three, parts.get(0), parts.get(1), parts.get(2));
    }

    /**
     * Peek the zip on a worker, then show a category checklist before applying.
     * Used by Import library and by Add custom when a zip is picked.
     */
    private void importLibraryZip(final Uri source) {
        if (!beginStorageWork()) return;
        new Thread(new Runnable() {
            public void run() {
                final CustomStorage.ZipPeekResult peek = CustomStorage.peekZip(Settings.this, source);
                runOnUiThread(new Runnable() {
                    public void run() {
                        storageBusy = false;
                        if (peek.error != null) {
                            showAlertDialog(getString(R.string.library_import_failed_title), peek.error);
                            return;
                        }
                        if (peek.isEmpty()) {
                            showAlertDialog(getString(R.string.library_import_ok_title),
                                    getString(R.string.library_import_nothing));
                            return;
                        }
                        showImportChooseDialog(source, peek);
                    }
                });
            }
        }, "ponypaper-peek").start();
    }

    private void showImportChooseDialog(final Uri source, final CustomStorage.ZipPeekResult peek) {
        final ArrayList<String> labels = new ArrayList<String>();
        final ArrayList<Integer> kinds = new ArrayList<Integer>();
        if (peek.ponyCount > 0) {
            labels.add(getString(R.string.library_import_item_ponies, peek.ponyCount));
            kinds.add(0);
        }
        if (peek.hasBackground) {
            boolean willReplaceBg = CustomStorage.hasLocalBackground(this);
            labels.add(willReplaceBg
                    ? getString(R.string.library_import_item_background_replace)
                    : getString(R.string.library_import_item_background));
            kinds.add(1);
        }
        if (peek.mixCount > 0) {
            labels.add(getString(R.string.library_import_item_mixes, peek.mixCount));
            kinds.add(2);
        }
        final boolean[] checked = new boolean[labels.size()];
        for (int i = 0; i < checked.length; i++) checked[i] = true;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.library_import_choose_title);
        // Multi-choice dialogs often hide setMessage; keep guidance in the title/items.
        builder.setMultiChoiceItems(
                labels.toArray(new CharSequence[labels.size()]),
                checked,
                new DialogInterface.OnMultiChoiceClickListener() {
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                });
        builder.setPositiveButton(R.string.library_import_action, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                CustomStorage.ImportOptions options = new CustomStorage.ImportOptions();
                options.ponies = false;
                options.background = false;
                options.mixes = false;
                for (int i = 0; i < checked.length; i++) {
                    if (!checked[i]) continue;
                    int kind = kinds.get(i);
                    if (kind == 0) options.ponies = true;
                    else if (kind == 1) options.background = true;
                    else if (kind == 2) options.mixes = true;
                }
                if (!options.ponies && !options.background && !options.mixes) {
                    showAlertDialog(getString(R.string.library_import_nothing_selected_title),
                            getString(R.string.library_import_nothing_selected_message));
                    return;
                }
                runImportLibraryZip(source, options);
            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, null);
        builder.create().show();
    }

    private void runImportLibraryZip(final Uri source, final CustomStorage.ImportOptions options) {
        if (!beginStorageWork()) return;
        final CustomStorage.ImportOptions importOptions =
                options != null ? options : CustomStorage.ImportOptions.all();
        new Thread(new Runnable() {
            public void run() {
                final CustomStorage.ZipImportResult result =
                        CustomStorage.importZip(Settings.this, source, importOptions);
                if (result.error == null && (result.poniesAdded > 0 || result.backgroundImported)) {
                    CustomStorage.SyncResult sync = CustomStorage.syncLibrary(Settings.this);
                    if (sync.permissionLost) {
                        // Keep imported local files; reconnect is separate.
                    }
                }
                if (result.error == null && result.backgroundImported) {
                    PreferenceManager.getDefaultSharedPreferences(Settings.this).edit()
                            .putString("pref_select_background",
                                    Long.toString(System.currentTimeMillis()))
                            .commit();
                }
                runOnUiThread(new Runnable() {
                    public void run() {
                        storageBusy = false;
                        showZipImportResult(result);
                        refreshCustomPoniesUi();
                        refreshSharedBackgroundControls();
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
        int mixCount = result.mixesAdded + result.mixesReplaced;
        int skippedCount = result.skipped + result.mixesSkipped;
        if (result.poniesAdded == 0 && !result.backgroundImported && mixCount == 0) {
            showAlertDialog(getString(R.string.library_import_ok_title),
                    getString(R.string.library_import_nothing));
            return;
        }
        ArrayList<String> parts = new ArrayList<String>();
        if (result.poniesAdded > 0) {
            String ponyWord = result.poniesAdded == 1
                    ? getString(R.string.library_import_pony_one)
                    : getString(R.string.library_import_pony_many);
            parts.add(getString(R.string.library_import_ponies, result.poniesAdded, ponyWord));
        }
        if (mixCount > 0) {
            String mixWord = mixCount == 1
                    ? getString(R.string.library_import_mix_one)
                    : getString(R.string.library_import_mix_many);
            parts.add(getString(R.string.library_import_mixes, mixCount, mixWord));
        }
        if (result.backgroundImported) {
            parts.add(getString(R.string.library_import_background));
        }
        String skipped = skippedCount > 0
                ? getString(R.string.library_import_skipped, skippedCount)
                : "";
        showAlertDialog(getString(R.string.library_import_ok_title),
                getString(R.string.library_import_ok_message, joinLibraryParts(parts), skipped));
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
            refreshSharedBackgroundControls();
        } catch (IOException e) {
            showAlertDialog("Failed to set background", "An I/O error occurred.");
        }
    }

    private void onClearBackgroundClicked() {
        if (!CustomStorage.hasLocalBackground(this)) {
            showAlertDialog(getString(R.string.pref_clear_background_none_title),
                    getString(R.string.pref_clear_background_none_message));
            refreshSharedBackgroundControls();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.pref_clear_background_confirm_title);
        builder.setMessage(R.string.pref_clear_background_confirm_message);
        builder.setPositiveButton(R.string.pref_clear_background_title, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                clearBackgroundNow();
            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, null);
        builder.create().show();
    }

    private void clearBackgroundNow() {
        if (!beginStorageWork()) return;
        new Thread(new Runnable() {
            public void run() {
                final CustomStorage.RemoveResult result = CustomStorage.clearBackground(Settings.this);
                runOnUiThread(new Runnable() {
                    public void run() {
                        storageBusy = false;
                        refreshSharedBackgroundControls();
                        if (result.error != null) {
                            showAlertDialog(getString(R.string.pref_clear_background_failed_title),
                                    result.error);
                        } else {
                            showAlertDialog(getString(R.string.pref_clear_background_ok_title),
                                    getString(R.string.pref_clear_background_ok_message));
                        }
                    }
                });
            }
        }, "ponypaper-clear-bg").start();
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
            refreshCustomPoniesUi();
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
            pickLibraryFolderLauncher.launch(intent);
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
                                                    result.pulled, result.pushed, result.dropped));
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
