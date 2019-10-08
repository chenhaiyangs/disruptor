package com.lmax.disruptor;

/**
 * 每次获取新消息后都会批量回调此API。
 * EventHadnler 也可以实现此接口
 *
 * @author ;
 */
public interface BatchStartAware {

    /**
     * onBatchStart
     * @param batchSize batchSize
     */
    void onBatchStart(long batchSize);
}
