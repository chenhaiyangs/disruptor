package com.lmax.disruptor;

/**
 * EventSequencer继承自DataProvider
 * @param <T> ;
 */
public interface EventSequencer<T> extends DataProvider<T>, Sequenced {

}
