package com.fadcam.pip;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;

/** Small Home-screen entry point for the reaction/commentary workspace. */
public class ReactionPipLaunchButton extends AppCompatButton {
    public ReactionPipLaunchButton(Context context) { super(context); init(); }
    public ReactionPipLaunchButton(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public ReactionPipLaunchButton(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr); init();
    }

    private void init() {
        setText("REACTION PIP");
        setAllCaps(false);
        setOnClickListener(v -> {
            Context context = getContext();
            context.startActivity(new Intent(context, CameraVideoPipActivity.class));
        });
    }
}
