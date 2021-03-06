package de.vier_bier.habpanelviewer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import de.vier_bier.habpanelviewer.db.CredentialManager;

/**
 * UI utility methods.
 */
public class UiUtil {
    private static final String TAG = "HPV-UiUtil";

    static synchronized String formatDateTime(Date d) {
        return d == null ? "-" : DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault()).format(d);
    }

    public static void showDialog(final Context context, final String title, final String text) {
        showButtonDialog(context, title, text, android.R.string.ok, null, -1, null);
    }

    public static void showCancelDialog(final Context context, final String title, final String text,
                                        final DialogInterface.OnClickListener yesListener,
                                        final DialogInterface.OnClickListener noListener) {
        showButtonDialog(context, title, text, android.R.string.yes, yesListener, android.R.string.no, noListener);
    }

    public static void showButtonDialog(final Context context, final String title, final String text,
                                        final int button1TextId,
                                        final DialogInterface.OnClickListener button1Listener,
                                        final int button2TextId,
                                        final DialogInterface.OnClickListener button2Listener) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            if (title != null) {
                builder.setTitle(title);
            }
            builder.setMessage(text);
            if (button1TextId != -1) {
                builder.setPositiveButton(button1TextId, button1Listener);
            }
            if (button2TextId != -1) {
                builder.setNegativeButton(button2TextId, button2Listener);
            }
            builder.show();
        });
    }

    public static void showSnackBar(View view, int textId, int actionTextId, View.OnClickListener clickListener) {
        showSnackBar(view, view.getContext().getString(textId), view.getContext().getString(actionTextId), clickListener);
    }

    public static void showSnackBar(View view, int textId) {
        showSnackBar(view, view.getContext().getString(textId), null, null);
    }

    public static void showSnackBar(View view, String text) {
        showSnackBar(view, text, null, null);
    }

    private static void showSnackBar(View view, String text, String actionText, View.OnClickListener clickListener) {
        Snackbar sb = Snackbar.make(view, text, Snackbar.LENGTH_LONG);
        View sbV = sb.getView();
        TextView textView = sbV.findViewById(com.google.android.material.R.id.snackbar_text);
        textView.setMaxLines(3);
        if (actionText != null && clickListener != null) {
            sb.setAction(actionText, clickListener);
        }

        sb.show();
    }

    static int getThemeId(String theme) {
        if ("dark".equals(theme)) {
            return R.style.Theme_AppCompat_NoActionBar;
        }

        return R.style.Theme_AppCompat_Light_NoActionBar;
    }

    public static boolean themeChanged(String theme, Activity ctx) {
        Resources.Theme dummy = ctx.getResources().newTheme();
        dummy.applyStyle(getThemeId(theme), true);

        TypedValue a = new TypedValue();
        ctx.getTheme().resolveAttribute(android.R.attr.windowBackground, a, true);
        TypedValue b = new TypedValue();
        dummy.resolveAttribute(android.R.attr.windowBackground, b, true);

        return a.data != b.data;
    }

    public static boolean themeChanged(SharedPreferences prefs, Activity ctx) {
        String theme = prefs.getString(Constants.PREF_THEME, "dark");
        return themeChanged(theme, ctx);
    }

    public static void showPasswordDialog(final Context ctx, final String host, final String realm,
                                          final CredentialsListener l) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            final AlertDialog alert = new AlertDialog.Builder(ctx)
                    .setCancelable(false)
                    .setTitle(R.string.credentials_required)
                    .setMessage(ctx.getString(R.string.host_realm, host, realm))
                    .setView(R.layout.dialog_credentials)
                    .setPositiveButton(R.string.okay, (dialog12, id) -> {
                        EditText userT = ((AlertDialog) dialog12).findViewById(R.id.username);
                        EditText passT = ((AlertDialog) dialog12).findViewById(R.id.password);
                        CheckBox storeCB = ((AlertDialog) dialog12).findViewById(R.id.checkBox);

                        l.credentialsEntered(host, realm, userT.getText().toString(), passT.getText().toString(),
                                storeCB.isChecked() && storeCB.isEnabled());
                    })
                    .setNegativeButton(R.string.cancel, (dialogInterface, i) -> l.credentialsCancelled()).create();

            if (!(ctx instanceof Activity) || !((Activity) ctx).isFinishing()) {
                alert.show();

                TextView msg = alert.findViewById(R.id.message);
                if (host.toLowerCase().startsWith("http:")) {
                    msg.setText(R.string.httpWithBasicAuth);
                } else {
                    msg.setText("");
                }

                CheckBox storeCB = alert.findViewById(R.id.checkBox);
                storeCB.setEnabled(CredentialManager.getInstance().isDatabaseUsed());
                storeCB.setChecked(CredentialManager.getInstance().getDatabaseState(ctx) == CredentialManager.State.ENCRYPTED);

                EditText passT = ((AlertDialog) alert).findViewById(R.id.password);
                passT.setOnKeyListener((v, keyCode, event) -> {
                    // If the event is a key-down event on the "enter" button
                    if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                        alert.getButton(DialogInterface.BUTTON_POSITIVE).performClick();

                        return true;
                    }
                    return false;
                });
            }
        });
    }

    public static void showMasterPasswordDialog(final Context ctx, final CredentialsListener l) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            final AlertDialog alert = new AlertDialog.Builder(ctx)
                    .setCancelable(false)
                    .setTitle(R.string.credentials_required)
                    .setMessage(R.string.EnterMasterDisable)
                    .setView(R.layout.dialog_master_password)
                    .setPositiveButton(R.string.okay, (dialog12, id) -> {
                        EditText passT = ((AlertDialog) dialog12).findViewById(R.id.password);

                        l.credentialsEntered(null, null, null, passT.getText().toString(), false);
                    })
                    .setNegativeButton(R.string.cancel, (dialogInterface, i) -> l.credentialsCancelled()).create();

            if (!(ctx instanceof Activity) || !((Activity) ctx).isFinishing()) {
                alert.show();

                EditText passT = ((AlertDialog) alert).findViewById(R.id.password);
                passT.setOnKeyListener((v, keyCode, event) -> {
                    // If the event is a key-down event on the "enter" button
                    if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                        alert.getButton(DialogInterface.BUTTON_POSITIVE).performClick();

                        return true;
                    }
                    return false;
                });
            }
        });
    }

    public interface CredentialsListener {
        void credentialsEntered(String host, String realm, String user, String password, boolean store);

        void credentialsCancelled();
    }
}
