# Android-

一.Android消息机制概述

1.Android的消息机制主要是指Handler的运行机制，Handler的运行需要底层的MessageQueue和Looper的支撑，MessageQueue是消息队列，Looper会已无线循环的形式
取查找MessageQueue中是否有新消息，如果有就处理消息，如果没有就一直等待着。

2.Looper有一个重要的概念就是ThreadLocal，ThreadLocal并不是线程，它的作用是可以在每个线程中存储数据。Handler根据ThreadLocal来获取当前线程的Looper。

3.主线程，UI线程也就是ActivityThread，被创建时会自动初始化Looper，所以主线程中可以直接使用Handler。

4.Android规定UI只能在主线程中进行，子线程中访问UI会抛出异常，因为ViewRootImpl会调用checkThread方法会线程进行验证：

    void checkThread() {
        if (mThread != Thread.currentThread()) {
            throw new CalledFromWrongThreadException(
                    "Only the original thread that created a view hierarchy can touch its views.");
        }
    }
不能再子线程访问UI的原因：因为UI控件不是线程安全的，所以多线程访问时可能会导致UI控件处于不可预期的状态。

5.Handler创建时，必须创建当前线程的Looper，或者在一个Looper的线程中创建Handler。

6.Handler的工作原理：Handler的post方法将一个runnable投递到Handler内部的Looper中去处理，也可以通过send方法发送消息，其实post最终也是通过send方法
来完成的，当Handler的send方法被调用时，他会调用MessageQueue的enqueueMessage方法将这个消息放入消息队列中，然后Looper发现有新来的消息就会处理这个
消息，最终消息中的Runnable或者Handler的handleMessage方法就会被调用。注意Looper是运行在Handler所在的线程中。我们通常用的更新UI子线程中发送消息的
Handler并不是创建Handler的线程。


二.Android的消息机制分析

1.ThreadLocal介绍：
ThreadLocal的作用：
（1）在指定的线程中存储数据：

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private ThreadLocal<Boolean> mBooleanThreadLocal = new ThreadLocal<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBooleanThreadLocal.set(true);
        Log.d(TAG, "[Thread#main]mBooleanThreadLocal: "+mBooleanThreadLocal.get());

        new Thread(new Runnable() {
            @Override
            public void run() {
                mBooleanThreadLocal.set(false);
                Log.d(TAG, "[Thread#1]mBooleanThreadLocal: "+mBooleanThreadLocal.get());
            }
        }).start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "[Thread#2]mBooleanThreadLocal: "+mBooleanThreadLocal.get());
            }
        }).start();

    }
}
打印结果为true,false,null.不同线程访问同一个ThreadLocal的get方法，ThreadLocal内部会从各自的线程中取出一个数组，然后从数组中根据当前ThreadLocal
的索引去查找出对应的value值。

    public void set(T value) {
        Thread currentThread = Thread.currentThread();
        Values values = values(currentThread);------------获取当前线程的数据
        if (values == null) {
            values = initializeValues(currentThread);
        }
        values.put(this, value);--------存储数据
    }

存储规则：ThreadLocal的值在table数组中的储存位置总是为ThreadLocal的reference字段所标识对象的下一位置。

（2）复杂逻辑下的数据传递：

2.消息队列的工作原理：
消息队列----MessageQueue。只要包含两个操作：enqueueMessage（插入），next（读取）。
next的作用会从消息队列中取出一条消息，并将其从消息队列中删除。，next是一个无线循环的方法，如果消息队列中没有消息，next方法会一直阻塞在这里，如果有
新消息时，next方法会返回这条消息并将其从消息队列中删除。


3.Looper的工作原理：
Looper会不停的从MessageQueue中查找是否有新消息，如果没有会一直阻塞在那里，前面说到Handler要想正常工作，必须有创建当前线程的Looper。我们先看看
Looper的构造方法：

  private Looper(Boolean quitAllowed){
    mQueue = new MessageQueue(quitAllowed);
    mThread = Thread。currentThread（）;
  }

在它的构造方法中会创建一个MessageQueue消息队列，然后将当前线程的对象保存起来。

那么如何为当前线程创建一个Looper呢？ 通过Looper.prepare()即可为一个线程创建Looper，接着通过Looper.loop（）来开启消息循环：

    new Thread（"#Thread#2"){
      
      public void run(){
        Looper.prepare();-------创建Looper
        Handler handler = new Handler();----创建当前线程的Handler
        Looper.loop();----开启消息循环
      };
    }.start();
    
Looper提供一个getMainLooper方法可以在任何地方获取到主线程的Looper。Looper也是可以退出的，Looper提供了quit和quitSafely方法退出一个Looper。
两者的区别是：quit会直接退出Looper。quitSalely只是设定一个退出标记，然后把消息队列中已有的消息处理完之后在安全退出。Looper退出后，通过Handler
发送的消息会失败，这个时候Handler的send方法返回false。注意：在子线程中手动创建的Looper，那么在所有事情完成以后应该调用quit方法来终止消息循环
否则这个子线程就会一直处于等待状态，而如退出Looper之后这个线程会立刻终止。

Looper最重要的方法时loop方法，只有调用了loop方法后，消息循环才会真正的起作用。

      /**
     * Run the message queue in this thread. Be sure to call
     * {@link #quit()} to end the loop.
     */
    public static void loop() {
        final Looper me = myLooper();
        if (me == null) {
            throw new RuntimeException("No Looper; Looper.prepare() wasn't called on this thread.");
        }
        final MessageQueue queue = me.mQueue;

        // Make sure the identity of this thread is that of the local process,
        // and keep track of what that identity token actually is.
        Binder.clearCallingIdentity();
        final long ident = Binder.clearCallingIdentity();

        for (;;) {
            Message msg = queue.next(); // might block
            if (msg == null) {---------------------------------------------------唯一跳出循环的方法，MessageQueue的next返回null
                // No message indicates that the message queue is quitting.
                return;
            }

            // This must be in a local variable, in case a UI event sets the logger
            Printer logging = me.mLogging;
            if (logging != null) {
                logging.println(">>>>> Dispatching to " + msg.target + " " +
                        msg.callback + ": " + msg.what);
            }

            msg.target.dispatchMessage(msg);

            if (logging != null) {
                logging.println("<<<<< Finished to " + msg.target + " " + msg.callback);
            }

            // Make sure that during the course of dispatching the
            // identity of the thread wasn't corrupted.
            final long newIdent = Binder.clearCallingIdentity();
            if (ident != newIdent) {
                Log.wtf(TAG, "Thread identity changed from 0x"
                        + Long.toHexString(ident) + " to 0x"
                        + Long.toHexString(newIdent) + " while dispatching to "
                        + msg.target.getClass().getName() + " "
                        + msg.callback + " what=" + msg.what);
            }

            msg.recycleUnchecked();
        }
    }

loop方法是一个死循环，唯一跳出循环的方式是MessageQueue的next方法返回了null，当Looper的quit方法被调用时，Looper就会调用MessageQueue的quit
或者quitSafely方法来通知消息队列退出，当消息队列退出时，他的next方法就会返回null，此时就会退出，否则MessageQueue的next方法会一直阻塞在那里，
这也导致loop方法一直阻塞在那里。



3.Handler的工作原理：

    public final boolean sendMessageDelayed(Message msg, long delayMillis)
    {
        if (delayMillis < 0) {
            delayMillis = 0;
        }
        return sendMessageAtTime(msg, SystemClock.uptimeMillis() + delayMillis);
    }
    
     public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
        MessageQueue queue = mQueue;
        if (queue == null) {
            RuntimeException e = new RuntimeException(
                    this + " sendMessageAtTime() called with no mQueue");
            Log.w("Looper", e.getMessage(), e);
            return false;
        }
        return enqueueMessage(queue, msg, uptimeMillis);
    }
    
     private boolean enqueueMessage(MessageQueue queue, Message msg, long uptimeMillis) {
        msg.target = this;
        if (mAsynchronous) {
            msg.setAsynchronous(true);
        }
        return queue.enqueueMessage(msg, uptimeMillis);
    }
    
Handler的主要工作包含消息的发送和接收。通过源码可以看出，Handler发送消息过程仅仅是向消息队列中插入了一条消息，MessageQueue的next方法就会返回这条
消息给Looper，Looper收到消息后就开始处理了，最终消息通过Looper交给Handler处理，即Handler的dispatchMessage方法会被调用，这时Handler就进入了
处理消息的阶段：dispatchMessage的实现如下：

  public void dispatchMessage(Message msg) {
        if (msg.callback != null) {
            handleCallback(msg);
        } else {
            if (mCallback != null) {
                if (mCallback.handleMessage(msg)) {
                    return;
                }
            }
            handleMessage(msg);------------回调Handler的handleMessage方法
        }
   }


4.主线程的消息循环：
Android的主线程就是ActivityThread，主线程的入口方法为main，在main方法中系统会通过Looper.prepareMainLooper()来为主线程创建Looper以及消息队列，
并通过Looper.loop来开启主线程的消息循环。
还需要一个Handler，这个Handler就是ActivityThread.H，它内部定义了一组消息类型，主要包含了四大组件的启动和停止等过程。

ActivityThread通过AppliocationThread和ActivityManagerService进行线程间通信，AMS以进程间通信的方式完成AcitvityThread的请求后回调ApplicationThread
中的Binder方法,然后ApplicationThread会想H发送消息，H接收到消息后将ApplicationThread中的逻辑切换到ActivityThread中去执行，即主线程。这个过程就是
主线程的消息循环模型。












































  
                       
    
