package io.github.b1tsagar.liquiwin.core.design.motion

import android.content.Context
import android.provider.Settings

/** Whether Android's animations are on. "Remove animations" sets their scale to 0. */
fun animatorOn(context: Context) = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
