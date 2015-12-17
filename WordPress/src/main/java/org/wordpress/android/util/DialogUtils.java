package org.wordpress.android.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import org.wordpress.android.widgets.OpenSansEditText;
import org.wordpress.android.widgets.WPTextView;

public class DialogUtils {
    public static void showMyProfileDialog(Context context, String title, String initialText, String hint, final Callback callback) {
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View promptView = layoutInflater.inflate(org.wordpress.android.R.layout.my_profile_dialog, null);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
        alertDialogBuilder.setView(promptView);

        final WPTextView textView = (WPTextView) promptView.findViewById(org.wordpress.android.R.id.my_profile_dialog_label);
        final OpenSansEditText editText = (OpenSansEditText) promptView.findViewById(org.wordpress.android.R.id.my_profile_dialog_input);
        final WPTextView hintView = (WPTextView) promptView.findViewById(org.wordpress.android.R.id.my_profile_dialog_hint);

        textView.setText(title);
        if (!TextUtils.isEmpty(hint)) {
            hintView.setText(hint);
        }
        else {
            hintView.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(initialText)) {
            editText.setText(initialText);
            editText.setSelection(0, initialText.length());
        }

        alertDialogBuilder.setCancelable(false)
                .setPositiveButton(org.wordpress.android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        callback.onSuccessfulInput(editText.getText().toString());
                    }
                })
                .setNegativeButton(org.wordpress.android.R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });

        AlertDialog alert = alertDialogBuilder.create();
        alert.show();
    }

    public interface Callback {
        void onSuccessfulInput(String input);
    }
}
