package it.unipd.dei.esp1617.patova.hdresp;

import android.content.Context;
import android.content.Intent;
import android.media.Image;
import android.net.Uri;
import android.util.Log;

import org.opencv.core.Mat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Saves a JPEG {@link Image} into the specified {@link File}.
 * <p>
 * If enabled, it also saves the exposure information in another
 * {@link File}.
 */
class ImageSaver implements Runnable {

    /**
     * TAG for {@code Log}
     */
    private static final String TAG = "ImageSaver";

    /**
     * Enable/disable log
     */
    private static final boolean DEBUG = true;

    /**
     * Reference to {@code CameraPreferences}
     */
    private static CameraPreferences sCameraPreferences;

    /**
     * The JPEG image
     */
    private final Image mImage;

    /**
     * The file we save the image into.
     */
    private File mImageFile;

    /**
     * The image file name
     */
    private final String mImageFileName;

    /**
     * The {@link Context} of the application.
     * It is useful to trigger the media scanner after saving a new file.
     */
    private final Context mContext;

    /**
     * Reference to the global counter.
     */
    private final AtomicInteger mPhotoCounter;

    /**
     * Counter of the photo: it is used to determine the position
     * of a photo in a sequence of photo.
     */
    private final int mPhotoIndex;

    /**
     * Reference to the list of {@link Mat} images that will be sent
     * to the HDR algorithm when all the photos are available.
     */
    private final List<byte[]> mMatList;

    /**
     * Create a new ImageSaver object.
     *
     * @param context    the application context
     * @param image      the {@link Image} to be saved and/or elaborated
     * @param imageFile  the name of the jpg file where to save the photo
     * @param photoIndex counter that tracks the position of a photo in a sequence of photos
     */
    ImageSaver(Context context, Image image, String imageFile,
               AtomicInteger photoIndex, List<byte[]> matList) {
        mContext = context;
        sCameraPreferences = CameraPreferences.getInstance(context);
        mImage = image;
        mImageFileName = imageFile;
        mPhotoCounter = photoIndex;
        mPhotoIndex = mPhotoCounter.incrementAndGet();
        mMatList = matList;
        createFiles();
    }

    @Override
    public void run() {
        // Get the buffer from the Image object
        ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        if (mPhotoIndex != 0) {
            mMatList.add(mPhotoIndex - 1, bytes);
        }

        // If the user has chosen to save the photo, save it (if appropriate).
        // IMPORTANT: no matter what, close the Image object!
        FileOutputStream output = null;
        try {
            if (sCameraPreferences.getSaveIntermediatePhotos() || mPhotoIndex == 0) {
                output = new FileOutputStream(mImageFile);
                output.write(bytes);
                triggerMediaScanner(mImageFile.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            mImage.close();
            if (null != output) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (sCameraPreferences.isHdrOn()) {
            // Finally, if it is the last photo of the sequence,
            // we start the HDR algorithm.
            if (mPhotoIndex == sCameraPreferences.getNumHdrPhotos()) {
                // Start HDR
                Hdr makeHdr = new Hdr(mContext, mMatList);
                new Thread(makeHdr).start();

                // Reset the global counter
                mPhotoCounter.set(-1);
            }
        }
    }

    /**
     * Create the appropriate files.
     */
    private void createFiles() {
        File storageDirectory = CameraPreferences.getAppDir();

        if (!storageDirectory.exists()) {
            if (!storageDirectory.mkdirs()) {
                Log.e(TAG, "Failed to create directory");
                if (DEBUG) {
                    Log.d(TAG, storageDirectory.mkdir() + "" + storageDirectory.getParentFile() + "");
                }
            }
        }

        if (sCameraPreferences.getSaveIntermediatePhotos() || mPhotoIndex == 0) {
            String imageFileName;
            if (mPhotoIndex == 0) {
                imageFileName = mImageFileName + ".jpg";
            } else {
                imageFileName = mImageFileName + "_" + mPhotoIndex + ".jpg";
            }
            mImageFile = new File(storageDirectory, imageFileName);
        }
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
}
