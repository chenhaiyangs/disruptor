package com.lmax.disruptor;

/**
 * timeoutHandler，当timeout时回调
 * EventHandler也可以实现此接口
 * @author ;
 */
public interface TimeoutHandler {

    /**
     * ontimeout
     * @param sequence ;
     * @throws Exception ;
     */
    void onTimeout(long sequence) throws Exception;
}
