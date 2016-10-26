package com.achellies.android.ultron.sample;

/**
 * Created by achellies on 16/10/25.
 */

public class TestModule {
    static int ID = 0;
    private int mId;
    protected int mValue;
    int mCount;
    public int mSize;

    private TestModule(int id, int value, int count, int size) {
        this.mId = id;
        this.mValue = value;
        this.mCount = count;
        this.mSize = size;
    }

    public TestModule(int value, int count, int size) {
        this(++ID, value, count, size);

//        this.mValue += 10;
//        this.mCount += 10;
//        this.mSize += 10;
    }

    public long calculate() {
        check();
        return mValue * mCount * mSize;
    }

    private void check() {
        if (mValue > 0) {
        }
    }
}
