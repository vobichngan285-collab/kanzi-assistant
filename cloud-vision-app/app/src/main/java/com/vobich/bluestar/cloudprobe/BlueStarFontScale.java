package com.vobich.bluestar.cloudprobe;

import android.content.Context;
import android.content.res.Configuration;

/** Scope 50% font scaling to the four Blue Star Activities, leaving every
 * dp-sized control, view position, touch target, color and device setting intact.
 * No process-global resource mutation. Preserve the user's system font choice
 * by multiplying its current scale instead of resetting it to a fixed size. */
final class BlueStarFontScale {
    private BlueStarFontScale(){}
    static Context wrap(Context base) {
        Configuration copy=new Configuration(base.getResources().getConfiguration());
        copy.fontScale=copy.fontScale*0.5f;
        return base.createConfigurationContext(copy);
    }
}
