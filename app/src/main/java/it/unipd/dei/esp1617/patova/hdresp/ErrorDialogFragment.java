package it.unipd.dei.esp1617.patova.hdresp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

/**
 * Shows an error message dialog.
 */
public class ErrorDialogFragment extends DialogFragment {

    final static String TAG = "ERROR_DIALOG_FRAGMENT";

    private static final String ARG_MESSAGE = "message";
    private static DialogInterface.OnClickListener mPositiveListener;

    private static ErrorDialogFragment newInstance(String message,
                                                   DialogInterface.OnClickListener positiveListener) {
        ErrorDialogFragment dialog = new ErrorDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MESSAGE, message);
        dialog.setArguments(args);
        mPositiveListener = positiveListener;
        return dialog;
    }

    /**
     * Show an error dialog.
     *
     * @param message  The message to display as an error.
     * @param activity The activity to attach the fragment.
     * @return The {@link ErrorDialogFragment} to display.
     */
    public static ErrorDialogFragment newInstance(String message, final Activity activity) {
        return newInstance(message, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (activity != null) {
                    activity.finish();
                }
            }
        });
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
                .setMessage(getArguments().getString(ARG_MESSAGE))
                .setPositiveButton(android.R.string.ok, mPositiveListener)
                .create();
    }

}
