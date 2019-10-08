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


/**
 * Yielding strategy that uses a Thread.yield() for {@link com.lmax.disruptor.EventProcessor}s waiting on a barrier
 * after an initially spinning.
 * <p>
 * This strategy will use 100% CPU, but will more readily give up the CPU than a busy spin strategy if other threads
 * require CPU resource.
 * 线程礼让的等待策略
 *
 * @author ;
 */
public final class YieldingWaitStrategy implements WaitStrategy {
    /**
     * 从100减到0以后调用Yield
     */
    private static final int SPIN_TRIES = 100;

    /**
     * 第一个参数是消费者期望的下一个序列。
     * 第二个参数是生产者正对着的序列
     * 第三个参数是所有消费者中最小的序列
     * @param sequence          to be waited on.
     * @param cursor            the main sequence from ringbuffer. Wait/notify strategies will
     *                          need this as it's the only sequence that is also notified upon update.
     * @param dependentSequence on which to wait.
     * @param barrier           the processor is waiting on.
     * @return ;
     * @throws AlertException ;
     * @throws InterruptedException ;
     */
    @Override
    public long waitFor(
        final long sequence, Sequence cursor, final Sequence dependentSequence, final SequenceBarrier barrier)
        throws AlertException, InterruptedException {

        long availableSequence;
        int counter = SPIN_TRIES;

        //如果当前的消费者需要的sequence大于被依赖的前一组消费者已经消费的最小序号，那么就一直在循环里不出来
        while ((availableSequence = dependentSequence.get()) < sequence) {
            counter = applyWaitMethod(barrier, counter);
        }
        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking() {
    }

    private int applyWaitMethod(final SequenceBarrier barrier, int counter)
        throws AlertException {

        barrier.checkAlert();

        if (0 == counter) {
            Thread.yield();
        }
        else {
            --counter;
        }
        return counter;
    }
}
