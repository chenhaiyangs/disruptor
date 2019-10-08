package com.lmax.disruptor;

/**
 * 单例的timeout异常
 *
 * @author ;
 */
@SuppressWarnings("serial")
public final class TimeoutException extends Exception {
    public static final TimeoutException INSTANCE = new TimeoutException();

    private TimeoutException() {
        // Singleton
    }

    @Override
    public Throwable fillInStackTrace()
    {
        return this;
    }
}
