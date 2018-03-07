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

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import org.opencv.android.OpenCVLoader;

public class CameraActivity extends Activity implements ChangeFragment {
    private static final String TAG = "CameraActivity";

    static {
        if (OpenCVLoader.initDebug()) {
            Log.d(TAG, "OPENCV LOADED!");
        } else Log.d(TAG, "OPENCV NOT LOADED!");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the default settings for the app. This method is called
        // only once in the lifetime of the app.
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        CameraSettings cameraSettings = CameraSettings.getInstance(this);

        setContentView(R.layout.activity_camera);

        if (null == savedInstanceState) {
            // Check if the device have a back camera that does support HDR:
            // if so, start the camera fragment...
            if (cameraSettings.isHdrSupported(cameraSettings.getBackCamera())) {
                changeFragment(CameraFragment.newInstance(), CameraFragment.TAG);
            } else {  // otherwise start gallery fragment.
                changeFragment(GalleryFragment.newInstance(), GalleryFragment.TAG);
            }
        }
    }

    // Override necessary to pass the result to the fragment
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void changeFragment(Fragment fragment, String tag) {
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.container, fragment, tag);
        fragmentTransaction.commit();
    }
}