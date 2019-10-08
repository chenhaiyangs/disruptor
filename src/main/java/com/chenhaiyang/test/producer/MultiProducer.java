package com.chenhaiyang.test.producer;

import com.chenhaiyang.test.Producer;
import com.chenhaiyang.test.ValueEvent;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;

public class MultiProducer implements Producer<ValueEvent>{

    private  RingBuffer<ValueEvent> ringBuffer;
    @Override
    public RingBuffer<ValueEvent> newRingBuffer() {
        if(ringBuffer==null) {
            this.ringBuffer = RingBuffer.createMultiProducer(ValueEvent.EVENT_INSTANCE,
                    Producer.BUFFER_SIZE, new YieldingWaitStrategy());

            //默认使用BlockingWaitStrategy
            //RingBuffer.createMultiProducer(ValueEvent.EVENT_INSTANCE,
            //Producer.BUFFER_SIZE);
        }
        return ringBuffer;
    }
}
