package com.chenhaiyang.test.comsumer;

import com.chenhaiyang.test.ValueEvent;
import com.lmax.disruptor.EventHandler;

public class ValueEventHandler implements EventHandler<ValueEvent>{
    @Override
    public void onEvent(ValueEvent event, long sequence, boolean endOfBatch) throws Exception {
        System.out.println(event);
    }
}
