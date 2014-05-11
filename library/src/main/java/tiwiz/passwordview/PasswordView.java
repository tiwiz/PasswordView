package tiwiz.passwordview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;


/**
 * Created by tiwiz on 09/05/14.
 */
public class PasswordView extends FrameLayout{

    private ImageButton mBtnShowPassword;
    private TextView mPasswordStrenghtLabel;
    private EditText mTxtPassword;
    private Drawable mSecuredPasswordDrawable, mUnsecuredPasswordDrawable;
    private final FrameLayout father = this;
    private int passwordValidator;
    private String[] passwordMessages;
    private int[] passwordMessagesColors;
    private OnPasswordChangedListener onPasswordChangedListener;
    private int oldPasswordLevel = PASSWORD_DEFAULT_LEVEL;

    /**
     * Internal constants and default values
     * */
    private static final int ANIMATION_DURATION = 150;
    private static final int PASSWORD_DEFAULT_LEVEL = -1;
    private static final float IMAGE_SIDE = 48f;
    private static final float IMAGE_MARGIN = 10f;
    private static final float IMAGE_WIDTH = IMAGE_SIDE + IMAGE_MARGIN;
    private static final float LABEL_PADDING = IMAGE_WIDTH;
    private static final String[] DEFAULT_PASSWORD_MESSAGES = new String[]{ "Weak password", "Unsafe password", "Good password", "Strong password"};
    private static final int [] DEFAULT_PASSWORD_COLORS = new int[]{ android.R.color.holo_red_dark, android.R.color.holo_orange_dark, android.R.color.holo_green_dark, android.R.color.holo_blue_dark};
    public static final int NO_VALIDATOR = 0;
    public static final int DEFAULT_VALIDATOR = 1;
    public static final int CUSTOM_VALIDATOR = 2;

    public PasswordView(Context context) {
        this(context, null);
    }

    public PasswordView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PasswordView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        //gets attributes defined by developers
        final TypedArray styledData = context.obtainStyledAttributes(attrs,R.styleable.PasswordView, defStyle, 0);

        //loads Drawable from resources (gets default if none is set)
        mSecuredPasswordDrawable = styledData.getDrawable(R.styleable.PasswordView_securePasswordIcon);
        mUnsecuredPasswordDrawable = styledData.getDrawable(R.styleable.PasswordView_unsecurePasswordIcon);
        final Drawable mBackgroundDrawable = styledData.getDrawable(R.styleable.PasswordView_buttonBackground);

        //if developer did not specify buttons, it takes the default ones
        if(null == mSecuredPasswordDrawable) mSecuredPasswordDrawable = getResources().getDrawable(android.R.drawable.ic_secure);
        if(null == mUnsecuredPasswordDrawable) mUnsecuredPasswordDrawable = getResources().getDrawable(android.R.drawable.ic_secure);

        //loads validator data
        passwordValidator = styledData.getInt(R.styleable.PasswordView_passwordValidator, NO_VALIDATOR);
        if(passwordValidator == DEFAULT_VALIDATOR){
            passwordMessages = DEFAULT_PASSWORD_MESSAGES;
            passwordMessagesColors = DEFAULT_PASSWORD_COLORS;
            onPasswordChangedListener = new DefaultValidator();
        }

        if(passwordValidator != NO_VALIDATOR){
            mPasswordStrenghtLabel = new TextView(context);
            mPasswordStrenghtLabel.setVisibility(INVISIBLE);
            final int sidePadding = (int) styledData.getDimension(R.styleable.PasswordView_passwordMessageSidePadding, dipsToPix(LABEL_PADDING));
            mPasswordStrenghtLabel.setPadding(sidePadding, 0, sidePadding, 0);
            mPasswordStrenghtLabel.setTextAppearance(context, android.R.style.TextAppearance_Small);
            //adds label to the View
            addView(mPasswordStrenghtLabel, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        }

        //converts side to an int dimension
        final int intImageSide = dipsToPix(IMAGE_SIDE);

        //creates "show password" button
        mBtnShowPassword = new ImageButton(context);
        mBtnShowPassword.setImageDrawable(mSecuredPasswordDrawable);
        mBtnShowPassword.setEnabled(false);
        mBtnShowPassword.setMaxWidth(styledData.getInt(R.styleable.PasswordView_maxButtonWidth, intImageSide));
        mBtnShowPassword.setMinimumWidth(styledData.getInt(R.styleable.PasswordView_minButtonWidth, intImageSide));
        mBtnShowPassword.setMaxHeight(styledData.getInt(R.styleable.PasswordView_maxButtonHeight, intImageSide));
        mBtnShowPassword.setMinimumHeight(styledData.getInt(R.styleable.PasswordView_minButtonHeight, intImageSide));

        //sets background if user defined it
        if(null != mBackgroundDrawable)
            mBtnShowPassword.setBackground(mBackgroundDrawable);

        //sets listener for main behaviour
        mBtnShowPassword.setOnClickListener(passwordShower);

        //adds new view to the layout
        addView(mBtnShowPassword, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        styledData.recycle();
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {

        if(child instanceof EditText){
            if(mTxtPassword != null) throw new IllegalArgumentException("Only one child is accepted");

            if(passwordValidator != NO_VALIDATOR) {
                final LayoutParams layoutParams = new LayoutParams(params);
                layoutParams.gravity = Gravity.BOTTOM;
                layoutParams.topMargin = (int) mPasswordStrenghtLabel.getTextSize();
                params = layoutParams;
            }

            setPasswordField((EditText) child);

        }else if(child instanceof ImageButton){
            if(passwordValidator != NO_VALIDATOR){
                final LayoutParams layoutParams = new LayoutParams(params);
                layoutParams.topMargin = (int) mPasswordStrenghtLabel.getTextSize();
                params = layoutParams;
            }
        }
        super.addView(child, index, params);
    }

    /**
     * Methods to dinamically add options to the component
     * */
    public void setPasswordMessages(String[] passwordMessages) {
        this.passwordMessages = passwordMessages;
    }

    public void setPasswordMessagesColors(int[] passwordMessagesColors) {
        this.passwordMessagesColors = passwordMessagesColors;
    }

    public void setPasswordValidator(int passwordValidator) {
        this.passwordValidator = passwordValidator;
    }

    public void setOnPasswordChangedListener(OnPasswordChangedListener onPasswordChangedListener) {
        this.onPasswordChangedListener = onPasswordChangedListener;
    }

    public int getNumberOfMessagesAndColors(){
        return Math.min(passwordMessages.length, passwordMessagesColors.length);
    }

    /**
     * Internal methods
     * */

    private void setPasswordField(EditText editText){

        mTxtPassword = editText;

        //moves the text so that it can fit the button properly
        final int paddingSize = dipsToPix(IMAGE_WIDTH);
        final int paddingVertical = dipsToPix(IMAGE_SIDE) / 4;
        mTxtPassword.setPadding(paddingSize,paddingVertical + 10,0,paddingVertical - 10);
        //sets the listener that will activate additional features only if the input text is not empty
        mTxtPassword.addTextChangedListener(passwordWatcher);

        this.bringChildToFront(mBtnShowPassword);
    }

    private final TextWatcher passwordWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {}

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {}

        @Override
        public void afterTextChanged(Editable editable) {
            if(TextUtils.isEmpty(editable)) {
                mBtnShowPassword.setEnabled(false);
                resetLayoutStatus();
                oldPasswordLevel = PASSWORD_DEFAULT_LEVEL;
                if(passwordValidator != NO_VALIDATOR)
                    if(mPasswordStrenghtLabel.getVisibility() == VISIBLE)
                        hideLabel(null);
            }else {
                mBtnShowPassword.setEnabled(true);
                if(passwordValidator != NO_VALIDATOR) {
                    final int newPasswordLevel = onPasswordChangedListener.evaluateCurrentPassword(editable.toString());
                    if (newPasswordLevel != oldPasswordLevel) {
                        oldPasswordLevel = newPasswordLevel; //sets the password security level to avoid useless redrawing
                        if(mPasswordStrenghtLabel.getVisibility() == VISIBLE)
                            hideLabel(new AnimationReloader()); //if password is already shown and it's required an update, this will animate it
                        else
                            showLabel(oldPasswordLevel); //shows the password
                    }
                }
            }

            father.bringChildToFront(mBtnShowPassword);
        }
    };

    private final OnClickListener passwordShower = new OnClickListener() {
        @Override
        public void onClick(View view) {

            if(mBtnShowPassword.getDrawable().equals(mSecuredPasswordDrawable)){
                //shows password and changes icon to unsecure
                mBtnShowPassword.setImageDrawable(mUnsecuredPasswordDrawable);
                mTxtPassword.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                mTxtPassword.setTypeface(Typeface.MONOSPACE);
                father.bringChildToFront(mBtnShowPassword);
            }else{
                //hides password and changes icon back to secure
                resetLayoutStatus();
                father.bringChildToFront(mBtnShowPassword);
            }
            //moves the selector to the end of the end of the input String
            mTxtPassword.setSelection(mTxtPassword.getText().length());

        }
    };

    // Helper method
    private int dipsToPix(float dps) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dps,
                getResources().getDisplayMetrics());
    }

    private final void resetLayoutStatus(){ //resets the layout status to the original password mode
        mBtnShowPassword.setImageDrawable(mSecuredPasswordDrawable);
        mTxtPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    }

    private final void hideLabel(final AnimationReloader reloader){
        mPasswordStrenghtLabel.setAlpha(1f);
        mPasswordStrenghtLabel.setTranslationY(0f);
        mPasswordStrenghtLabel.setScaleY(0f);
        mPasswordStrenghtLabel.animate()
                .alpha(0f)
                .translationY(mPasswordStrenghtLabel.getHeight())
                .scaleY(1f)
                .setDuration(ANIMATION_DURATION)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mPasswordStrenghtLabel.setVisibility(View.GONE);
                        if(null != reloader)
                            reloader.show();
                    }
                }).start();


    }

    private final void showLabel(int passwordLevel){

        mPasswordStrenghtLabel.setText(passwordMessages[passwordLevel]);
        mPasswordStrenghtLabel.setTextColor(getResources().getColor(passwordMessagesColors[passwordLevel]));

        mPasswordStrenghtLabel.setVisibility(View.VISIBLE);
        mPasswordStrenghtLabel.setAlpha(0f);
        mPasswordStrenghtLabel.setTranslationY(mPasswordStrenghtLabel.getHeight());
        mPasswordStrenghtLabel.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(ANIMATION_DURATION)
                .setListener(null)
                .start();
    }

    private class DefaultValidator implements OnPasswordChangedListener{
        @Override
        public int evaluateCurrentPassword(String currentPassword) {

            final String REGEX_NUMBERS = ".*\\d.*";
            final String REGEX_LOWERCASE = ".*[a-z].*";
            final String REGEX_UPPERCASE = ".*[A-Z].*";
            final String REGEX_PUNCTUATION = ".*\\p{Punct}.*";
            final int PASSWORD_LENGHT = 8;

            int securityLevel = 0;

            if(currentPassword.matches(REGEX_LOWERCASE) && currentPassword.matches(REGEX_UPPERCASE)) securityLevel++;
            if(currentPassword.matches(REGEX_NUMBERS)) securityLevel++;
            if(currentPassword.matches(REGEX_PUNCTUATION)) securityLevel++;
            if(currentPassword.length() >= PASSWORD_LENGHT) securityLevel++;

            //In case the developer gives us a non-matching number of parameters, this code avoid the ArrayOutOfBoundsException
            final int minVectorSize = getNumberOfMessagesAndColors();

            if(securityLevel >= minVectorSize) securityLevel = minVectorSize -1;

            return securityLevel;

        }
    }

    /**
     * Password listener
     * Use this method to implement your own validator
     * @return the position of the message and color to show from the given resources
     * */
    public interface OnPasswordChangedListener{
        public int evaluateCurrentPassword(String currentPassword);
    }

    /**
     * Animation reloader
     * */
    private final class AnimationReloader{
        public final void show(){
            //this is to be sure that the given index is between the bounds of the given array
            if(oldPasswordLevel >=0 && oldPasswordLevel < getNumberOfMessagesAndColors())
                showLabel(oldPasswordLevel);
        }
    }

}