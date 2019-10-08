package com.chenhaiyang.test;

import com.lmax.disruptor.EventFactory;

/**
 * 消息定义
 * @author ;
 */
public class ValueEvent {

    /**
     * 用户名
     */
    private String userName;
    /**
     * 密码
     */
    private String passWord;

    private long value;

    public String getUserName() {
        return userName;
    }

    public String getPassWord() {
        return passWord;
    }

    public long getValue() {
        return value;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setPassWord(String passWord) {
        this.passWord = passWord;
    }

    public void setValue(long value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "ValueEvent{" +
                "userName='" + userName + '\'' +
                ", passWord='" + passWord + '\'' +
                ", value=" + value +
                '}';
    }

    public static final EventFactory<ValueEvent> EVENT_INSTANCE = ValueEvent::new;
}

