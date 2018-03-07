package it.unipd.dei.esp1617.patova.hdresp;


import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v13.app.ActivityCompat;
import android.support.v13.app.FragmentCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static android.app.Activity.RESULT_OK;

public class GalleryFragment extends Fragment implements View.OnClickListener {

    static final String TAG = "GALLERY_FRAGMENT";
    static final String FROM_CAMERA = "FROM_CAMERA";
    private static boolean mCameraCaller = true;

    /**
     * Permissions related constants.
     */
    private static final int REQUEST_STORAGE_PERMISSION = 100;
    private static final String[] STORAGE_PERMISSIONS = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    /**
     * {@link CameraSettings} object to help access to camera characteristics
     */
    private static CameraSettings mCameraSettings;

    /**
     * Setup the camera with user preferences and more
     */
    private static CameraPreferences mCameraPreferences;

    /**
     * Listener that helps to load a new fragment from the activity
     * when it is necessary.
     */
    private ChangeFragment mChangeFragment;

    private final static int PICK_IMAGE_REQUEST = 1;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    private static List<byte[]> mByteImages;

    public static GalleryFragment newInstance() {
        return new GalleryFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        if (savedInstanceState != null) {
            mCameraCaller = savedInstanceState.getBoolean("FROM_CAMERA");
        }

        mCameraSettings = CameraSettings.getInstance(getActivity());
        mCameraPreferences = CameraPreferences.getInstance(getActivity());

        return inflater.inflate(R.layout.gallery_fragment, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        view.findViewById(R.id.settings).setOnClickListener(this);
        view.findViewById(R.id.galleryTextView).setOnClickListener(this);

        CameraSettings cameraSettings = CameraSettings.getInstance(getActivity());
        if (cameraSettings.isHdrSupported(cameraSettings.getBackCamera())) {
            view.findViewById(R.id.camera).setOnClickListener(this);
        } else {
            view.findViewById(R.id.camera).setVisibility(View.GONE);
        }

        // If the fragment is called by the camera, start directly choosing the images
        Bundle bundle = getArguments();
        if (bundle != null) {
            int caller = bundle.getInt(FROM_CAMERA);
            if (caller == 1 && mCameraCaller) {
                chooseImages();
            }
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.settings: {
                Activity activity = getActivity();
                if (null != activity) {
                    Intent intent = new Intent(activity, SettingsActivity.class);
                    startActivity(intent);
                }
                break;
            }
            case R.id.camera: {
                mCameraCaller = true;
                mChangeFragment.changeFragment(CameraFragment.newInstance(), CameraFragment.TAG);
                break;
            }
            case R.id.galleryTextView : {
                chooseImages();
                break;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
    }

    /**
     * Start an intent that gives the user the ability to choose the images
     * that will be then processed.
     */
    private void chooseImages() {
        Intent intent = new Intent();
        intent.setType("image/*");  // Show only images, no videos or anything else
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);  //Permette selezioni immagini multiple
        intent.setAction(Intent.ACTION_GET_CONTENT);  // on Upload click call ACTION_GET_CONTENT intent

        // Always show the chooser (if there are multiple options available)
        this.startActivityForResult(
                Intent.createChooser(intent, getString(R.string.gallery_chooser_title)),
                PICK_IMAGE_REQUEST);
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        HandlerThread backgroundThread = new HandlerThread("GalleryBackground");
        backgroundThread.start();
        mBackgroundHandler = new Handler(backgroundThread.getLooper());
    }

    /*
        Selezione le immagini, le converte in array di byte e lancia l'Hdr()
    */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mCameraCaller = false;

        mByteImages = new ArrayList<>();

        try {

            //Se sono state selezionate immagini allora PICK_IMAGE_REQUEST == 1
            if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {

                if (data.getData() != null) {  //se viene selezionata una sola immagine

                    Toast.makeText(getActivity(), getString(R.string.gallery_too_few_images), Toast.LENGTH_LONG).show();
                    Log.d(TAG, "Attenzione: è stata selezionata solo un'immagine.");

                } else {  //In caso di selezione multipla

                    if (data.getClipData() != null) {

                        ClipData mClipData = data.getClipData();
                        int nSelectedPhotos = mClipData.getItemCount();  //number of selected pics

                        if  ( nSelectedPhotos < 3 ){
                            Toast.makeText(getActivity(), getString(R.string.gallery_too_few_images), Toast.LENGTH_LONG).show();
                        }
                        else {
                            if (nSelectedPhotos > 7) {
                                Toast.makeText(getActivity(), getString(R.string.gallery_too_many_images), Toast.LENGTH_LONG).show();
                            }
                            else {

                                for (int i = 0; i < nSelectedPhotos; i++) {

                                    ClipData.Item item = mClipData.getItemAt(i);
                                    Uri uri = item.getUri();

                                    // Copia ogni singola immagine in un byteArray
                                    InputStream iStream = getActivity().getContentResolver().openInputStream(uri);
                                    byte[] byteArray = getBytes(iStream);
                                    mByteImages.add(byteArray);
                                }

                                /*Check permissions and make Hdr*/
                                if (checkStoragePermissions()) {
                                    mBackgroundHandler.post(new Hdr(getActivity(), mByteImages));
                                }
                            }
                        }
                        Log.d(TAG, "Numero immagini selezionate: " + nSelectedPhotos);
                    }
                }
            } else {
                Toast.makeText(getActivity(), getString(R.string.gallery_no_image_picked), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getActivity(), getString(R.string.gallery_generic_error), Toast.LENGTH_LONG).show();
        }

    }

    /*
    Legge immagini e le salva byte per byte in un byteArray
     */
    private byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
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
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("FROM_CAMERA", mCameraCaller);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_STORAGE_PERMISSION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Storage permission granted.");
                    mBackgroundHandler.post(new Hdr(getActivity(), mByteImages));
                } else {
                    Log.i(TAG, "Storage permission NOT granted.");
                    showMissingPermissionError(getString(R.string.storage_permission_not_granted_error));
                }
                break;
            }
        }
    }

    /**
     * Tells whether storage permissions are granted to this app.
     */
    private boolean checkStoragePermissions() {
        for (String permission : STORAGE_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(getActivity(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                requestStoragePermission();
                return false;
            }
        }

        return true;
    }

    /**
     Request only storage permissions.
     * <p>
     * Eventually show a dialog to the user explaining why
     * the app needs those permissions.
     */
    private void requestStoragePermission() {
        if (shouldShowRationale()) {
            ConfirmationDialogFragment.newInstance(
                    getString(R.string.storage_request_permission_rationale),
                    getActivity(),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(
                                    getFragmentManager().findFragmentByTag(TAG),
                                    STORAGE_PERMISSIONS,
                                    REQUEST_STORAGE_PERMISSION);
                        }
                    }).show(getFragmentManager(), ConfirmationDialogFragment.TAG);
        } else {
            FragmentCompat.requestPermissions(this,
                    STORAGE_PERMISSIONS, REQUEST_STORAGE_PERMISSION);
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
        for (String permission : STORAGE_PERMISSIONS) {
            if (FragmentCompat.shouldShowRequestPermissionRationale(this, permission)) {
                return true;
            }
        }
        return false;
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
