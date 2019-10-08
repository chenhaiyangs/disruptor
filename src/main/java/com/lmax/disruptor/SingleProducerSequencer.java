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

import java.util.concurrent.locks.LockSupport;

import com.lmax.disruptor.util.Util;

/*
 * 单生产者ProducerSequencer
 * @author ;
 */

/**
 * 填充
 */
abstract class SingleProducerSequencerPad extends AbstractSequencer {
    protected long p1, p2, p3, p4, p5, p6, p7;

    SingleProducerSequencerPad(int bufferSize, WaitStrategy waitStrategy)
    {
        super(bufferSize, waitStrategy);
    }
}

/**
 * 单生产者的序列字段
 */
abstract class SingleProducerSequencerFields extends SingleProducerSequencerPad {
    /**
     * 构造函数
     * @param bufferSize ;
     * @param waitStrategy ;
     */
    SingleProducerSequencerFields(int bufferSize, WaitStrategy waitStrategy)
    {
        super(bufferSize, waitStrategy);
    }

    /**
     * Set to -1 as sequence starting point
     * 生产者下一个value
     */
    long nextValue = Sequence.INITIAL_VALUE;
    /**
     * 缓存的上一次的消费者的最小的偏移量value
     */
    long cachedValue = Sequence.INITIAL_VALUE;
}

/**
 * <p>Coordinator for claiming sequences for access to a data structure while tracking dependent {@link Sequence}s.
 * Not safe for use from multiple threads as it does not implement any barriers.</p>
 *
 * <p>* Note on {@link Sequencer#getCursor()}:  With this sequencer the cursor value is updated after the call
 * to {@link Sequencer#publish(long)} is made.</p>
 *
 * SingleproducerSequencer
 * @author ;
 */
public final class SingleProducerSequencer extends SingleProducerSequencerFields {
    //填充
    protected long p1, p2, p3, p4, p5, p6, p7;

    /**
     * Construct a Sequencer with the selected wait strategy and buffer size.
     *
     * @param bufferSize   the size of the buffer that this will sequence over.
     * @param waitStrategy for those waiting on sequences.
     * 构造函数
     */
    public SingleProducerSequencer(int bufferSize, WaitStrategy waitStrategy) {
        super(bufferSize, waitStrategy);
    }

    /**
     * @see Sequencer#hasAvailableCapacity(int)
     * 是否有可用的容量
     */
    @Override
    public boolean hasAvailableCapacity(int requiredCapacity) {
        return hasAvailableCapacity(requiredCapacity, false);
    }

    /**
     * 是否有可用的容量。如果wrapPoint小于所有消费者的最小序列号，则有容量，返回true
     * @param requiredCapacity ;
     * @param doStore 是否进行内存屏障的store操作
     * @return ;
     */
    private boolean hasAvailableCapacity(int requiredCapacity, boolean doStore) {
        long nextValue = this.nextValue;

        long wrapPoint = (nextValue + requiredCapacity) - bufferSize;
        long cachedGatingSequence = this.cachedValue;

        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > nextValue) {
            if (doStore) {
                cursor.setVolatile(nextValue);  // StoreLoad fence
            }
            //最小的序号
            long minSequence = Util.getMinimumSequence(gatingSequences, nextValue);
            this.cachedValue = minSequence;
            if (wrapPoint > minSequence) {
                return false;
            }
        }

        return true;
    }

    /**
     * @see Sequencer#next()
     * next 1
     */
    @Override
    public long next()
    {
        return next(1);
    }

    /**
     * @see Sequencer#next(int)
     * next n
     */
    @Override
    public long next(int n) {
        if (n < 1 || n > bufferSize) {
            throw new IllegalArgumentException("n must be > 0 and < bufferSize");
        }

        long nextValue = this.nextValue;

        long nextSequence = nextValue + n;
        //是否有绕过RingBuffer这个环。wrap判断用于判断当前的序号有没有没有绕过整个RingBuffer容器
        //生产者只能生产bufferSize个元素以后，就会有覆盖老值的风险。此时，就得判断wrapPoint<=所有消费者的最小消费偏移量，才能继续生产，否则一直阻塞1纳秒
        long wrapPoint = nextSequence - bufferSize;
        //缓存的value。缓存了上一次的所有消费者的minSequence
        long cachedGatingSequence = this.cachedValue;

        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > nextValue) {
            // StoreLoad fence
            cursor.setVolatile(nextValue);
            //最小的序号
            long minSequence;
            //自旋操作。找到消费者中最小的消费者的值
            //生产者序号大于消费者最小的序号，就挂起。避免数据被覆盖
            while (wrapPoint > (minSequence = Util.getMinimumSequence(gatingSequences, nextValue))) {
                LockSupport.parkNanos(1L); // TODO: Use waitStrategy to spin?
            }
            //更新cacheValue
            this.cachedValue = minSequence;
        }

        this.nextValue = nextSequence;
        return nextSequence;
    }

    /**
     * @see Sequencer#tryNext()
     */
    @Override
    public long tryNext() throws InsufficientCapacityException {
        return tryNext(1);
    }

    /**
     * @see Sequencer#tryNext(int)
     */
    @Override
    public long tryNext(int n) throws InsufficientCapacityException {
        if (n < 1) {
            throw new IllegalArgumentException("n must be > 0");
        }

        if (!hasAvailableCapacity(n, true)) {
            throw InsufficientCapacityException.INSTANCE;
        }

        long nextSequence = this.nextValue += n;

        return nextSequence;
    }

    /**
     * @see Sequencer#remainingCapacity()
     * 可写容量
     */
    @Override
    public long remainingCapacity() {
        long nextValue = this.nextValue;

        long consumed = Util.getMinimumSequence(gatingSequences, nextValue);
        long produced = nextValue;
        return getBufferSize() - (produced - consumed);
    }

    /**
     * @see Sequencer#claim(long)
     * 重置nextValue
     */
    @Override
    public void claim(long sequence) {
        this.nextValue = sequence;
    }

    /**
     * @see Sequencer#publish(long)
     * publish某个序列
     */
    @Override
    public void publish(long sequence) {
        //设置
        cursor.set(sequence);
        //等待策略的唤醒
        waitStrategy.signalAllWhenBlocking();
    }

    /**
     * @see Sequencer#publish(long, long)
     */
    @Override
    public void publish(long lo, long hi)
    {
        publish(hi);
    }

    /**
     * @see Sequencer#isAvailable(long)
     * 某个序列是否可用
     */
    @Override
    public boolean isAvailable(long sequence)
    {
        return sequence <= cursor.get();
    }

    @Override
    public long getHighestPublishedSequence(long lowerBound, long availableSequence) {
        return availableSequence;
    }
}
