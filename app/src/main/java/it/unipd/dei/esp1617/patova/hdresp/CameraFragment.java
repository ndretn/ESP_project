/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.unipd.dei.esp1617.patova.hdresp;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v13.app.ActivityCompat;
import android.support.v13.app.FragmentCompat;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CameraFragment extends Fragment implements View.OnClickListener {

    /**
     * Permissions related constants.
     */
    private static final int REQUEST_CAMERA_STORAGE_PERMISSION = 200;
    private static final String[] CAMERA_STORAGE_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    /**
     * Listener that helps to load a new fragment from the activity
     * when it is necessary.
     */
    private ChangeFragment mChangeFragment;

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    /**
     * Tags for the {@link Log}.
     */
    static final String TAG = "CAMERA_FRAGMENT";

    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    /**
     * TAG object to identify the photo with the best exposure.
     * It is used later to retrieve the time exposure used by the camera.
     */
    private static final AtomicInteger mPhotoCounter = new AtomicInteger();

    /**
     * Counter used to keep track of the sequence of photos shoot.
     * Based on the counter state different actions will be performed.
     */
    private final static AtomicInteger number = new AtomicInteger(-1);

    /**
     * List of images that will be sent to the HDR algorithm.
     */
    private final static List<byte[]> images = new ArrayList<>();

    /**
     * Setup the camera with user preferences and more
     */
    private static CameraPreferences mCameraPreferences;

    /**
     * {@link CameraSettings} object to help access to camera characteristics
     */
    private static CameraSettings mCameraSettings;

    /**
     * True if the camera is opened, false otherwise.
     */
    private static boolean mCameraOpened = false;

    /**
     * File name of the JPEG files. We use this as a common prefix for all
     * the photo in the same capture sequence.
     */
    private static String mImageFileName;

    /**
     * ID of the current {@link CameraDevice}.
     */
    private static String mCameraId;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            mBackgroundHandler.post(
                    new ImageSaver(getActivity(), reader.acquireNextImage(), mImageFileName, number,
                            images));
        }
    };

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private int mState = STATE_PREVIEW;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private final Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Whether the current camera device supports Flash or not.
     */
    private boolean mFlashSupported;

    /**
     * Whether the current camera device supports AF or not.
     */
    private boolean mAutoFocusSupported;

    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;
    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            if (mCameraDevice != null) {
                closeCamera();
                mCameraDevice = null;
            }

            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {

        }

    };
    /**
     * Auto exposure set by the camera.
     * It is taken as a reference for the HDR photo.
     */
    private long mBestExposure;
    /**
     * ISO set automatically by the camera.
     * It is taken as a reference for the HDR photo.
     */
    private int mBestISO = 0;
    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private final CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_INACTIVE == afState || // some cameras do not have AF
                            CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED == afState) { // some cameras have passive AF
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };
    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            mCameraOpened = true;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraOpened = false;
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            mCameraOpened = false;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    public static CameraFragment newInstance() {
        return new CameraFragment();
    }

    /**
     * Retrieve user and camera preferences and set the corresponding behaviour of the app
     */
    private static CameraPreferences setUserPreferences(final Activity activity) {
        return CameraPreferences.getInstance(activity);
    }

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    private void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mCameraSettings = CameraSettings.getInstance(getActivity());  // keep a reference to avoid GC
        mCameraPreferences = setUserPreferences(getActivity()); // keep a reference to avoid GC

        return inflater.inflate(R.layout.camera_fragment, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        view.findViewById(R.id.picture).setOnClickListener(this);
        view.findViewById(R.id.settings).setOnClickListener(this);
        view.findViewById(R.id.gallery).setOnClickListener(this);

        mTextureView = view.findViewById(R.id.texture);
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        reOpenCamera();
    }

    private void reOpenCamera() {
        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
            // We close the camera to avoid a nasty SecurityException due to
            // lacking access privileges to the camera. It always happens when
            // the activity launch with the screen off: the SurfaceTexture is
            // already available and when we turn on the screen the app throws
            // a SecurityException.
            closeCamera();
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(int width, int height) {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics cameraCharacteristics;
            String cameraId = mCameraSettings.getBackCamera();

            cameraCharacteristics = manager.getCameraCharacteristics(cameraId);

            StreamConfigurationMap map = cameraCharacteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            // For still image captures, we choose the resolution based on
            // user settings. If it is not available, it choose the largest
            // one supported by the camera.
            int widthRes;
            int heightRes;
            int[] resolution = mCameraPreferences.getActualCameraResolution();
            if (resolution == null) {
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());
                widthRes = largest.getWidth();
                heightRes = largest.getHeight();
            } else {
                widthRes = resolution[0];
                heightRes = resolution[1];
            }

            mImageReader = ImageReader.newInstance(widthRes, heightRes,
                    ImageFormat.JPEG, /*maxImages*/2);
            mImageReader.setOnImageAvailableListener(
                    mOnImageAvailableListener, mBackgroundHandler);

            // Find out if we need to swap dimension to get the preview size relative to sensor
            // coordinate.
            int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            mSensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            boolean swappedDimensions = false;
            switch (displayRotation) {
                case Surface.ROTATION_0:
                case Surface.ROTATION_180:
                    if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                        swappedDimensions = true;
                    }
                    break;
                case Surface.ROTATION_90:
                case Surface.ROTATION_270:
                    if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                        swappedDimensions = true;
                    }
                    break;
                default:
                    Log.e(TAG, "Display rotation is invalid: " + displayRotation);
            }

            Point displaySize = new Point();
            activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
            int rotatedPreviewWidth = width;
            int rotatedPreviewHeight = height;
            int maxPreviewWidth = displaySize.x;
            int maxPreviewHeight = displaySize.y;

            if (swappedDimensions) {
                //noinspection SuspiciousNameCombination
                rotatedPreviewWidth = height;
                //noinspection SuspiciousNameCombination
                rotatedPreviewHeight = width;
                //noinspection SuspiciousNameCombination
                maxPreviewWidth = displaySize.y;
                //noinspection SuspiciousNameCombination
                maxPreviewHeight = displaySize.x;
            }

            if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                maxPreviewWidth = MAX_PREVIEW_WIDTH;
            }

            if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                maxPreviewHeight = MAX_PREVIEW_HEIGHT;
            }

            // Attempting to use too large a preview size could exceed the camera
            // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
            // garbage capture data.
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                    maxPreviewHeight, new Size(widthRes, heightRes));

            // We fit the aspect ratio of TextureView to the size of preview we picked.
            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(
                        mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(
                        mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }

            // Check if the flash is supported.
            mFlashSupported = mCameraSettings.isFlashSupported(cameraId);

            // Check if AF is supported
            mAutoFocusSupported = mCameraSettings.isAutoFocusSupported(cameraId);

            mCameraId = cameraId;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialogFragment.newInstance(
                    getString(R.string.camera_error),
                    getActivity()).show(getFragmentManager(), ErrorDialogFragment.TAG);
        }
    }

    /**
     * Opens the camera specified by {@link CameraFragment#mCameraId}.
     */
    private void openCamera(int width, int height) {
        if (mCameraDevice != null && mCameraOpened) {
            return;
        }

        // Proceed only if both camera and storage permission are granted
        if (checkCameraStoragePermissions()) {

            setUpCameraOutputs(width, height);
            configureTransform(width, height);

            Activity activity = getActivity();

            CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

            try {
                if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                    throw new RuntimeException("Time out waiting to lock camera opening.");
                }

                if (manager != null) {
                    manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
                } else {
                    throw new RuntimeException("Error on camera opening.");
                }
            } catch (CameraAccessException | SecurityException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
            }
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
                mCameraOpened = false;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
//                                setAutoFlash(mPreviewRequestBuilder);

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * Initiate a still image capture.
     */
    private void takePicture() {
        lockFocus();
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            if (mAutoFocusSupported) {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                        CameraMetadata.CONTROL_AF_TRIGGER_START);
            }
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     */
    private void captureStillPicture() {
        try {
            final Activity activity = getActivity();
            if (null == activity || null == mCameraDevice) {
                return;
            }

            // Orientation
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

            CaptureRequest captureRequest =
                    setNormalCaptureRequest(mCameraDevice, mImageReader, rotation);

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureStarted(@NonNull CameraCaptureSession session,
                                             @NonNull CaptureRequest request,
                                             long timestamp, long frameNumber) {
                    if (mCameraPreferences.isHdrOn()) {
                        showToast(getString(R.string.photo_sequence_capture_begin));
                    }
                }

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {

                    if (mCameraPreferences.isHdrOn()) {
                        if (request.getTag() != null) {
                            int photoNumber = (Integer) request.getTag();

                            if (photoNumber == 0) {
                                // Use the first photo to capture the best exposure
                                // and adjust it based on the best ISO chosen by the camera.
                                mBestExposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                                mBestISO = result.get(CaptureResult.SENSOR_SENSITIVITY);
                                mBestExposure += Math.log10((mBestISO / 100d)) / Math.log10(2d) * mBestExposure;

                                // Then begin the capture of the sequence
                                captureSequenceStillPicture(mBestExposure);
                            }
                        }
                    } else {
                        showToast(getString(R.string.photo_taken));

                        // Make sure to reset the tag value, otherwise it gets messed up!
                        if (request.getTag() != null) {
                            mPhotoCounter.set(0);
                        }
                    }

                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureRequest, CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void captureSequenceStillPicture(long bestExposure) {
        try {
            Activity activity = getActivity();
            if (activity == null || mCameraDevice == null) {
                return;
            }

            // Set the prefix for the image files
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date());
            mImageFileName = "IMG_" + timeStamp;

            // Orientation
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

            // Define the capture requests
            final int captureRequestSize = mCameraPreferences.getNumHdrPhotos();
            final CaptureRequest[] captureRequest = new CaptureRequest[captureRequestSize];
            long[] exposure = getExposureTimes(bestExposure, captureRequestSize);
            for (int i = 0; i < captureRequestSize; i++) {
                captureRequest[i] = setHdrCaptureRequest(mCameraDevice, mImageReader, rotation,
                        true, exposure[i]);
            }

            // Define the capture callback
            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {

                    if (request.getTag() != null) {
                        int photoNumber = (Integer) request.getTag();

                        if (photoNumber == captureRequestSize) {
                            // At the last photo captured inform the user that the procedure
                            // has ended and reset the counter for the next request
                            showToast(getString(R.string.photo_capture_of_x_photo_completed, captureRequestSize));
                            mPhotoCounter.set(0);
                        }
                    }

                    unlockFocus();
                }
            };

            // Start capturing the sequence
            mCaptureSession.captureBurst(Arrays.asList(captureRequest),
                    CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Compute exposures that will be used to shoot different
     * photos in order to create an HDR photo.
     * <p>
     * The idea is to choose symmetrically spaced exposure times
     * with respect to the best exposure.
     *
     * @param bestExposure the best exposure computed by the camera
     * @param numHdrPhotos the number of photos at different exposure
     * @return the values of different exposure to use
     */
    private long[] getExposureTimes(long bestExposure, int numHdrPhotos) {
        long[] exposureTimes = new long[numHdrPhotos];
        Range<Long> rangeExposure =
                mCameraSettings.getExposureTimesInterval(mCameraId);

        long lowerBound = rangeExposure.getLower();
        long upperBound = rangeExposure.getUpper();

        int photoPerSide = (numHdrPhotos - 1) / 2;
        exposureTimes[0] = bestExposure;

        for (int i = 1; i <= photoPerSide; i++) {
            long tmpExp = (long) (bestExposure * Math.pow(mCameraPreferences.getExposureStepUp(), i));

            if (tmpExp <= upperBound) {
                exposureTimes[i] = tmpExp;
            } else {
                exposureTimes[i] = upperBound;
            }

            tmpExp = (long) (bestExposure * Math.pow(mCameraPreferences.getExposureStepDown(), i));

            if (tmpExp >= lowerBound) {
                exposureTimes[photoPerSide + i] = tmpExp;
            } else {
                exposureTimes[photoPerSide + i] = lowerBound;
            }
        }

        return exposureTimes;
    }

    /**
     * Prepare the capture request to shoot a photo.
     * It supports both normal and HDR {@link CaptureRequest}s.
     *
     * @param cameraDevice the {@link CameraDevice}
     * @param imageReader  the {@link ImageReader} onto project the photo
     * @param rotation     the JPEG rotation with respect to the sensor orientation
     * @param hdrOn        set to true in order to set the {@link CaptureRequest} as to be suitable to shoot
     *                     an HDR photo
     * @param exposureTime the exposure time to shoot the photo with
     * @return the {@link CaptureRequest}
     */
    private CaptureRequest setHdrCaptureRequest(@NonNull CameraDevice cameraDevice,
                                                @NonNull ImageReader imageReader,
                                                int rotation,
                                                boolean hdrOn,
                                                long exposureTime) {
        CaptureRequest.Builder captureBuilder = null;
        try {
            captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());

            if (mAutoFocusSupported) {
                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            }

            captureBuilder.setTag(mPhotoCounter.getAndIncrement());

            if (!hdrOn && mFlashSupported) {
                setAutoFlash(captureBuilder);
            }

            if (hdrOn) {
                // Set AE OFF otherwise it will override the custom values
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);

                // Set custom AE
                captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime);

                // Set minimum ISO
                Range<Integer> rangeIso = mCameraSettings.getIsoRange(mCameraSettings.getBackCamera());
                captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, rangeIso.getLower());
            }

            // Set orientation
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
            ErrorDialogFragment.newInstance(
                    getString(R.string.camera_error_generic),
                    getActivity()).show(getFragmentManager(), ErrorDialogFragment.TAG);
        }

        return captureBuilder != null ? captureBuilder.build() : null;
    }

    /**
     * Prepare the {@link CaptureRequest} to shoot a normal photo.
     *
     * @param cameraDevice the {@link CameraDevice}
     * @param imageReader  the {@link ImageReader} onto project the photo
     * @param rotation     the JPEG rotation with respect to the sensor orientation
     * @return the {@link CaptureRequest} set as desired
     */
    private CaptureRequest setNormalCaptureRequest(CameraDevice cameraDevice,
                                                   ImageReader imageReader,
                                                   int rotation) {
        return setHdrCaptureRequest(cameraDevice, imageReader, rotation, false, 0L);
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
//            setAutoFlash(mPreviewRequestBuilder);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.gallery: {
                GalleryFragment galleryFragment = GalleryFragment.newInstance();
                Bundle bundle = new Bundle();
                bundle.putInt(GalleryFragment.FROM_CAMERA, 1);
                galleryFragment.setArguments(bundle);
                mChangeFragment.changeFragment(galleryFragment, GalleryFragment.TAG);
                break;
            }
            case R.id.picture: {
                takePicture();
                break;
            }
            case R.id.settings: {
                Activity activity = getActivity();
                if (null != activity) {
                    Intent intent = new Intent(activity, SettingsActivity.class);
                    startActivity(intent);
                }
                break;
            }
        }
    }

    /**
     * If the camera supports the flash, set it.
     *
     * @param requestBuilder the {@link CaptureRequest.Builder}.
     */
    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    private static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mChangeFragment = (ChangeFragment) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement ChangeFragment listener");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_STORAGE_PERMISSION) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Camera or storage permission NOT granted.");
                    showMissingPermissionError(getString(R.string.camera_storage_permissions_not_granted_error));
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Check if you should explain to the user why the app
     * needs some permissions or not.
     *
     * @return {@code true} if you should provide the user with
     * some explanation, {@code false} otherwise.
     */
    private boolean shouldShowRationale() {
        for (String permission : CAMERA_STORAGE_PERMISSIONS) {
            if (FragmentCompat.shouldShowRequestPermissionRationale(this, permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Request both camera and storage permissions.
     * <p>
     * Eventually show a dialog to the user explaining why
     * the app needs those permissions.
     */
    private void requestCameraStoragePermission() {
        if (shouldShowRationale()) {
            ConfirmationDialogFragment.newInstance(
                    getString(R.string.camera_storage_request_permissions_rationale),
                    getActivity(),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(
                                    getFragmentManager().findFragmentById(R.id.container),
                                    CAMERA_STORAGE_PERMISSIONS,
                                    REQUEST_CAMERA_STORAGE_PERMISSION);
                        }
                    }).show(getFragmentManager(), ConfirmationDialogFragment.TAG);
        } else {
            FragmentCompat.requestPermissions(this,
                    CAMERA_STORAGE_PERMISSIONS, REQUEST_CAMERA_STORAGE_PERMISSION);
        }
    }

    /**
     * Tells whether both camera and storage permissions are granted to this app.
     */
    private boolean checkCameraStoragePermissions() {
        for (String permission : CAMERA_STORAGE_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(getActivity(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                requestCameraStoragePermission();
                return false;
            }
        }

        return true;
    }

    /**
     * When permissions are definitely denied by the user,
     * show an error message and close the app.
     */
    private void showMissingPermissionError(String message) {
        Activity activity = getActivity();
        if (activity != null) {
            Toast.makeText(activity,
                    message,
                    Toast.LENGTH_LONG)
                    .show();
            activity.finish();
        }
    }
}
