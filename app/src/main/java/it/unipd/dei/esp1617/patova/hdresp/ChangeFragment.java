package it.unipd.dei.esp1617.patova.hdresp;

import android.app.Fragment;

/**
 * Interface that the host activity needs to implement in
 * order to receive a callback when the app needs to load
 * a new fragment.
 */
interface ChangeFragment {
    /**
     * Change {@link Fragment} within the activity container.
     *
     * @param fragment  the fragment to load
     * @param tag the tag for the fragment
     */
    void changeFragment(Fragment fragment, String tag);
}
