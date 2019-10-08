package com.chenhaiyang.test.producer;

import com.chenhaiyang.test.Producer;
import com.chenhaiyang.test.ValueEvent;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;

public class SingleProducer implements Producer<ValueEvent>{

    private  RingBuffer<ValueEvent> ringBuffer;

    @Override
    public RingBuffer<ValueEvent> newRingBuffer() {

        if(ringBuffer==null) {
            this.ringBuffer = RingBuffer.createSingleProducer(ValueEvent.EVENT_INSTANCE,
                    Producer.BUFFER_SIZE, new YieldingWaitStrategy());

            //默认使用BlockingWaitStrategy
            //RingBuffer.createSingleProducer(ValueEvent.EVENT_INSTANCE,
                    //Producer.BUFFER_SIZE);
        }
        return ringBuffer;

    }



}
