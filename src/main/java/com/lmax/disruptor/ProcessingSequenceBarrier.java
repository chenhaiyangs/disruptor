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
 * {@link SequenceBarrier} handed out for gating {@link EventProcessor}s on a cursor sequence and optional dependent {@link EventProcessor}(s),
 * using the given WaitStrategy.
 * 序号栅栏实现类：ProcessingSequenceBarrier
 * 主要作用用于当消费者消费完了，根据指定的WaitStrategy消费新的数据
 *
 * @author ;
 */
final class ProcessingSequenceBarrier implements SequenceBarrier {
    /**
     * 等待策略
     */
    private final WaitStrategy waitStrategy;
    /**
     * 一组的消费者的指针
     */
    private final Sequence dependentSequence;
    /**
     * alerted
     */
    private volatile boolean alerted = false;
    /**
     * 生产者的cursor
     */
    private final Sequence cursorSequence;
    /**
     * 生产者的生产指针
     */
    private final Sequencer sequencer;

    /**
     * 构造函数
     * @param sequencer ;
     * @param waitStrategy ;
     * @param cursorSequence ;
     * @param dependentSequences ;
     */
    ProcessingSequenceBarrier(
        final Sequencer sequencer,
        final WaitStrategy waitStrategy,
        final Sequence cursorSequence,
        final Sequence[] dependentSequences) {

        this.sequencer = sequencer;
        this.waitStrategy = waitStrategy;
        this.cursorSequence = cursorSequence;
        /*
         * Disruptor的并行以及汇聚关系都是通过序号栅栏实现的。
         * 第一组EventHandler依靠的是生产者的指针，也就是cursorSequence。
         * 后面被依赖的就是dependentSequences。也就是，此EventHandler的消费指针必须得小于dependentSequences里面最小的消费指针
         */
        if (0 == dependentSequences.length) {
            dependentSequence = cursorSequence;
        }
        else {
            dependentSequence = new FixedSequenceGroup(dependentSequences);
        }
    }

    @Override
    public long waitFor(final long sequence)
        throws AlertException, InterruptedException, TimeoutException {
        //如果alert为true，则抛出异常
        checkAlert();
        //可用的序号
        long availableSequence = waitStrategy.waitFor(sequence, cursorSequence, dependentSequence, this);
        //如果可用的序号大于期望的sequence。则返回可用的availableSequence
        if (availableSequence < sequence) {
            return availableSequence;
        }

        return sequencer.getHighestPublishedSequence(sequence, availableSequence);
    }

    @Override
    public long getCursor()
    {
        return dependentSequence.get();
    }

    @Override
    public boolean isAlerted()
    {
        return alerted;
    }

    @Override
    public void alert() {
        alerted = true;
        waitStrategy.signalAllWhenBlocking();
    }

    @Override
    public void clearAlert()
    {
        alerted = false;
    }

    @Override
    public void checkAlert() throws AlertException {
        if (alerted) {
            throw AlertException.INSTANCE;
        }
    }
}