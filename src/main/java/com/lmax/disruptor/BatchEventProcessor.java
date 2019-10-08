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

import java.util.concurrent.atomic.AtomicInteger;


/**
 * Convenience class for handling the batching semantics of consuming entries from a {@link RingBuffer}
 * and delegating the available events to an {@link EventHandler}.
 * <p>
 * If the {@link EventHandler} also implements {@link LifecycleAware} it will be notified just after the thread
 * is started and just before the thread is shutdown.
 *
 * @param <T> event implementation storing the data for sharing during exchange or parallel coordination of an event.
 *
 * 单消费者处理器
 *
 * @author ;
 */
public final class BatchEventProcessor<T> implements EventProcessor {

    /**
     * 状态
     */
    private static final int IDLE = 0;
    private static final int HALTED = IDLE + 1;
    private static final int RUNNING = HALTED + 1;

    /**
     * 是否在运行
     */
    private final AtomicInteger running = new AtomicInteger(IDLE);
    /**
     * 异常处理器
     */
    private ExceptionHandler<? super T> exceptionHandler = new FatalExceptionHandler();
    /**
     * 数据提供者
     */
    private final DataProvider<T> dataProvider;
    /**
     * 序号栅栏
     */
    private final SequenceBarrier sequenceBarrier;
    /**
     * 客户端的消费者的接口事件。EventHandler也可以实现LifecycleAware接口
     */
    private final EventHandler<? super T> eventHandler;
    /**
     * ringBuffer中真实，有效，最大的序号
     */
    private final Sequence sequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
    /**
     * 超时时处理器
     */
    private final TimeoutHandler timeoutHandler;
    private final BatchStartAware batchStartAware;

    /**
     * Construct a {@link EventProcessor} that will automatically track the progress by updating its sequence when
     * the {@link EventHandler#onEvent(Object, long, boolean)} method returns.
     *
     * @param dataProvider    to which events are published.
     * @param sequenceBarrier on which it is waiting.
     * @param eventHandler    is the delegate to which events are dispatched.
     */
    public BatchEventProcessor(
        final DataProvider<T> dataProvider,
        final SequenceBarrier sequenceBarrier,
        final EventHandler<? super T> eventHandler) {

        this.dataProvider = dataProvider;
        this.sequenceBarrier = sequenceBarrier;
        this.eventHandler = eventHandler;

        if (eventHandler instanceof SequenceReportingEventHandler) {
            ((SequenceReportingEventHandler<?>) eventHandler).setSequenceCallback(sequence);
        }

        //EventHandler也可以同时实现batchStartAware或者timeoutHandler
        batchStartAware =
            (eventHandler instanceof BatchStartAware) ? (BatchStartAware) eventHandler : null;
        timeoutHandler =
            (eventHandler instanceof TimeoutHandler) ? (TimeoutHandler) eventHandler : null;
    }

    @Override
    public Sequence getSequence() {
        return sequence;
    }

    @Override
    public void halt() {
        running.set(HALTED);
        sequenceBarrier.alert();
    }

    @Override
    public boolean isRunning()
    {
        return running.get() != IDLE;
    }

    /**
     * Set a new {@link ExceptionHandler} for handling exceptions propagated out of the {@link BatchEventProcessor}
     *
     * @param exceptionHandler to replace the existing exceptionHandler.
     */
    public void setExceptionHandler(final ExceptionHandler<? super T> exceptionHandler) {
        if (null == exceptionHandler) {
            throw new NullPointerException();
        }
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * It is ok to have another thread rerun this method after a halt().
     *
     * @throws IllegalStateException if this object instance is already running in a thread
     * 这个线程启动以后是不会退出的
     */
    @Override
    public void run() {
        //更改为运行状态
        if (running.compareAndSet(IDLE, RUNNING)) {
            //序号栅栏清空
            sequenceBarrier.clearAlert();

            notifyStart();
            try {
                if (running.get() == RUNNING) {
                    //处理事件
                    processEvents();
                }
            }
            finally {
                notifyShutdown();
                running.set(IDLE);
            }
        }
        else {
            // This is a little bit of guess work.  The running state could of changed to HALTED by
            // this point.  However, Java does not have compareAndExchange which is the only way
            // to get it exactly correct.
            if (running.get() == RUNNING) {
                throw new IllegalStateException("Thread is already running");
            }
            else {
                earlyExit();
            }
        }
    }

    /**
     * 处理事件
     */
    private void processEvents() {
        T event = null;
        long nextSequence = sequence.get() + 1L;

        while (true) {
            try {
                //获取可用的序号
                final long availableSequence = sequenceBarrier.waitFor(nextSequence);
                if (batchStartAware != null && availableSequence >= nextSequence) {
                    batchStartAware.onBatchStart(availableSequence - nextSequence + 1);
                }
                //从nextSequence从avalilabeSqeuqnce移除处理事件
                while (nextSequence <= availableSequence) {
                    //获取整个event
                    event = dataProvider.get(nextSequence);
                    //endOfBatch代表是否是本批次的末尾
                    eventHandler.onEvent(event, nextSequence, nextSequence == availableSequence);
                    nextSequence++;
                }
                sequence.set(availableSequence);
            }
            catch (final TimeoutException e) {
                notifyTimeout(sequence.get());
            }
            catch (final AlertException ex) {
                if (running.get() != RUNNING) {
                    break;
                }
            }
            catch (final Throwable ex) {
                exceptionHandler.handleEventException(ex, nextSequence, event);
                sequence.set(nextSequence);
                nextSequence++;
            }
        }
    }

    /**
     * 提早退出
     */
    private void earlyExit() {
        notifyStart();
        notifyShutdown();
    }

    /**
     * 通知超时 生命周期事件
     * @param availableSequence 事件
     */
    private void notifyTimeout(final long availableSequence) {
        try {
            if (timeoutHandler != null) {
                timeoutHandler.onTimeout(availableSequence);
            }
        }
        catch (Throwable e) {
            exceptionHandler.handleEventException(e, availableSequence, null);
        }
    }

    /**
     * Notifies the EventHandler when this processor is starting up
     * notify
     * 如果你的EventHandler属于LifecycleAware,会调用生命周期方法
     */
    private void notifyStart() {
        if (eventHandler instanceof LifecycleAware) {
            try {
                ((LifecycleAware) eventHandler).onStart();
            }
            catch (final Throwable ex) {
                exceptionHandler.handleOnStartException(ex);
            }
        }
    }

    /**
     * Notifies the EventHandler immediately prior to this processor shutting down
     * 通知关闭 生命周期事件
     */
    private void notifyShutdown() {
        if (eventHandler instanceof LifecycleAware) {
            try {
                ((LifecycleAware) eventHandler).onShutdown();
            }
            catch (final Throwable ex) {
                exceptionHandler.handleOnShutdownException(ex);
            }
        }
    }
}