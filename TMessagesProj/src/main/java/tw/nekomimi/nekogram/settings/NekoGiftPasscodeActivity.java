package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.biometric.BiometricManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.CodeFieldContainer;
import org.telegram.ui.CodeNumberField;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.TextViewSwitcher;
import org.telegram.ui.Components.TransformableLoginButtonView;
import org.telegram.ui.Components.VerticalPositionAutoAnimator;

import tw.nekomimi.nekogram.helpers.PasscodeHelper;

public class NekoGiftPasscodeActivity extends BaseFragment {

    public static final int TYPE_SETUP = 0;
    public static final int TYPE_CHECK = 1;

    private int type;
    private int account;

    private RLottieImageView lockImageView;
    private TextView titleTextView;
    private TextViewSwitcher descriptionTextSwitcher;
    private CodeFieldContainer codeFieldContainer;
    private EditTextBoldCursor passwordEditText;
    private ImageView passwordButton;
    private FrameLayout floatingButtonContainer;
    private TransformableLoginButtonView floatingButtonIcon;

    private int passcodeSetStep = 0;
    private String firstPassword;

    public NekoGiftPasscodeActivity(int type) {
        this.type = type;
        this.account = -1;
    }

    @Override
    public boolean onFragmentCreate() {
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = frameLayout;

        LinearLayout innerLinearLayout = new LinearLayout(context);
        innerLinearLayout.setOrientation(LinearLayout.VERTICAL);
        innerLinearLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        frameLayout.addView(innerLinearLayout,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        lockImageView = new RLottieImageView(context);
        lockImageView.setFocusable(false);
        lockImageView.setAnimation(R.raw.tsv_setup_intro, 120, 120);
        lockImageView.setAutoRepeat(false);
        lockImageView.playAnimation();
        lockImageView.setVisibility(
                !AndroidUtilities.isSmallScreen() && AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y
                        ? View.VISIBLE
                        : View.GONE);
        innerLinearLayout.addView(lockImageView, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL));

        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setTypeface(AndroidUtilities.bold());
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        innerLinearLayout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

        descriptionTextSwitcher = new TextViewSwitcher(context);
        descriptionTextSwitcher.setFactory(() -> {
            TextView tv = new TextView(context);
            tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            tv.setGravity(Gravity.CENTER_HORIZONTAL);
            tv.setLineSpacing(AndroidUtilities.dp(2), 1);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            return tv;
        });
        descriptionTextSwitcher.setInAnimation(context, R.anim.alpha_in);
        descriptionTextSwitcher.setOutAnimation(context, R.anim.alpha_out);
        innerLinearLayout.addView(descriptionTextSwitcher, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 20, 8, 20, 0));

        FrameLayout codeContainer = new FrameLayout(context);
        codeFieldContainer = new CodeFieldContainer(context) {
            @Override
            protected void processNextPressed() {
                if (type == TYPE_SETUP) {
                    if (passcodeSetStep == 0) {
                        processNext();
                    } else {
                        processDone();
                    }
                } else {
                    processDone();
                }
            }
        };
        codeFieldContainer.setNumbersCount(4, CodeFieldContainer.TYPE_PASSCODE);
        for (CodeNumberField f : codeFieldContainer.codeField) {
            f.setTransformationMethod(PasswordTransformationMethod.getInstance());
            f.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        }
        codeContainer.addView(codeFieldContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 40, 10, 40, 0));
        innerLinearLayout.addView(codeContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 32, 0, 72));

        // Setup logic
        if (type == TYPE_SETUP) {
            titleTextView.setText(LocaleController.getString(R.string.CreatePasscode));
            passcodeSetStep = 0;
        } else {
            titleTextView.setText(LocaleController.getString(R.string.EnterYourPasscode));

            if (PasscodeHelper.isGiftPasscodeResetPending()) {
                long resetTime = PasscodeHelper.getGiftPasscodeResetTime();
                long diff = resetTime - System.currentTimeMillis();
                if (diff > 0) {
                    descriptionTextSwitcher.setText(LocaleController.formatString("ResetInDays", R.string.ResetInDays,
                            LocaleController.formatDuration((int) (diff / 1000))));
                } else {
                    PasscodeHelper.checkGiftPasscodeReset(); // Should reset now
                    finishFragment();
                }
            } else {
                descriptionTextSwitcher.setText(LocaleController.getString(R.string.EnterYourPasscode));
            }

            TextView forgotTextView = new TextView(context);
            forgotTextView.setText(LocaleController.getString(R.string.ForgotPasscode));
            forgotTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            forgotTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            forgotTextView.setGravity(Gravity.CENTER);
            forgotTextView.setOnClickListener(v -> {
                if (PasscodeHelper.isGiftPasscodeResetPending()) {
                    return;
                }

                org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(
                        getParentActivity());
                builder.setTitle(LocaleController.getString(R.string.ResetPasscode));
                builder.setMessage(LocaleController.getString(R.string.ResetPasscodeText));
                builder.setPositiveButton(LocaleController.getString(R.string.Reset), (dialog, which) -> {
                    PasscodeHelper.initiateGiftPasscodeReset();
                    dialog.dismiss();
                    finishFragment();
                });
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                showDialog(builder.create());
            });
            innerLinearLayout.addView(forgotTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 0));
        }

        floatingButtonContainer = new FrameLayout(context);
        floatingButtonIcon = new TransformableLoginButtonView(context);
        floatingButtonIcon.setTransformType(TransformableLoginButtonView.TYPE_PROGRAMMING);
        floatingButtonIcon.setColor(Theme.getColor(Theme.key_nextClicked));
        floatingButtonContainer.addView(floatingButtonIcon,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        floatingButtonContainer.setBackground(
                Theme.createCircleDrawable(AndroidUtilities.dp(60), Theme.getColor(Theme.key_chats_actionBackground)));
        floatingButtonContainer.setOnClickListener(v -> {
            if (type == TYPE_SETUP) {
                if (passcodeSetStep == 0) {
                    processNext();
                } else {
                    processDone();
                }
            } else {
                processDone();
            }
        });
        frameLayout.addView(floatingButtonContainer,
                LayoutHelper.createFrame(60, 60, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 34, 44));

        if (PasscodeHelper.getGiftPasscodeRetryUntil() > 0) {
            checkRetryState();
        }

        return fragmentView;
    }

    private void checkRetryState() {
        long until = PasscodeHelper.getGiftPasscodeRetryUntil();
        if (until > 0) {
            long diff = until - System.currentTimeMillis();
            if (diff > 0) {
                codeFieldContainer.setVisibility(View.INVISIBLE);
                floatingButtonContainer.setVisibility(View.INVISIBLE);
                String timeString = LocaleController.formatDuration((int) (diff / 1000));
                descriptionTextSwitcher
                        .setText(LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));

                AndroidUtilities.cancelRunOnUIThread(checkRetryRunnable);
                AndroidUtilities.runOnUIThread(checkRetryRunnable, 1000);
            } else {
                AndroidUtilities.cancelRunOnUIThread(checkRetryRunnable);
                codeFieldContainer.setVisibility(View.VISIBLE);
                floatingButtonContainer.setVisibility(View.VISIBLE);
                descriptionTextSwitcher.setText(LocaleController.getString(R.string.EnterYourPasscode));
                codeFieldContainer.setText("");
            }
        } else {
            AndroidUtilities.cancelRunOnUIThread(checkRetryRunnable);
            codeFieldContainer.setVisibility(View.VISIBLE);
            floatingButtonContainer.setVisibility(View.VISIBLE);
        }
    }

    private final Runnable checkRetryRunnable = this::checkRetryState;

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        AndroidUtilities.cancelRunOnUIThread(checkRetryRunnable);
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            codeFieldContainer.requestFocus();
            AndroidUtilities.showKeyboard(codeFieldContainer);
        }
    }

    private void processNext() {
        String password = codeFieldContainer.getCode();
        if (password.length() != 4) {
            AndroidUtilities.shakeViewSpring(codeFieldContainer);
            return;
        }

        firstPassword = password;
        passcodeSetStep = 1;
        codeFieldContainer.post(() -> codeFieldContainer.setText(""));
        titleTextView.setText(LocaleController.getString(R.string.ConfirmCreatePasscode));
        AndroidUtilities.shakeViewSpring(titleTextView, 5);
    }

    private void processDone() {
        String password = codeFieldContainer.getCode();
        if (password.length() != 4) {
            AndroidUtilities.shakeViewSpring(codeFieldContainer);
            return;
        }

        if (type == TYPE_SETUP) {
            if (passcodeSetStep == 1) {
                if (firstPassword.equals(password)) {
                    PasscodeHelper.setGiftPasscode(firstPassword);
                    finishFragment();
                } else {
                    AndroidUtilities.shakeViewSpring(codeFieldContainer);
                    passcodeSetStep = 0;
                    firstPassword = null;
                    codeFieldContainer.setText("");
                    titleTextView.setText(LocaleController.getString(R.string.CreatePasscode));
                    AndroidUtilities.shakeViewSpring(titleTextView, 5);
                }
            }
        } else {
            if (PasscodeHelper.getGiftPasscodeRetryUntil() > 0) {
                checkRetryState();
                codeFieldContainer.setText("");
                return;
            }

            if (PasscodeHelper.checkGiftPasscode(password)) {
                PasscodeHelper.cleanGiftPasscodeBadTries();
                finishFragment();
                if (onPasscodeConfirmed != null)
                    onPasscodeConfirmed.run();
            } else {
                PasscodeHelper.increaseGiftPasscodeBadTries();
                if (PasscodeHelper.getGiftPasscodeRetryUntil() > 0) {
                    checkRetryState();
                    AndroidUtilities.shakeViewSpring(descriptionTextSwitcher);
                } else {
                    AndroidUtilities.shakeViewSpring(codeFieldContainer);
                }
                VibratorCompat.vibrate(200);
                codeFieldContainer.setText("");
            }
        }
    }

    private Runnable onPasscodeConfirmed;

    public void setOnPasscodeConfirmed(Runnable runnable) {
        this.onPasscodeConfirmed = runnable;
    }

    // Helper to simulate vibration simply
    private static class VibratorCompat {
        static void vibrate(long ms) {
            try {
                View v = new View(ApplicationLoader.applicationContext);
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            } catch (Exception ignore) {
            }
        }
    }
}