# Disruptor为何高性能？

## 事件预分配

    在定义disruptor的时候我们需要指定事件工厂EventFactory的逻辑，
    disruptor内部的ringbuffer的数据结构是数组，EventFactory就用于disruptor初始化时数组每个元素的填充。
    生产者开始后，是通过获取对应位置的Event，
    调用Event的setter函数更新Event达到生产数据的目的的。为什么这样？
    假设使用LinkedList，在生产消费的场景下生产者会产生大量的新节点，
    新节点被消费后又需要被回收，频繁的生产消费给GC带来很大的压力。
    使用数组后，在内存中存在的是一块大小稳定的内存，
    频繁的生产消费对GC并没有什么影响，大大减小了系统的最慢响应时间，
    更不会因为消费者的滞后导致OOM的发生。
    因此这种事件预分配的方法对于减轻GC压力可以说是一种简单有效的方法，日常工作中的借鉴意义还是很大的。
    
    数组是一块连续的内存，而且收尾不缓存数据，为了避免伪共享单纯使用填充。
    
## padding 避免伪共享

    disruptor避免伪共享。使得操作更容易命中CPU缓存，通过padding机制，空间换时间。
    伪共享一直是一个比较高级的话题，
    Doug lea在JDK的Concurrent使用了大量的缓存行机制避免伪共享，
    disruptor也是用了这样的机制。但是对于广大的码农而言，实际工作中我们可能很少会需要使用这样的机制。
    毕竟对于大部分人而言，与避免伪共享带来的性能提升而言，
    优化工程架构，算法，io等可能会给我们带来更大的性能提升。
    所以本文只简单提到这个话题，并不深入讲解，毕竟我也没有实际的应用经验去讲解这个话题。
    
## 无锁算法

    通过CAS竞争和while判断配合YieldingWaitStrategy 实现了高效的偏移量维护
    
## 数据批处理
    
    例如，BatchEventProcessor 一次返回一批可消费的数据用于消费，而不是一个。
## 缓存。
    
    SingleProducerSequencer通过缓存上一次获取到的所有消费者的最小指针。以尽可能减少
      while (wrapPoint > (minSequence = Util.getMinimumSequence(gatingSequences, nextValue))) {
            // TODO: Use waitStrategy to spin?
            LockSupport.parkNanos(1L); 
      }
    代码的调用。
## 位运算的使用

    ringBuffer的size() 大小要求位2的n次方，这是因为 如果size是2的n次方。
    那么 index&(size-1)可以代替取模算法。
    同时，在ringBuffer为了计算数据在buffer中的偏移位置，通过Unsafe计算。  index<<2 取代 index*4 。 index <<3 取代 index*8
    
   
## 原理

    Disruptor是一个RingBuffer数据结构。环形队列。
    生产者通过next获取一个空的对象。然后调用publish方法使得数据对所有消费者可见。
    
    ProcessingSequenceBarrier是序号栅栏。其里面维护了生产者的生产指针和一组被依赖的消费者的消费指针。（用于实现多个EventHandler的串行处理）
    第一次调用disruptor的handleEventsWith方法。消费者指针队列数组为空。
    代表着第一组可以并行的handleEventsWith的序号展览依靠生产者的生产指针。代表其消费位置不能超过生产指针。
    
    通过链式调用第二次调用handleEventsWith方法。则消费者指针数组则是上一个handleEventsWith里面的Handler的指针，
    代表其消费位置不能超过上一组handlers的最小的消费位置。
    
    链式调用handleEventsWith，比如下面这样：则handler2只能消费handler1消费过后的指针。handler3只能消费hander2消费过后的指针。
    disruptor
    .handleEventsWith(new Handler1())
    .handleEventsWith(new Handler2())
    .handleEventsWith(new Handler3());
    
    其顺序调用就是通过序号栅栏实现。
    
    并行调用：
    disruptor.handleEventsWith(new Handler1(), new Handler2(), new Handler3());实现并行调用，其实就是一个分组。分组内的三个Handler是可以并行的。
    因为它们的序号栅栏都是依靠生产者的生产指针。
    
    生产者类型分单生产者和多生产者。对应的生产序号实现机制分别为SingleProducerSequencer和MultiProducerSequencer。
    
    消费类型分为单消费者和多消费者。
    单消费者的实现类为BatchEventProcessor。
    多消费者的实现类型为通过WorkerPool构建的WorkerProcessor。
    
    
    
    