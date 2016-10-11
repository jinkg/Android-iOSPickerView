package com.yalin.iospickerview;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.yalin.wheelview.WheelAdapter;
import com.yalin.wheelview.WheelView;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WheelView wheelView = (WheelView) findViewById(R.id.wheel_view);
        wheelView.setLabel("K");
        wheelView.setTextSize(30);
        wheelView.setCurrentItem(3);
//        wheelView.setLoopable(false);

        String[] items = new String[]{"AA", "BB", "CC", "DD",
                "EE", "FF", "GG", "hh", "ii", "jj", "kk", "ll", "mm", "nn"};
        List<String> listItems = Arrays.asList(items);

        wheelView.setAdapter(new StringWheelViewAdapter(listItems));
        wheelView.setOnItemSelectedListener(new WheelView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(WheelAdapter adapter, int position) {
                Log.d(TAG, "onItemSelected: " + position);
            }
        });
    }
}
