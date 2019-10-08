/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;

import com.lmax.disruptor.util.ThreadHints;

/**
 * Blocking strategy that uses a lock and condition variable for {@link EventProcessor}s waiting on a barrier.
 * <p>
 * This strategy can be used when throughput and low-latency are not as important as CPU resource.
 *
 * @author ;
 *
 * 阻塞的等待策略
 */
public final class BlockingWaitStrategy implements WaitStrategy {


    private final Object mutex = new Object();

    @Override
    public long waitFor(long sequence, Sequence cursorSequence, Sequence dependentSequence, SequenceBarrier barrier)
        throws AlertException, InterruptedException {
        long availableSequence;
        if (cursorSequence.get() < sequence) {
            synchronized (mutex) {
                //如果生产者的进度小于消费者
                while (cursorSequence.get() < sequence) {

                    barrier.checkAlert();
                    //等待
                    mutex.wait();
                }
            }
        }
        //如果生产者的进度小于消费者
        while ((availableSequence = dependentSequence.get()) < sequence) {
            barrier.checkAlert();
            //执行空方法
            ThreadHints.onSpinWait();
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking() {
        //唤醒
        synchronized (mutex) {
            mutex.notifyAll();
        }
    }

    @Override
    public String toString() {
        return "BlockingWaitStrategy{" +
            "mutex=" + mutex +
            '}';
    }
}
