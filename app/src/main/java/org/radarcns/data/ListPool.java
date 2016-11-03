package org.radarcns.data;

import java.util.ArrayList;
import java.util.List;

public class ListPool extends ObjectPool<List> {
    public ListPool(int capacity) {
        super(capacity);
    }

    @Override
    protected List newObject() {
        return new ArrayList();
    }

    @Override
    public void add(List list) {
        list.clear();
        super.add(list);
    }
}
