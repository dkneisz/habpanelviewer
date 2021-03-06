package de.vier_bier.habpanelviewer.preferences;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.legacy.app.FragmentCompat;

import de.vier_bier.habpanelviewer.Constants;
import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.UiUtil;

public class PreferencesCamera extends PreferenceFragment implements Preference.OnPreferenceChangeListener {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences_camera);

        CheckBoxPreference cbPref = (CheckBoxPreference) findPreference(Constants.PREF_MOTION_DETECTION_PREVIEW);
        cbPref.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
        boolean value = (Boolean) o;

        if (value) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

            if (prefs.getBoolean(Constants.PREF_ALLOW_WEBRTC, false)) {
                UiUtil.showCancelDialog(getActivity(), null,
                        "Enabling preview will disable WebRTC. Continue?",
                        (dialogInterface, i) -> {
                            final SharedPreferences prefs1 = PreferenceManager.getDefaultSharedPreferences(getActivity());
                            SharedPreferences.Editor editor1 = prefs1.edit();
                            editor1.putBoolean(Constants.PREF_ALLOW_WEBRTC, false);
                            editor1.putBoolean(Constants.PREF_MOTION_DETECTION_PREVIEW, true);
                            editor1.apply();

                            CheckBoxPreference allowPreference = (CheckBoxPreference) findPreference(Constants.PREF_MOTION_DETECTION_PREVIEW);
                            allowPreference.setChecked(true);
                        }, null);

                return false;
            }

            if (needsPermissions()) {
                requestMissingPermissions();
                return false;
            }
        }

        return true;
    }

    private boolean needsPermissions() {
        return ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED;
    }

    private void requestMissingPermissions() {
        FragmentCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                Constants.REQUEST_CAMERA);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == Constants.REQUEST_CAMERA) {
            setAllowPreviewPref(grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED);
        }
    }

    private void setAllowPreviewPref(boolean allowPreview) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        if (prefs.getBoolean(Constants.PREF_MOTION_DETECTION_PREVIEW, false) != allowPreview) {
            SharedPreferences.Editor editor1 = prefs.edit();
            editor1.putBoolean(Constants.PREF_MOTION_DETECTION_PREVIEW, allowPreview);
            editor1.apply();

            CheckBoxPreference allowPreference = (CheckBoxPreference) findPreference(Constants.PREF_MOTION_DETECTION_PREVIEW);
            allowPreference.setChecked(allowPreview);
        }
    }
}

