package it.unipd.dei.esp1617.patova.hdresp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;

public class SettingsFragment extends PreferenceFragment {

    private static final String PREF_CAMERA_CATEGORY = "pref_camera_category";
    private static final String PREF_HDR_CATEGORY = "pref_category_hdr";
    static final String BACK_CAMERA_RESOLUTION_SETTINGS = "pref_resolution_back_camera";
    static final String HDR_NUM_PHOTOS = "pref_hdr_number_photo";
    static final String HDR_AVAILABLE = "pref_hdr_available";
    static final String HDR_DELETE_INTERMEDIATE_PHOTOS = "pref_hdr_save_intermediate_photo";
    static final String EXPOSURE_STEP_SETTINGS = "pref_hdr_exposure_step";
    static final String HDR_ALGORITHM = "pref_hdr_algorithm";
    static final String TONE_MAPPING_ALGORITHM = "pref_hdr_tone_mapping";
    static final String PHOTO_ALIGNMENT = "pref_hdr_photo_alignment";

    /**
     * This listener keeps settings consistent across the all application.
     * When the user change a setting, it configures the app accordingly.
     */
    private final SharedPreferences.OnSharedPreferenceChangeListener mOnSharedPreferenceChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                    if (key.equals(BACK_CAMERA_RESOLUTION_SETTINGS)) {
                        Preference preference = findPreference(BACK_CAMERA_RESOLUTION_SETTINGS);
                        preference.setSummary(sharedPreferences.getString(key,
                                getString(R.string.pref_resolution_back_camera_summary)));
                        CameraPreferences.setCameraResolution();
                    }
                    if (key.equals(HDR_NUM_PHOTOS)) {
                        CameraPreferences.setNumHdrPhotos();
                    }
                    if (key.equals(HDR_DELETE_INTERMEDIATE_PHOTOS)) {
                        CameraPreferences.setSaveIntermediatePhotos();
                    }
                    if (key.equals(EXPOSURE_STEP_SETTINGS)) {
                        CameraPreferences.setExposureSteps();
                    }
                    if (key.equals(HDR_ALGORITHM)) {
                        CameraPreferences.setHdrAlgorithm();
                    }
                    if (key.equals(TONE_MAPPING_ALGORITHM)) {
                        CameraPreferences.setToneMappingAlgorithm();
                    }
                    if (key.equals(PHOTO_ALIGNMENT)) {
                        CameraPreferences.setPhotoAlignment();
                    }
                }
            };

    /**
     * Set dynamically the data of a ListPreference.
     *
     * @param listPreference the ListPreference to populate
     * @param entries        the entries to populate the ListPreference with
     */
    private static void setListPreferenceData(ListPreference listPreference, CharSequence[] entries) {
        listPreference.setEntries(entries);
        listPreference.setEntryValues(entries);
        listPreference.setDefaultValue(entries[0].toString());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);

        SharedPreferences sharedPreferences = getDefaultSharedPreferences(getActivity());

        CameraSettings cameraSettings = CameraSettings.getInstance(getActivity());

        // Edit HDR preferences based on the actual support of HDR functionality
        // of the device.
        if (!cameraSettings.isHdrSupported(cameraSettings.getBackCamera())) {
            sharedPreferences.edit().putString(HDR_AVAILABLE, "0").apply();

            // Remove camera resolution settings
            PreferenceCategory preference = (PreferenceCategory) findPreference(PREF_CAMERA_CATEGORY);
            preference.removePreference(findPreference(BACK_CAMERA_RESOLUTION_SETTINGS));

            // Set the appropriate support for HDR by the camera
            Preference preferenceHdrAvailable = findPreference(HDR_AVAILABLE);
            preferenceHdrAvailable.setTitle(getString(R.string.pref_hdr_on_disabled_title));
            preferenceHdrAvailable.setSummary(getString(R.string.pref_both_camera_does_not_support_hdr));

            // Remove HDR settings related to the camera
            PreferenceCategory hdrCategory = (PreferenceCategory) findPreference(PREF_HDR_CATEGORY);
            hdrCategory.removePreference(findPreference(HDR_NUM_PHOTOS));
            hdrCategory.removePreference(findPreference(EXPOSURE_STEP_SETTINGS));
            hdrCategory.removePreference(findPreference(HDR_DELETE_INTERMEDIATE_PHOTOS));
        } else {
            // Populate the ListPreference with the resolutions supported by the
            // back camera of the device.
            final ListPreference listPreferenceBackCamera =
                    (ListPreference) findPreference(BACK_CAMERA_RESOLUTION_SETTINGS);

            setListPreferenceData(listPreferenceBackCamera,
                    cameraSettings.getResolutions(cameraSettings.getBackCamera())
            );
            listPreferenceBackCamera.setSummary(
                    sharedPreferences.getString(BACK_CAMERA_RESOLUTION_SETTINGS,
                            getString(R.string.pref_resolution_back_camera_summary))
            );
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);
    }

    @Override
    public void onPause() {
        super.onPause();

        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);
    }
}
