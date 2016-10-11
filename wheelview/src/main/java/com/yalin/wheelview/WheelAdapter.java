package com.yalin.wheelview;

import android.support.annotation.NonNull;

/**
 * 作者：YaLin
 * 日期：2016/10/10.
 */

public interface WheelAdapter {
    int getItemsCount();

    @NonNull
    String getItemLabel(int index);

}
