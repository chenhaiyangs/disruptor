/*
 * Copyright 2012 LMAX Ltd.
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

import sun.misc.Unsafe;

import com.lmax.disruptor.util.Util;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sequence为递增的序号类
 * @author ;
 * padding
 */
class LhsPadding {
    /**
     * 坐边填充7个long类型
     */
    protected long p1, p2, p3, p4, p5, p6, p7;
}

/**
 * 真实的value
 * value在中间，可以保证value一定可以独占一个cache_line
 */
class Value extends LhsPadding {
    protected volatile long value;
}

/**
 * 右边填充7个long类型
 */
class RhsPadding extends Value {
    protected long p9, p10, p11, p12, p13, p14, p15;
}

/**
 * <p>Concurrent sequence class used for tracking the progress of
 * the ring buffer and event processors.  Support a number
 * of concurrent operations including CAS and order writes.
 *
 * <p>Also attempts to be more efficient with regards to false
 * sharing by adding padding around the volatile field.
 *
 * @author ;
 * Sequence
 */
public class Sequence extends RhsPadding {

    /**
     * 无效value -1
     */
    static final long INITIAL_VALUE = -1L;
    /**
     * unsafe对象
     */
    private static final Unsafe UNSAFE;
    /**
     * value偏移量。后续更新value是基于VALUE_OFFSET的
     */
    private static final long VALUE_OFFSET;

    /*
     * 初始化静态常量
     */
    static {
        UNSAFE = Util.getUnsafe();
        try {
            VALUE_OFFSET = UNSAFE.objectFieldOffset(Value.class.getDeclaredField("value"));
        }
        catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create a sequence initialised to -1.
     * 初始化
     */
    public Sequence()
    {
        this(INITIAL_VALUE);
    }

    /**
     * Create a sequence with a specified initial value.
     *
     * @param initialValue The initial value for this sequence.
     * 初始化，指定value
     *
     */
    public Sequence(final long initialValue)
    {
        UNSAFE.putOrderedLong(this, VALUE_OFFSET, initialValue);
    }

    /**
     * Perform a volatile read of this sequence's value.
     *
     * @return The current value of the sequence.
     * get
     */
    public long get()
    {
        return value;
    }

    /**
     * Perform an ordered write of this sequence.  The intent is
     * a Store/Store barrier between this write and any previous
     * store.
     * set
     *
     * @param value The new value for the sequence.
     */
    public void set(final long value) {
        UNSAFE.putOrderedLong(this, VALUE_OFFSET, value);
    }

    /**
     * Performs a volatile write of this sequence.  The intent is
     * a Store/Store barrier between this write and any previous
     * write and a Store/Load barrier between this write and any
     * subsequent volatile read.
     *
     * @param value The new value for the sequence.
     */
    public void setVolatile(final long value) {
        /*
         *    ==>设置obj对象中offset偏移地址对应的long型field的值为指定值。
         *    ==>obj：包含需要修改field的对象
         *    ==>offset: <code>obj</code>中long型field的偏移量
         *    ==>value: field将被设置的新值
         */
        UNSAFE.putLongVolatile(this, VALUE_OFFSET, value);
    }

    /**
     * Perform a compare and set operation on the sequence.
     *
     * @param expectedValue The expected current value.
     * @param newValue The value to update to.
     * @return true if the operation succeeds, false otherwise.
     */
    public boolean compareAndSet(final long expectedValue, final long newValue) {
        return UNSAFE.compareAndSwapLong(this, VALUE_OFFSET, expectedValue, newValue);
    }

    /**
     * Atomically increment the sequence by one.
     *
     * @return The value after the increment
     */
    public long incrementAndGet()
    {
        return addAndGet(1L);
    }

    /**
     * Atomically add the supplied value.
     *
     * @param increment The value to add to the sequence.
     * @return The value after the increment.
     * CAS的ADDAndGET并获取到最新的值
     */
    public long addAndGet(final long increment)
    {
        long currentValue;
        long newValue;

        do
        {
            currentValue = get();
            newValue = currentValue + increment;
        }
        while (!compareAndSet(currentValue, newValue));
        return newValue;
    }

    @Override
    public String toString()
    {
        return Long.toString(get());
    }
}
