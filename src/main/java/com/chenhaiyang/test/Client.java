package com.chenhaiyang.test;

import com.chenhaiyang.test.comsumer.ValueEventHandler;
import com.chenhaiyang.test.producer.LongEventProducer;
import com.chenhaiyang.test.producer.SingleProducer;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WorkHandler;
import com.lmax.disruptor.dsl.Disruptor;

import javax.swing.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


public class Client{

    public static void main(String[] args) throws InterruptedException {

        // 消费者消费线程
        Executor executor = Executors.newCachedThreadPool();
        // 构造disruptor实例
        Disruptor<ValueEvent> disruptor = new Disruptor<>(ValueEvent.EVENT_INSTANCE, Producer.BUFFER_SIZE, executor);
        // 添加事件处理器
        disruptor.handleEventsWith(new ValueEventHandler());
        // 启动
        disruptor.start();
        // Get the ring buffer from the Disruptor to be used for publishing.
        RingBuffer<ValueEvent> ringBuffer = disruptor.getRingBuffer();

        LongEventProducer producer = new LongEventProducer(ringBuffer);

        for (;;) {
            producer.onData();
            Thread.sleep(1);
        }

    }
}
