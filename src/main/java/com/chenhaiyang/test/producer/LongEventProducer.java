package com.chenhaiyang.test.producer;

import com.chenhaiyang.test.ValueEvent;
import com.lmax.disruptor.RingBuffer;

import java.util.concurrent.atomic.AtomicInteger;

public class LongEventProducer {

    private AtomicInteger increasedCount = new AtomicInteger(1);

    private final RingBuffer<ValueEvent> ringBuffer;
    public LongEventProducer(RingBuffer<ValueEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    /**
     * onData用来发布事件，每调用一次就发布一次事件事件
     * 它的参数会通过事件传递给消费者
     *
     */
    public void onData() {
        //可以把ringBuffer看做一个事件队列，那么next就是得到下面一个事件槽
        long sequence = ringBuffer.next();
        try {
            //用上面的索引取出一个空的事件用于填充
            ValueEvent event = ringBuffer.get(sequence);// for the sequence
            event.setUserName("admin");
            event.setPassWord("123456");
            event.setValue(increasedCount.incrementAndGet());
        } finally {
            //发布事件
            ringBuffer.publish(sequence);
        }
    }
}