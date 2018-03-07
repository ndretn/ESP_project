package it.unipd.dei.esp1617.patova.hdresp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

/**
 * Shows OK/Cancel confirmation dialog.
 */
public class ConfirmationDialogFragment extends DialogFragment {

    static final String TAG = "CONFIRMATION_DIALOG_FRAGMENT";

    private static final String ARG_MESSAGE = "message";
    private static DialogInterface.OnClickListener mPositiveListener;
    private static DialogInterface.OnClickListener mNegativeListener;

    private static ConfirmationDialogFragment newInstance(String message,
                                                          DialogInterface.OnClickListener positiveListener,
                                                          DialogInterface.OnClickListener negativeListener) {
        ConfirmationDialogFragment dialog = new ConfirmationDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MESSAGE, message);
        dialog.setArguments(args);
        mPositiveListener = positiveListener;
        mNegativeListener = negativeListener;
        return dialog;
    }

    public static ConfirmationDialogFragment newInstance(String message,
                                                         final Activity activity,
                                                         DialogInterface.OnClickListener positiveListener) {
        return newInstance(message, positiveListener,
                new DialogInterface.OnClickListener() {
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
                .setNegativeButton(android.R.string.cancel, mNegativeListener)
                .create();
    }
}
