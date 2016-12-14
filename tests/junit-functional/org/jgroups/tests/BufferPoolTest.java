package org.jgroups.tests;

import org.jgroups.Global;
import org.jgroups.util.Buf;
import org.jgroups.util.BufferPool;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Bela Ban
 * @since  4.0
 */
@Test(groups=Global.FUNCTIONAL,singleThreaded=true)
public class BufferPoolTest {
    protected BufferPool pool;

    @BeforeMethod
    protected void setup() {
        pool=new BufferPool(10, 1024);
    }

    public void testSimplePool() {
        System.out.println("pool = " + pool);
        assert pool.capacity() == 16;

        List<Buf> buffers=new ArrayList<>(16);
        for(int i=0; i < 8; i++) {
            Buf buf=pool.getBuffer();
            buffers.add(buf);
        }
        System.out.println("pool = " + pool);
        assert pool.size() == 8;

        buffers.forEach(Buf::release);
        System.out.println("pool = " + pool);
        assert pool.size() == pool.capacity();
    }

    public void testPoolExhaustion() {
        for(int i=0; i < pool.capacity(); i++)
            pool.getBuffer();

        Buf buf=pool.getBuffer();
        System.out.println("buf = " + buf);
        assert buf != null;
        assert buf.getIndex() < 0;
        buf.release();
        assert pool.size() == 0;
    }

    public void testMultipleAccessors() throws Exception {
        ExecutorService thread_pool=Executors.newCachedThreadPool();
        for(int i=0; i < 1_000; i++) {
            Buf buf=pool.getBuffer();
            Releaser r=new Releaser(buf);
            thread_pool.execute(r);
            Thread.yield();
        }

        thread_pool.shutdown();
        thread_pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("\npool = " + pool);
        assert pool.size() == pool.capacity();

    }

    protected static class Releaser extends Thread {
        protected final Buf buf;

        public Releaser(Buf buf) {
            this.buf=buf;
        }

        public void run() {
            Thread.yield();
            buf.release();
        }
    }

}
