package it.unipd.dei.esp1617.patova.hdresp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.SparseArray;

import java.io.File;

/**
 * This class it's a helper class that helps to set up
 * the application based on the user preferences (and more).
 * <p>
 * It implements the Singleton pattern design.
 */
final class CameraPreferences {

    private static final CameraPreferences INSTANCE = new CameraPreferences();

    /**
     * Exposure values with respect to stops
     */
    private static final SparseArray<Double> EXPOSURE_VALUES = new SparseArray<>();
    private static final String APP_DIR = "HDRESP";
    private static String mActualCameraResolution;
    private static int mHdrOn;
    private static int mNumHdrPhotos;
    private static int mHdrAlgorithm;
    private static int mToneMappingAlgorithm;
    private static Double mExposureStepUp;
    private static Double mExposureStepDown;
    private static boolean mSaveIntermediatePhotos;
    private static boolean mPhotoAlignment;
    private static SharedPreferences mSharedPreferences;

    static {
        EXPOSURE_VALUES.append(1, 2d); // +1 stop
        EXPOSURE_VALUES.append(2, 1.6); // +2/3 stop
        EXPOSURE_VALUES.append(3, 1.25); // +1/3 stop
        EXPOSURE_VALUES.append(-1, 0.5); // -1 stop
        EXPOSURE_VALUES.append(-2, 0.64); // -2/3 stop
        EXPOSURE_VALUES.append(-3, 0.8); // -1/3 stop
    }

    private CameraPreferences() {
    }

    static CameraPreferences getInstance(Context context) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        setHdrOn();
        setNumHdrPhotos();
        setSaveIntermediatePhotos();
        setCameraResolution();
        setExposureSteps();
        setHdrAlgorithm();
        setToneMappingAlgorithm();
        setPhotoAlignment();
        return INSTANCE;
    }

    /**
     * Set in the {@link SharedPreferences} the choice of the user on the alignment
     * of the photos.
     */
    static void setPhotoAlignment() {
        mPhotoAlignment = mSharedPreferences.getBoolean(SettingsFragment.PHOTO_ALIGNMENT, true);
    }

    /**
     * Set in the {@link SharedPreferences} the choice of the user on the camera resolution.
     */
    static void setCameraResolution() {
        mActualCameraResolution =
                mSharedPreferences.getString(SettingsFragment.BACK_CAMERA_RESOLUTION_SETTINGS, null);
    }

    /**
     * Set in the {@link SharedPreferences} the choice of the user on the HDR algorithm.
     */
    static void setHdrAlgorithm() {
        mHdrAlgorithm = Integer.valueOf(
                mSharedPreferences.getString(SettingsFragment.HDR_ALGORITHM, "1")
        );
    }

    /**
     * Set in the {@link SharedPreferences} the choice of the user on the tone mapping algorithm.
     */
    static void setToneMappingAlgorithm() {
        mToneMappingAlgorithm = Integer.valueOf(
                mSharedPreferences.getString(SettingsFragment.TONE_MAPPING_ALGORITHM, "1")
        );
    }

    /**
     * Set in the {@link SharedPreferences} if the HDR is ON or OFF.
     */
    private static void setHdrOn() {
        mHdrOn = Integer.valueOf(mSharedPreferences.getString(SettingsFragment.HDR_AVAILABLE, "0"));
    }

    /**
     * Set in the {@link SharedPreferences} the choice of the user on the exposure step.
     */
    static void setExposureSteps() {
        int exposureStep = Integer.parseInt(
                mSharedPreferences.getString(SettingsFragment.EXPOSURE_STEP_SETTINGS, "1"));
        mExposureStepUp = EXPOSURE_VALUES.get(exposureStep);
        mExposureStepDown = EXPOSURE_VALUES.get(-exposureStep);
    }

    /**
     * Set in the {@link SharedPreferences} if the user has chosen to save the intermediate photos.
     */
    static void setSaveIntermediatePhotos() {
        mSaveIntermediatePhotos = mHdrOn == 1 &&
                mSharedPreferences.getBoolean(SettingsFragment.HDR_DELETE_INTERMEDIATE_PHOTOS, false);
    }

    /**
     * Set in the {@link SharedPreferences} the choice of the user on the number of photos of the sequence.
     */
    static void setNumHdrPhotos() {
        mNumHdrPhotos = mHdrOn == 1 ?
                Integer.parseInt(mSharedPreferences.getString(SettingsFragment.HDR_NUM_PHOTOS, "3")) :
                1;
    }

    /**
     * Retrieve the directory that the app uses in the internal memory.
     *
     * @return the {@link File} that represents the directory
     */
    static File getAppDir() {
        return Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES + File.separator + APP_DIR);
    }

    /**
     * Retrieve the user settings on the alignment.
     *
     * @return {@code true} if the user wants to align the photos, {@code false} otherwise.
     */
    boolean getPhotoAlignment() {
        return mPhotoAlignment;
    }

    /**
     * Retrieve the camera resolution set by the user.
     *
     * @return an array of {@code int} where in the first component is saved the y and in the second
     * the x.
     */
    int[] getActualCameraResolution() {

        if (mActualCameraResolution != null) {
            String[] res = mActualCameraResolution.split("[\\[\\]: x]");
            int[] resolution = new int[2];
            resolution[0] = Integer.valueOf(res[0]);
            resolution[1] = Integer.valueOf(res[1]);
            return resolution;
        }

        return null;
    }

    /**
     * Retrieve whether the user has enabled the HDR or not.
     *
     * @return {@code true} if the user has activated the HDR, {@code false} otherwise.
     */
    boolean isHdrOn() {
        return mHdrOn == 1;
    }

    /**
     * Retrieve the number of photos in the sequence set by the user.
     *
     * @return the number of photos in the sequence.
     */
    int getNumHdrPhotos() {
        return mNumHdrPhotos;
    }

    /**
     * Retrieve the HDR algorithm set by the user.
     *
     * @return the HDR algorithm.
     */
    int getHdrAlgorithm() {
        return mHdrAlgorithm;
    }

    /**
     * Retrieve the tone mapping algorithm set by the user.
     *
     * @return the tone mapping algorithm.
     */
    int getToneMappingAlgorithm() {
        return mToneMappingAlgorithm;
    }

    /**
     * Retrieve the step-up of the exposure set by the user.
     *
     * @return the step-up in the exposure.
     */
    Double getExposureStepUp() {
        return mExposureStepUp;
    }

    /**
     * Retrieve the step-down of the exposure set by the user.
     *
     * @return the step-down in the exposure.
     */
    Double getExposureStepDown() {
        return mExposureStepDown;
    }

    /**
     * Retrieve whether the user has chosen to save the photos of the sequence or not.
     *
     * @return {@code} true if the user has chosen to save the photos of the sequence, {@code} false
     * otherwise.
     */
    boolean getSaveIntermediatePhotos() {
        return mSaveIntermediatePhotos;
    }
}
