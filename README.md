# Android-iOSPickerView
A item pick view like iOS


## Usage

```xml
<com.yalin.wheelview.WheelView
        android:id="@+id/wheel_view"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        app:gravity="center" />
```

```java
    WheelView wheelView = (WheelView) findViewById(R.id.wheel_view);
        wheelView.setLabel("K");
        wheelView.setTextSize(30);
        wheelView.setCurrentItem(3);
        wheelView.setLoopable(false);

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
```

You can see a complete usage in the demo app.
