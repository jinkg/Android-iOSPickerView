package com.yalin.iospickerview;

import android.support.annotation.NonNull;

import com.yalin.wheelview.WheelAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * 作者：YaLin
 * 日期：2016/10/10.
 */

public class StringWheelViewAdapter implements WheelAdapter {
    private List<String> mItems = new ArrayList<>();

    public StringWheelViewAdapter(List<String> items) {
        mItems = items;
    }

    @Override
    public int getItemsCount() {
        return mItems == null ? 0 : mItems.size();
    }

    public String getItem(int index) {
        return mItems.get(index);
    }

    @NonNull
    @Override
    public String getItemLabel(int index) {
        return getItem(index);
    }
}
