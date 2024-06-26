// Copyright © SixtyFPS GmbH <info@slint.dev>
// SPDX-License-Identifier: GPL-3.0-only OR LicenseRef-Slint-Royalty-free-1.1 OR LicenseRef-Slint-commercial

import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.text.Editable;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.view.inputmethod.InputMethodManager;
import android.app.Activity;
import android.widget.FrameLayout;
import android.view.inputmethod.BaseInputConnection;

class SlintInputView extends View {
    private String mText = "";
    private int mCursorPosition = 0;
    private int mAnchorPosition = 0;
    private int mPreeditStart = 0;
    private int mPreeditEnd = 0;
    private int mInputType = EditorInfo.TYPE_CLASS_TEXT;
    private int mInBatch = 0;
    private boolean mPending = false;
    private SlintEditable mEditable;

    public class SlintEditable extends SpannableStringBuilder {
        public SlintEditable() {
            super(mText);
        }

        @Override
        public SpannableStringBuilder replace(int start, int end, CharSequence tb, int tbstart, int tbend) {
            super.replace(start, end, tb, tbstart, tbend);
            if (mInBatch == 0) {
                update();
            } else {
                mPending = true;
            }
            return this;
        }

        public void update() {
            mPending = false;
            mText = toString();
            mCursorPosition = Selection.getSelectionStart(this);
            mAnchorPosition = Selection.getSelectionEnd(this);
            mPreeditStart = BaseInputConnection.getComposingSpanStart(this);
            mPreeditEnd = BaseInputConnection.getComposingSpanEnd(this);
            SlintAndroidJavaHelper.updateText(mText, mCursorPosition, mAnchorPosition, mPreeditStart, mPreeditEnd);
        }
    }

    public SlintInputView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        mEditable = new SlintEditable();
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = mInputType;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        outAttrs.initialSelStart = mCursorPosition;
        outAttrs.initialSelEnd = mAnchorPosition;
        return new BaseInputConnection(this, true) {
            @Override
            public Editable getEditable() {
                return mEditable;
            }

            @Override
            public boolean beginBatchEdit() {
                mInBatch += 1;
                return super.beginBatchEdit();
            }

            @Override
            public boolean endBatchEdit() {
                mInBatch -= 1;
                if (mInBatch == 0 && mPending) {
                    mEditable.update();
                }
                return super.endBatchEdit();
            }
        };
    }

    public void setText(String text, int cursorPosition, int anchorPosition, int preeditStart, int preeditEnd,
            int inputType) {
        boolean restart = mInputType != inputType || !mText.equals(text) || mCursorPosition != cursorPosition
                || mAnchorPosition != anchorPosition;
        mText = text;
        mCursorPosition = cursorPosition;
        mAnchorPosition = anchorPosition;
        mPreeditStart = preeditStart;
        mPreeditEnd = preeditEnd;
        mInputType = inputType;

        if (restart) {
            mEditable = new SlintEditable();
            Selection.setSelection(mEditable, cursorPosition, anchorPosition);
            InputMethodManager imm = (InputMethodManager) this.getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.restartInput(this);
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int currentNightMode = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        switch (currentNightMode) {
            case Configuration.UI_MODE_NIGHT_NO:
                SlintAndroidJavaHelper.setDarkMode(false);
                break;
            case Configuration.UI_MODE_NIGHT_YES:
                SlintAndroidJavaHelper.setDarkMode(true);
                break;
        }
    }
}

public class SlintAndroidJavaHelper {
    Activity mActivity;
    SlintInputView mInputView;

    public SlintAndroidJavaHelper(Activity activity) {
        this.mActivity = activity;
        this.mInputView = new SlintInputView(activity);
        this.mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(10, 10);
                mActivity.addContentView(mInputView, params);
                mInputView.setVisibility(View.VISIBLE);
            }
        });
    }

    public void show_keyboard() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mInputView.requestFocus();
                InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(mInputView, 0);
            }
        });
    }

    public void hide_keyboard() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(mInputView.getWindowToken(), 0);
            }
        });
    }

    static public native void updateText(String text, int cursorPosition, int anchorPosition, int preeditStart,
            int preeditOffset);

    static public native void setDarkMode(boolean dark);

    public void set_imm_data(String text, int cursor_position, int anchor_position, int preedit_start, int preedit_end,
            int rect_x, int rect_y, int rect_w, int rect_h, int input_type) {

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(rect_w, rect_h);
                layoutParams.setMargins(rect_x, rect_y, 0, 0);
                mInputView.setLayoutParams(layoutParams);
                int selStart = Math.min(cursor_position, anchor_position);
                int selEnd = Math.max(cursor_position, anchor_position);
                mInputView.setText(text, selStart, selEnd, preedit_start, preedit_end, input_type);
            }
        });
    }

    public boolean dark_color_scheme() {
        int nightModeFlags = mActivity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    // Get the geometry of the view minus the system bars and the keyboard
    public Rect get_view_rect() {
        Rect rect = new Rect();
        mActivity.getWindow().getDecorView().getWindowVisibleDisplayFrame(rect);
        return rect;
    }
}
