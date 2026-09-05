package uk.cpjsmith.ponypaper;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceViewHolder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Tableau scene editor: slot list, pony/action/facing/position editors, and
 * named library load/name/duplicate. Edits auto-persist to the active JSON and,
 * when named, sync into the library entry. Structural edits bump epoch; hot
 * edits debounce-write JSON.
 */
public class TableauPreferencesFragment extends PonyPreferenceFragment {

    private static final long HOT_DEBOUNCE_MS = 400L;
    private static final String SLOT_KEY_PREFIX = "pref_tableau_slot_";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hotWriteRunnable = new Runnable() {
        @Override
        public void run() {
            flushHotWrite();
        }
    };

    private SharedPreferences prefs;
    private String activeId = "";
    private String activeName = "";
    private final ArrayList<SlotDraft> slots = new ArrayList<SlotDraft>();
    /** True when in-memory hot fields differ from the last prefs write. */
    private boolean hotDirty;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_tableau, rootKey);
    }

    @Override
    public void onStart() {
        super.onStart();
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        PonyScenes.ensureActiveScene(prefs);
        loadFromPrefs();
        wireStaticActions();
        rebuildSlotPreferences();
    }

    @Override
    public void onResume() {
        super.onResume();
        settings().setTitle(R.string.pref_screen_tableau_title);
        // Flush pending hot edits before reload so a deferred write cannot
        // persist a wiped draft after multi-window / translucent pause.
        handler.removeCallbacks(hotWriteRunnable);
        flushHotWrite();
        loadFromPrefs();
        rebuildSlotPreferences();
    }

    @Override
    public void onStop() {
        handler.removeCallbacks(hotWriteRunnable);
        flushHotWrite();
        super.onStop();
    }

    private void loadFromPrefs() {
        PonyScenes.TableauScene scene = PonyScenes.loadActiveScene(prefs);
        if (scene == null) {
            scene = PonyScenes.demoScene();
        }
        activeId = scene.id != null ? scene.id : "";
        activeName = scene.name != null ? scene.name : "";
        slots.clear();
        for (int i = 0; i < scene.slots.size(); i++) {
            slots.add(SlotDraft.from(scene.slots.get(i)));
        }
        hotDirty = false;
        updateSummary();
    }

    private void wireStaticActions() {
        Preference load = findPreference("pref_tableau_load");
        if (load != null) {
            load.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    showLoadDialog();
                    return true;
                }
            });
        }
        Preference save = findPreference("pref_tableau_save");
        if (save != null) {
            save.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    showSaveDialog(false);
                    return true;
                }
            });
        }
        Preference saveAs = findPreference("pref_tableau_save_as");
        if (saveAs != null) {
            saveAs.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    showSaveDialog(true);
                    return true;
                }
            });
        }
        Preference delete = findPreference("pref_tableau_delete");
        if (delete != null) {
            delete.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    showDeleteDialog();
                    return true;
                }
            });
        }
        Preference add = findPreference("pref_tableau_add_slot");
        if (add != null) {
            add.setSummary(getString(R.string.pref_tableau_add_slot_summary,
                    PonyScenes.MAX_SLOTS));
            add.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    addSlot();
                    return true;
                }
            });
        }
    }

    private void updateSummary() {
        Preference summary = findPreference("pref_tableau_summary");
        if (summary == null) return;
        if (activeName.length() > 0) {
            summary.setSummary(getString(R.string.pref_tableau_summary_named,
                    activeName, slots.size()));
        } else {
            summary.setSummary(getString(R.string.pref_tableau_summary_scratch,
                    slots.size()));
        }
        Preference save = findPreference("pref_tableau_save");
        if (save != null) {
            if (activeName.length() > 0) {
                save.setTitle(R.string.pref_tableau_rename_title);
                save.setSummary(R.string.pref_tableau_rename_summary);
            } else {
                save.setTitle(R.string.pref_tableau_save_title);
                save.setSummary(R.string.pref_tableau_save_summary);
            }
        }
    }

    private void rebuildSlotPreferences() {
        PreferenceCategory cat =
                (PreferenceCategory) findPreference("pref_tableau_slots_category");
        if (cat == null) return;
        // Keep Add at the end: remove dynamic slot prefs only.
        for (int i = cat.getPreferenceCount() - 1; i >= 0; i--) {
            Preference p = cat.getPreference(i);
            String key = p.getKey();
            if (key != null && key.startsWith(SLOT_KEY_PREFIX)) {
                cat.removePreference(p);
            }
        }
        int cap = TableauBuilder.estimateCapForSettings(requireContext(), prefs);
        for (int i = 0; i < slots.size(); i++) {
            final int index = i;
            SlotDraft draft = slots.get(i);
            SlotPreference pref = new SlotPreference(requireContext());
            pref.setKey(SLOT_KEY_PREFIX + i);
            pref.setPersistent(false);
            pref.setOrder(i);
            String title = getString(R.string.pref_tableau_slot_title,
                    i + 1, ponyTitle(draft.ponyKey));
            String facingLabel = facingLabel(draft.facing);
            boolean land = editingLandscape();
            String orientLabel = land
                    ? getString(R.string.pref_tableau_orient_landscape)
                    : getString(R.string.pref_tableau_orient_portrait);
            String summary = getString(R.string.pref_tableau_slot_summary,
                    orientLabel,
                    facingLabel,
                    Math.round(draft.xFor(land) * 100f),
                    Math.round(draft.yFor(land) * 100f),
                    draft.actions.size());
            if (i >= cap) {
                // Annotate + dim clipped slots; still editable / saved.
                summary = summary + getString(R.string.pref_tableau_slot_hidden_suffix);
                title = title + " ·";
                pref.setDimmed(true);
            }
            pref.setTitle(title);
            pref.setSummary(summary);
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    showSlotMenu(index);
                    return true;
                }
            });
            cat.addPreference(pref);
        }
        Preference add = findPreference("pref_tableau_add_slot");
        if (add != null) {
            add.setOrder(slots.size() + 1);
            add.setEnabled(slots.size() < PonyScenes.MAX_SLOTS);
        }
        updateSummary();
    }

    private void showSlotMenu(final int index) {
        if (index < 0 || index >= slots.size()) return;
        CharSequence[] items = new CharSequence[] {
                getString(R.string.pref_tableau_slot_edit_pony),
                getString(R.string.pref_tableau_slot_edit_actions),
                getString(R.string.pref_tableau_slot_edit_facing),
                getString(R.string.pref_tableau_slot_edit_x),
                getString(R.string.pref_tableau_slot_edit_y),
                getString(R.string.pref_tableau_slot_duplicate),
                getString(R.string.pref_tableau_slot_move_up),
                getString(R.string.pref_tableau_slot_move_down),
                getString(R.string.pref_tableau_slot_delete)
        };
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.pref_tableau_slot_menu_title, index + 1))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0: showPonyPicker(index); break;
                            case 1: showActionPicker(index); break;
                            case 2: showFacingPicker(index); break;
                            case 3: showPercentPicker(index, true); break;
                            case 4: showPercentPicker(index, false); break;
                            case 5: duplicateSlot(index); break;
                            case 6: moveSlot(index, -1); break;
                            case 7: moveSlot(index, 1); break;
                            case 8: deleteSlot(index); break;
                            default: break;
                        }
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void showPonyPicker(final int index) {
        final ArrayList<String> keys = AllPonies.allHerdKeys(requireContext());
        if (keys.isEmpty()) return;
        CharSequence[] labels = new CharSequence[keys.size()];
        int checked = 0;
        String current = slots.get(index).ponyKey;
        for (int i = 0; i < keys.size(); i++) {
            labels[i] = ponyTitle(keys.get(i));
            if (keys.get(i).equals(current)) checked = i;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.pref_tableau_slot_edit_pony)
                .setSingleChoiceItems(labels, checked,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String key = keys.get(which);
                                SlotDraft draft = slots.get(index);
                                if (key.equals(draft.ponyKey)) {
                                    dialog.dismiss();
                                    return;
                                }
                                draft.ponyKey = key;
                                draft.actions.clear();
                                String def = defaultActionId(key);
                                if (def != null) draft.actions.add(def);
                                commitStructural();
                                dialog.dismiss();
                            }
                        })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void showActionPicker(final int index) {
        SlotDraft draft = slots.get(index);
        AllPonies.ActionCatalog catalog =
                AllPonies.actionCatalog(requireContext(), draft.ponyKey);
        if (catalog == null || catalog.selectableIds.isEmpty()) {
            showAlert(getString(R.string.pref_tableau_slot_edit_actions),
                    getString(R.string.pref_tableau_actions_none));
            return;
        }
        final ArrayList<String> ids = new ArrayList<String>();
        final ArrayList<String> labels = new ArrayList<String>();
        for (int i = 0; i < catalog.idlePoseIds.size(); i++) {
            String id = catalog.idlePoseIds.get(i);
            ids.add(id);
            labels.add(getString(R.string.pref_tableau_actions_idle) + ": " + id);
        }
        for (int i = 0; i < catalog.inPlaceMoverIds.size(); i++) {
            String id = catalog.inPlaceMoverIds.get(i);
            ids.add(id);
            labels.add(getString(R.string.pref_tableau_actions_inplace) + ": " + id);
        }
        final boolean[] checked = new boolean[ids.size()];
        HashSet<String> selected = new HashSet<String>(draft.actions);
        for (int i = 0; i < ids.size(); i++) {
            checked[i] = selected.contains(ids.get(i));
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(R.string.pref_tableau_slot_edit_actions);
        builder.setMultiChoiceItems(
                labels.toArray(new CharSequence[labels.size()]),
                checked,
                new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which,
                            boolean isChecked) {
                        if (isChecked) {
                            int n = 0;
                            for (int i = 0; i < checked.length; i++) {
                                if (checked[i]) n++;
                            }
                            // Dialog already flipped checked[which]; reject over cap.
                            if (n > PonyScenes.MAX_ACTIONS_PER_SLOT) {
                                checked[which] = false;
                                ((AlertDialog) dialog).getListView()
                                        .setItemChecked(which, false);
                                Toast.makeText(requireContext(),
                                        getString(R.string.pref_tableau_actions_max_toast,
                                                PonyScenes.MAX_ACTIONS_PER_SLOT),
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                        checked[which] = isChecked;
                    }
                });
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(R.string.dialog_cancel, null);
        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(
                new android.view.View.OnClickListener() {
                    @Override
                    public void onClick(android.view.View v) {
                        ArrayList<String> next = new ArrayList<String>();
                        for (int i = 0; i < ids.size(); i++) {
                            if (checked[i]) next.add(ids.get(i));
                        }
                        if (next.isEmpty()) {
                            showAlert(getString(R.string.pref_tableau_save_invalid_title),
                                    getString(R.string.pref_tableau_save_invalid_message));
                            return;
                        }
                        if (next.size() > PonyScenes.MAX_ACTIONS_PER_SLOT) {
                            Toast.makeText(requireContext(),
                                    getString(R.string.pref_tableau_actions_max_toast,
                                            PonyScenes.MAX_ACTIONS_PER_SLOT),
                                    Toast.LENGTH_SHORT).show();
                            while (next.size() > PonyScenes.MAX_ACTIONS_PER_SLOT) {
                                next.remove(next.size() - 1);
                            }
                        }
                        slots.get(index).actions.clear();
                        slots.get(index).actions.addAll(next);
                        scheduleHotWrite();
                        rebuildSlotPreferences();
                        dialog.dismiss();
                    }
                });
    }

    private void showFacingPicker(final int index) {
        final String[] values = new String[] {
                Pony.FACING_RANDOM, Pony.FACING_LEFT, Pony.FACING_RIGHT
        };
        CharSequence[] labels = new CharSequence[] {
                getString(R.string.pref_tableau_facing_random),
                getString(R.string.pref_tableau_facing_left),
                getString(R.string.pref_tableau_facing_right)
        };
        int checked = 0;
        String current = slots.get(index).facing;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) checked = i;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.pref_tableau_slot_edit_facing)
                .setSingleChoiceItems(labels, checked,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                slots.get(index).facing = values[which];
                                scheduleHotWrite();
                                rebuildSlotPreferences();
                                dialog.dismiss();
                            }
                        })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void showPercentPicker(final int index, final boolean horizontal) {
        final boolean land = editingLandscape();
        SlotDraft draft = slots.get(index);
        int current = Math.round(
                (horizontal ? draft.xFor(land) : draft.yFor(land)) * 100f);
        if (current < 0) current = 0;
        if (current > 100) current = 100;
        final NumberPicker picker = new NumberPicker(requireContext());
        picker.setMinValue(0);
        picker.setMaxValue(100);
        picker.setValue(current);
        picker.setWrapSelectorWheel(false);
        int pad = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16f, getResources().getDisplayMetrics());
        LinearLayout wrap = new LinearLayout(requireContext());
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(picker);
        String orientLabel = land
                ? getString(R.string.pref_tableau_orient_landscape)
                : getString(R.string.pref_tableau_orient_portrait);
        new AlertDialog.Builder(requireContext())
                .setTitle(horizontal
                        ? getString(R.string.pref_tableau_slot_edit_x_orient,
                                orientLabel)
                        : getString(R.string.pref_tableau_slot_edit_y_orient,
                                orientLabel))
                .setView(wrap)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                float norm = picker.getValue() / 100f;
                                slots.get(index).setNorm(land, horizontal, norm);
                                scheduleHotWrite();
                                rebuildSlotPreferences();
                            }
                        })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    /** Settings edits follow the device orientation (not an explicit toggle). */
    private boolean editingLandscape() {
        return getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void addSlot() {
        if (slots.size() >= PonyScenes.MAX_SLOTS) {
            showAlert(getString(R.string.pref_tableau_slot_full_title),
                    getString(R.string.pref_tableau_slot_full_message,
                            PonyScenes.MAX_SLOTS));
            return;
        }
        ArrayList<String> keys = AllPonies.allHerdKeys(requireContext());
        String ponyKey = keys.isEmpty() ? "pref_ts" : keys.get(0);
        SlotDraft draft = new SlotDraft();
        draft.ponyKey = ponyKey;
        draft.xNorm = 0.5f;
        draft.yNorm = 0.5f;
        draft.facing = Pony.FACING_RANDOM;
        String def = defaultActionId(ponyKey);
        if (def != null) draft.actions.add(def);
        slots.add(draft);
        commitStructural();
    }

    private void duplicateSlot(int index) {
        if (index < 0 || index >= slots.size()) return;
        if (slots.size() >= PonyScenes.MAX_SLOTS) {
            showAlert(getString(R.string.pref_tableau_slot_full_title),
                    getString(R.string.pref_tableau_slot_full_message,
                            PonyScenes.MAX_SLOTS));
            return;
        }
        slots.add(index + 1, slots.get(index).copy());
        commitStructural();
    }

    private void moveSlot(int index, int delta) {
        int target = index + delta;
        if (index < 0 || index >= slots.size() || target < 0 || target >= slots.size()) {
            return;
        }
        SlotDraft a = slots.get(index);
        slots.set(index, slots.get(target));
        slots.set(target, a);
        commitStructural();
    }

    private void deleteSlot(int index) {
        if (index < 0 || index >= slots.size()) return;
        slots.remove(index);
        commitStructural();
    }

    private void commitStructural() {
        handler.removeCallbacks(hotWriteRunnable);
        hotDirty = false;
        PonyScenes.writeActiveStructural(prefs, activeId, buildScene());
        loadFromPrefs();
        rebuildSlotPreferences();
    }

    private void scheduleHotWrite() {
        hotDirty = true;
        handler.removeCallbacks(hotWriteRunnable);
        handler.postDelayed(hotWriteRunnable, HOT_DEBOUNCE_MS);
    }

    private void flushHotWrite() {
        if (prefs == null || !hotDirty) return;
        hotDirty = false;
        PonyScenes.writeActiveHot(prefs, buildScene());
    }

    private PonyScenes.TableauScene buildScene() {
        ArrayList<PonyScenes.TableauSlot> out =
                new ArrayList<PonyScenes.TableauSlot>(slots.size());
        for (int i = 0; i < slots.size(); i++) {
            out.add(slots.get(i).toSlot());
        }
        return new PonyScenes.TableauScene(activeId, activeName, out);
    }

    private void showSaveDialog(final boolean forceNewName) {
        if (!validateSlotsForSave()) {
            showAlert(getString(R.string.pref_tableau_save_invalid_title),
                    getString(R.string.pref_tableau_save_invalid_message));
            return;
        }
        String prefill = (!forceNewName && activeName.length() > 0) ? activeName : "";
        int pad = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16f, getResources().getDisplayMetrics());
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad / 2, pad, 0);
        final EditText nameField = new EditText(requireContext());
        nameField.setSingleLine(true);
        nameField.setHint(R.string.pref_tableau_save_name_hint);
        nameField.setText(prefill);
        nameField.setSelection(prefill.length());
        nameField.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        nameField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        layout.addView(nameField);
        int titleRes;
        if (forceNewName) {
            titleRes = R.string.pref_tableau_save_as_title;
        } else if (activeName.length() > 0) {
            titleRes = R.string.pref_tableau_rename_title;
        } else {
            titleRes = R.string.pref_tableau_save_title;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(titleRes);
        builder.setView(layout);
        builder.setNegativeButton(R.string.dialog_cancel, null);
        builder.setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Replaced after show.
            }
        });
        final AlertDialog dialog = builder.create();
        dialog.show();
        android.view.View.OnClickListener saveClick = new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                onSaveNameEntered(dialog, nameField.getText().toString(), forceNewName);
            }
        };
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(saveClick);
        nameField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    onSaveNameEntered(dialog, nameField.getText().toString(), forceNewName);
                    return true;
                }
                return false;
            }
        });
    }

    private void onSaveNameEntered(AlertDialog host, String rawName,
            boolean forceNewName) {
        String name = PonyScenes.normalizeName(rawName);
        if (name == null) {
            showAlert(getString(R.string.pref_tableau_save_title),
                    getString(R.string.pref_tableau_save_empty_name));
            return;
        }
        if (PonyScenes.nameCollidesWithOther(prefs, name, forceNewName)) {
            confirmReplaceScene(host, name, forceNewName);
            return;
        }
        if (commitSceneSave(name, forceNewName) && host != null) {
            host.dismiss();
        }
    }

    private void confirmReplaceScene(final AlertDialog host, final String name,
            final boolean forceNewName) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.pref_tableau_save_overwrite_title)
                .setMessage(getString(R.string.pref_tableau_save_overwrite_message, name))
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_replace,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (commitSceneSave(name, forceNewName) && host != null) {
                                    host.dismiss();
                                }
                            }
                        })
                .show();
    }

    private boolean commitSceneSave(String name, boolean forceNewName) {
        handler.removeCallbacks(hotWriteRunnable);
        flushHotWrite();
        ArrayList<PonyScenes.TableauSlot> list =
                new ArrayList<PonyScenes.TableauSlot>(slots.size());
        for (int i = 0; i < slots.size(); i++) {
            list.add(slots.get(i).toSlot());
        }
        PonyScenes.SaveResult result =
                PonyScenes.nameActive(prefs, name, list, forceNewName);
        if (result == PonyScenes.SaveResult.BAD_NAME) {
            showAlert(getString(R.string.pref_tableau_save_title),
                    getString(R.string.pref_tableau_save_empty_name));
            return false;
        }
        if (result == PonyScenes.SaveResult.FULL) {
            showAlert(getString(R.string.pref_tableau_save_full_title),
                    getString(R.string.pref_tableau_save_full_message,
                            PonyScenes.MAX_USER_SCENES));
            return false;
        }
        loadFromPrefs();
        rebuildSlotPreferences();
        int messageRes = forceNewName
                ? R.string.pref_tableau_save_as_ok_message
                : R.string.pref_tableau_save_ok_message;
        showAlert(getString(R.string.pref_tableau_save_ok_title),
                getString(messageRes, activeName, slots.size()));
        return true;
    }

    private boolean validateSlotsForSave() {
        if (slots.isEmpty()) return true;
        for (int i = 0; i < slots.size(); i++) {
            SlotDraft draft = slots.get(i);
            if (draft.ponyKey == null || draft.ponyKey.length() == 0) return false;
            AllPonies.ActionCatalog catalog =
                    AllPonies.actionCatalog(requireContext(), draft.ponyKey);
            if (catalog == null || catalog.selectableIds.isEmpty()) return false;
            int selectable = 0;
            for (int a = 0; a < draft.actions.size(); a++) {
                if (catalog.selectableIds.contains(draft.actions.get(a))) {
                    selectable++;
                }
            }
            if (selectable == 0) return false;
        }
        return true;
    }

    private void showLoadDialog() {
        final List<PonyScenes.TableauScene> scenes = PonyScenes.loadUserScenes(prefs);
        if (scenes.isEmpty()) {
            showAlert(getString(R.string.pref_tableau_load_title),
                    getString(R.string.pref_tableau_load_empty));
            return;
        }
        CharSequence[] labels = new CharSequence[scenes.size()];
        for (int i = 0; i < scenes.size(); i++) {
            PonyScenes.TableauScene scene = scenes.get(i);
            labels[i] = getString(R.string.pref_tableau_load_item,
                    scene.name, scene.slots.size());
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.pref_tableau_load_title)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        handler.removeCallbacks(hotWriteRunnable);
                        PonyScenes.loadSceneById(prefs, scenes.get(which).id);
                        loadFromPrefs();
                        rebuildSlotPreferences();
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void showDeleteDialog() {
        final List<PonyScenes.TableauScene> scenes = PonyScenes.loadUserScenes(prefs);
        if (scenes.isEmpty()) {
            showAlert(getString(R.string.pref_tableau_delete_title_dialog),
                    getString(R.string.pref_tableau_delete_empty));
            return;
        }
        CharSequence[] labels = new CharSequence[scenes.size()];
        for (int i = 0; i < scenes.size(); i++) {
            PonyScenes.TableauScene scene = scenes.get(i);
            labels[i] = getString(R.string.pref_tableau_load_item,
                    scene.name, scene.slots.size());
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.pref_tableau_delete_title_dialog)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        PonyScenes.deleteById(prefs, scenes.get(which).id);
                        loadFromPrefs();
                        rebuildSlotPreferences();
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private String defaultActionId(String ponyKey) {
        AllPonies.ActionCatalog catalog =
                AllPonies.actionCatalog(requireContext(), ponyKey);
        PonyAction action = AllPonies.preferredDefaultAction(catalog);
        return action != null ? action.actionId() : null;
    }

    private String ponyTitle(String ponyKey) {
        if (ponyKey == null || ponyKey.length() == 0) return "?";
        if (ponyKey.startsWith(PonyMixes.CUSTOM_PREFIX)) {
            String name = ponyKey.substring(PonyMixes.CUSTOM_PREFIX.length());
            if (name.endsWith(".xml")) {
                name = name.substring(0, name.length() - 4);
            }
            return name;
        }
        CharSequence[] entries = getResources().getTextArray(R.array.pref_waifu_entries);
        CharSequence[] values = getResources().getTextArray(R.array.pref_waifu_values);
        int n = Math.min(entries.length, values.length);
        for (int i = 0; i < n; i++) {
            if (ponyKey.equals(values[i].toString())) {
                return entries[i].toString();
            }
        }
        return ponyKey;
    }

    private String facingLabel(String facing) {
        if (Pony.FACING_LEFT.equals(facing)) {
            return getString(R.string.pref_tableau_facing_left);
        }
        if (Pony.FACING_RIGHT.equals(facing)) {
            return getString(R.string.pref_tableau_facing_right);
        }
        return getString(R.string.pref_tableau_facing_random);
    }

    private void showAlert(String title, String message) {
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.dialog_ok, null)
                .show();
    }

    private static final class SlotDraft {
        String ponyKey = "";
        float xNorm = 0.5f;
        float yNorm = 0.5f;
        float xNormLand = 0.5f;
        float yNormLand = 0.5f;
        boolean hasLandNorms;
        String facing = Pony.FACING_RANDOM;
        final ArrayList<String> actions = new ArrayList<String>();

        static SlotDraft from(PonyScenes.TableauSlot slot) {
            SlotDraft d = new SlotDraft();
            if (slot == null) return d;
            d.ponyKey = slot.ponyKey;
            d.xNorm = slot.xNorm;
            d.yNorm = slot.yNorm;
            d.hasLandNorms = slot.hasLandNorms;
            d.xNormLand = slot.xNormLand;
            d.yNormLand = slot.yNormLand;
            d.facing = slot.facing;
            if (slot.actions != null) {
                d.actions.addAll(Arrays.asList(slot.actions));
            }
            return d;
        }

        SlotDraft copy() {
            SlotDraft d = new SlotDraft();
            d.ponyKey = ponyKey;
            d.xNorm = xNorm;
            d.yNorm = yNorm;
            d.xNormLand = xNormLand;
            d.yNormLand = yNormLand;
            d.hasLandNorms = hasLandNorms;
            d.facing = facing;
            d.actions.addAll(actions);
            return d;
        }

        float xFor(boolean landscape) {
            return landscape && hasLandNorms ? xNormLand : xNorm;
        }

        float yFor(boolean landscape) {
            return landscape && hasLandNorms ? yNormLand : yNorm;
        }

        /**
         * Write one axis for the given orientation. Landscape is copy-on-write
         * from the effective (fallback) position when first customized.
         */
        void setNorm(boolean landscape, boolean horizontal, float norm) {
            if (landscape) {
                if (!hasLandNorms) {
                    xNormLand = xNorm;
                    yNormLand = yNorm;
                    hasLandNorms = true;
                }
                if (horizontal) xNormLand = norm;
                else yNormLand = norm;
            } else {
                if (horizontal) xNorm = norm;
                else yNorm = norm;
            }
        }

        PonyScenes.TableauSlot toSlot() {
            return new PonyScenes.TableauSlot(
                    ponyKey, xNorm, yNorm, hasLandNorms, xNormLand, yNormLand,
                    actions.toArray(new String[actions.size()]),
                    facing);
        }
    }

    /** Slot row that can stay clickable while dimmed for power-cap clipping. */
    private static final class SlotPreference extends Preference {
        private boolean dimmed;

        SlotPreference(android.content.Context context) {
            super(context);
        }

        void setDimmed(boolean dimmed) {
            this.dimmed = dimmed;
            notifyChanged();
        }

        @Override
        public void onBindViewHolder(PreferenceViewHolder holder) {
            super.onBindViewHolder(holder);
            holder.itemView.setAlpha(dimmed ? 0.45f : 1f);
        }
    }
}
