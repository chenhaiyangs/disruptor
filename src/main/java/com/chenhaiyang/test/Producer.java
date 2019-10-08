package com.chenhaiyang.test;

import com.lmax.disruptor.RingBuffer;

public interface Producer<T> {

    int BUFFER_SIZE =512;

    RingBuffer<T> newRingBuffer();
}
