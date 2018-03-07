package it.unipd.dei.esp1617.patova.hdresp;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.media.ExifInterface;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.AlignMTB;
import org.opencv.photo.CalibrateDebevec;
import org.opencv.photo.CalibrateRobertson;
import org.opencv.photo.MergeDebevec;
import org.opencv.photo.MergeRobertson;
import org.opencv.photo.Photo;
import org.opencv.photo.TonemapDrago;
import org.opencv.photo.TonemapDurand;
import org.opencv.photo.TonemapMantiuk;
import org.opencv.photo.TonemapReinhard;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;


class Hdr implements Runnable {

    private static final String TAG = "HDR";
    private final boolean align;
    private Mat matTime;
    private final List<byte[]> imagesByte;
    private List<Mat> imagesMat;
    private final int hdrAlg;
    private final int toneAlg;
    private final Mat resp;
    private final Mat output;
    private final Context mContext;

    Hdr(Context context, List<byte[]> imagesArrayByte) {

        CameraPreferences cameraPreferences = CameraPreferences.getInstance(context);

        mContext = context;
        imagesByte = imagesArrayByte;

        // Retrieve user preferences
        align = cameraPreferences.getPhotoAlignment();
        hdrAlg = cameraPreferences.getHdrAlgorithm();
        toneAlg = cameraPreferences.getToneMappingAlgorithm();

        resp = new Mat();
        output = new Mat();
    }

    public void run() {
        AlignMTB aligner;
        CalibrateDebevec cD;
        CalibrateRobertson cR;
        MergeDebevec mD;
        MergeRobertson mR;
        TonemapDurand tDu;
        TonemapDrago tDr;
        TonemapMantiuk tM;
        TonemapReinhard tR;

        // Start a progress bar
        startProgressBar();

        // Convert the images to the appropriate Mat for OpenCV
        fromArrayToMat(imagesByte);

        // Start the actual HDR algorithm
        Bitmap bmp;
        long totalTime;
        try {
            long start = System.currentTimeMillis();
            Log.d(TAG, "Allineamento...");
            if (align) {
                aligner = Photo.createAlignMTB();
                aligner.process(imagesMat, imagesMat);
            }
            long stopA = System.currentTimeMillis();
            Log.d(TAG, "Allineamento finito in : " + (int) (stopA - start) + "ms");
            Log.d(TAG, "Algoritmo HDR " + hdrAlg);
            Log.d(TAG, "Calibrazione...");
            if (hdrAlg == 1) {
                cD = Photo.createCalibrateDebevec(70,100f,false);
                cD.process(imagesMat, resp, matTime);
            } else {
                cR = Photo.createCalibrateRobertson(5,0.5f);
                cR.process(imagesMat, resp, matTime);
            }
            long stopC = System.currentTimeMillis();
            Log.d(TAG, "Calibrazione finita in : " + (int) (stopC - stopA) + "ms");
            Log.d(TAG, "Merge...");
            if (hdrAlg == 1) {
                mD = Photo.createMergeDebevec();
                mD.process(imagesMat, output, matTime, resp);
            } else {
                mR = Photo.createMergeRobertson();
                mR.process(imagesMat, output, matTime, resp);
            }
            long stopM = System.currentTimeMillis();
            Log.d(TAG, "Merge finito in : " + (int) (stopM - stopC) + "ms");
            Log.d(TAG, "Algoritmo Tonemap " + toneAlg);
            Log.d(TAG, "Tonemap...");
            switch (toneAlg) {
                case 1:
                    tDr = Photo.createTonemapDrago(1.3f,1.0f,0.85f);
                    tDr.process(output, output);
                    break;
                case 2:
                    tDu = Photo.createTonemapDurand(1.3f,4f,1.0f,2f,2f);
                    tDu.process(output, output);
                    break;
                case 3:
                    tM = Photo.createTonemapMantiuk(0.9f,0.75f,0.9f);
                    tM.process(output, output);
                    break;
                case 4:
                    tR = Photo.createTonemapReinhard(0.8f,0.0f,1f,0f);
                    tR.process(output, output);
                    break;
            }
            long stopT = System.currentTimeMillis();
            Log.d(TAG, "Tonemap finito in : " + (int) (stopT - stopM) + "ms");
            Imgproc.cvtColor(output, output, Imgproc.COLOR_BGR2RGB);
            long stopCC = System.currentTimeMillis();
            Log.d(TAG, "BGR2RGB in : " + (int) (stopCC - stopT) + "ms");
            Core.multiply(output, new Scalar(255.0, 255.0, 255.0), output);
            long stopMM = System.currentTimeMillis();
            Log.d(TAG, "Multiply in : " + (int) (stopMM - stopCC) + "ms");
            output.convertTo(output, CvType.CV_8UC3);
            long stopCCC = System.currentTimeMillis();
            Log.d(TAG, "Conversion in : " + (int) (stopCCC - stopMM) + "ms");

            totalTime = stopCCC - start;

            bmp = null;
            start = System.currentTimeMillis();
            try {
                bmp = Bitmap.createBitmap(output.cols(), output.rows(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(output, bmp);
            } catch (CvException e) {
                Log.d(TAG, e.getMessage());
            }
            long s1 = System.currentTimeMillis();
            Log.d(TAG, "MatToBitMap in : " + (int) (s1 - start) + "ms");

            totalTime += s1 - start;

            // Save the output in a file
            Log.d(TAG, "Start saving... ");
            long startSave = System.currentTimeMillis();
            saveBitmapToFile(bmp);
            long stopSave = System.currentTimeMillis();
            Log.d(TAG, "Salvata in : " + (int) (stopSave - startSave) + "ms");

            // Compute total execution time
            totalTime += stopSave - startSave;
            Log.i(TAG, "Tempo totale: " + (int) totalTime + "ms");
        } catch (Exception e) {
            showToast(mContext.getString(R.string.hdr_error_generic_message_error));
        } finally {
            // Close progress bar
            dismissProgressBar();

            // Clear and release all the resources used
            clearResources();
        }
    }

    /**
     * Convert the input images into a list of Mat images.
     * Also retrieve the exposure times and convert the into a Mat file.
     *
     * @param images the input images as a {@link List} of {@code byte[]}
     */
    private void fromArrayToMat(List<byte[]> images) {
        imagesMat = new ArrayList<>();
        float[] timesList = new float[images.size()];

        // Convert images and retrieve exposure times
        try {
            for (int i = 0; i < images.size(); i++) {
                byte[] imageByte = images.get(i);
                Mat image = Imgcodecs.imdecode(new MatOfByte(imageByte), Imgcodecs.CV_LOAD_IMAGE_UNCHANGED);
                imagesMat.add(i, image);

                try {
                    String exp;
                    ExifInterface ex = new ExifInterface(new ByteArrayInputStream(imageByte));
                    exp = ex.getAttribute(ExifInterface.TAG_EXPOSURE_TIME);
                    Log.d(TAG, exp);
                    timesList[i] = Float.valueOf(exp);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            showToast(mContext.getString(R.string.hdr_error_prepare_images_error));

            // Remember to dismiss the progress bar in case of error
            dismissProgressBar();
        }

        // Convert exposure times into a Mat file
        matTime = new MatOfFloat(timesList);
    }

    /**
     * Save a {@link Bitmap} to a file in the internal memory of the device.
     *
     * @param bitmap the bitmap to save as a file.
     */
    private void saveBitmapToFile(Bitmap bitmap) {

        FileOutputStream outputStream = null;
        String filename = "HDR_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                + ".jpg";
        File storageDirectory = CameraPreferences.getAppDir();
        final File destination = new File(storageDirectory, filename);

        if (!storageDirectory.exists()) {
            if (!storageDirectory.mkdirs()) {
                Log.e(TAG, "Failed to create directory");
                showToast(mContext.getString(R.string.hdr_error_saving_final_image));
                dismissProgressBar();
                clearResources();
                return;
            }
        }

        try {
            outputStream = new FileOutputStream(destination);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);

            // Update Android Media Provider
            triggerMediaScanner(destination.toString());

            // Show to the user the image
            showImage(destination);

            // Show where the image is saved
            showToast(mContext.getString(R.string.path_hdr_image, destination.toString()));
        } catch (FileNotFoundException e) {
            showToast(mContext.getString(R.string.hdr_error_saving_final_image));
            Log.e(TAG, e.getMessage());
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                    Log.d(TAG, "OK!");
                }
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }

    /**
     * Clear and release all the resources used.
     */
    private void clearResources() {
        output.release();
        resp.release();
        for (int i = 0; i < imagesMat.size(); i++) {
            imagesMat.get(i).release();
        }
        imagesMat.clear();
        imagesByte.clear();
    }

    /**
     * Trigger the Android Media Scanner to scan the new file created.
     * It, hopefully, ensures that other apps are notified about
     * the new file.
     *
     * @param path path of the new file created
     */
    private void triggerMediaScanner(String path) {
        File file = new File(path);
        Uri uri = Uri.fromFile(file);
        Intent scanFileIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri);
        mContext.sendBroadcast(scanFileIntent);
    }

    /**
     * Show an image with a proper view.
     *
     * @param file The image file to show
     */
    private void showImage(File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = FileProvider.getUriForFile(
                mContext,
                mContext.getApplicationContext().getPackageName() + ".provider",
                file
        );
        intent.setDataAndType(uri, "image/jpeg");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        mContext.startActivity(intent);
    }

    /**
     * Start a dialog with a progress bar.
     */
    private void startProgressBar() {
        final Activity activity = (Activity) mContext;
        final FragmentManager fragmentManager = activity.getFragmentManager();
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ProgressBarFragment loadingFragment = ProgressBarFragment.newInstance();
                loadingFragment.setCancelable(false);
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                if (!loadingFragment.isAdded()) {
                    fragmentTransaction.add(loadingFragment, ProgressBarFragment.TAG);
                    fragmentTransaction.addToBackStack(ProgressBarFragment.TAG);
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        fragmentTransaction.commitAllowingStateLoss();
                    }
                }
            }
        });
    }

    /**
     * Dismiss a dialog with a progress bar.
     */
    private void dismissProgressBar() {
        final Activity activity = (Activity) mContext;
        final FragmentManager fragmentManager = activity.getFragmentManager();
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ProgressBarFragment loadingFragment =
                        (ProgressBarFragment) fragmentManager.findFragmentByTag(ProgressBarFragment.TAG);
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                if (loadingFragment != null) {
                    Log.d(TAG, loadingFragment.getTag());
                    loadingFragment.dismissAllowingStateLoss();
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        fragmentTransaction.commitAllowingStateLoss();
                    }
                }
            }
        });
    }

    /**
     * Show a toast.
     */
    private void showToast(final String message) {
        final Activity activity = (Activity) mContext;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
