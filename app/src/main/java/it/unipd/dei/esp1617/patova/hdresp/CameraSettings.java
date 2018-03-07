package it.unipd.dei.esp1617.patova.hdresp;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Range;
import android.util.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class is a helper class that easy the access to the most
 * requested characteristics of a camera used by the app.
 * <p>
 * It implements the Singleton pattern design.
 */
final class CameraSettings {

    /**
     * Instance of {@link CameraSettings}
     */
    private static final CameraSettings INSTANCE = new CameraSettings();

    /**
     * Common aspect ratios
     */
    private static final String[] aspectRatios = {"[4:3]", "[16:9]"};

    /**
     * A {@link CameraManager}
     */
    private static CameraManager sCameraManager;

    private CameraSettings() {
    }

    static CameraSettings getInstance(Activity activity) {
        sCameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        return INSTANCE;
    }

    /**
     * Retrieve the camera ID of the back camera (if available).
     *
     * @return camera ID of the back camera (if available)
     */
    String getBackCamera() {
        return getCamera(CameraCharacteristics.LENS_FACING_BACK);
    }

    /**
     * Obtain the resolutions that the camera supports.
     * <p>
     * For simplicity, the only resolutions that are kept are those whose
     * aspect ratio is either 16:9 or 4:3 and that have a width or height
     * of at least 1080.
     *
     * @param cameraId a {@link String} that represents a camera
     * @return a {@link CharSequence} array with all the resolutions supported by the camera
     */
    CharSequence[] getResolutions(String cameraId) {
        ArrayList<String> res = new ArrayList<>();

        try {
            CameraCharacteristics cameraCharacteristics =
                    sCameraManager.getCameraCharacteristics(cameraId);

            StreamConfigurationMap map =
                    cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (map == null) {
                throw new RuntimeException("Error on obtaining camera resolutions.");
            }

            List<Size> resolutionsSize = Arrays.asList(map.getOutputSizes(ImageFormat.JPEG));
            for (int i = 0; i < resolutionsSize.size(); i++) {
                int height = resolutionsSize.get(i).getHeight();
                int width = resolutionsSize.get(i).getWidth();
                String aspectRatio;

                if (height >= 1080 || width >= 1080) {
                    if (4 * height == width * 3) {
                        aspectRatio = aspectRatios[0];
                    } else if (16 * height == width * 9) {
                        aspectRatio = aspectRatios[1];
                    } else {
                        continue;
                    }
                } else {
                    continue;
                }

                res.add(resolutionsSize.get(i).toString() + " " + aspectRatio);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return res.toArray(new CharSequence[res.size()]);
    }

    /**
     * Check if the camera supports HDR, i.e. check if it is possible to change
     * the exposure time manually.
     *
     * @param cameraId ID of the camera
     * @return true if the camera support HDR, false otherwise
     */
    boolean isHdrSupported(String cameraId) {
        if (null == cameraId) {
            return false;
        }

        try {
            CameraCharacteristics cameraCharacteristics =
                    sCameraManager.getCameraCharacteristics(cameraId);

            Integer level =
                    cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);

            if (level != null && (level == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
                    || level == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3
                    || level == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED)) {
                return true;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Check if the camera supports auto focus.
     *
     * @param cameraId ID of the camera
     * @return {@code true} if the camera supports auto focus, {@code false} otherwise
     */
    boolean isAutoFocusSupported(String cameraId) {
        try {
            CameraCharacteristics cameraCharacteristics =
                    sCameraManager.getCameraCharacteristics(cameraId);

            if (getHardwareSupportedLevel(cameraId) != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                Float minFocusDistance = cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
                if (minFocusDistance != null && minFocusDistance != 0) {
                    return true;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Check if the flash is available for the camera.
     *
     * @param cameraId ID of the camera
     * @return {@code true} if the flash is available for the camera, {@code false} otherwise
     */
    boolean isFlashSupported(String cameraId) {
        try {
            CameraCharacteristics cameraCharacteristics =
                    sCameraManager.getCameraCharacteristics(cameraId);

            Boolean flashAvailable = cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            if (flashAvailable != null && flashAvailable) {
                return true;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Retrieve the range of exposure times supported by the camera.
     *
     * @param cameraId ID of the camera
     * @return the range of exposure times or {@code null} if the camera does not support HDR
     */
    Range<Long> getExposureTimesInterval(String cameraId) {
        try {
            if (isHdrSupported(cameraId)) {
                CameraCharacteristics cameraCharacteristics =
                        sCameraManager.getCameraCharacteristics(cameraId);

                return cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Retrieve the range of ISO supported by the camera.
     *
     * @param cameraId ID of the camera
     * @return the range of ISO supported by the camera
     */
    Range<Integer> getIsoRange(String cameraId) {
        try {
            if (isHdrSupported(cameraId)) {
                CameraCharacteristics cameraCharacteristics =
                        sCameraManager.getCameraCharacteristics(cameraId);

                return cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Retrieve the camera ID of the camera whose lens faces the parameter.
     *
     * @param lensFacing {@link CameraCharacteristics}
     * @return camera ID of the camera (if available)
     */
    private String getCamera(Integer lensFacing) {
        String camera = null;

        try {
            for (String cameraId : sCameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics =
                        sCameraManager.getCameraCharacteristics(cameraId);

                Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing.equals(lensFacing)) {
                    camera = cameraId;
                    break;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return camera;
    }

    /**
     * Retrieve the hardware supported level for the camera.
     *
     * @param cameraId ID of the camera
     * @return an integer corresponding to the level of supports. Check
     * {@link CameraCharacteristics} under #INFO_SUPPORTED_HARDWARE_LEVEL for
     * more information.
     */
    private Integer getHardwareSupportedLevel(String cameraId) {
        try {
            CameraCharacteristics cameraCharacteristics =
                    sCameraManager.getCameraCharacteristics(cameraId);

            return cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return -1;
    }
}
