/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package in.shick.diode.threads;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Special class to to allow the parent to be pressed without being pressed itself.
 * This way the line of a tab can be pressed, but the image itself is not.
 */
public class DontPressWithParentFrameLayout extends FrameLayout {

    public DontPressWithParentFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setPressed(boolean pressed) {
        // If the parent is pressed, do not set to pressed.
        if (pressed && ((View) getParent()).isPressed()) {
            return;
        }
        super.setPressed(pressed);
    }
}
