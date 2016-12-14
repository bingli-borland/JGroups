package org.jgroups.util;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Pool of buffers
 * @author Bela Ban
 * @since  4.0
 */
public class BufferPool {
    protected final AtomicReferenceArray<Buf> pool;
    protected final int                       size;
    protected int                             index;   // only changed by (single) getter
    protected int                             gets;
    protected int                             creates; // incremented when the pool was full (a new buffer was created)

    public BufferPool(int capacity, int size) {
        this.pool=new AtomicReferenceArray<>(Util.getNextHigherPowerOfTwo(capacity));
        this.size=size;
        for(int i=0; i < pool.length(); i++)
            pool.set(i, new Buf(this, i, new byte[size]));
    }

    public int        creates() {return creates;}
    public int        gets()    {return gets;}
    public BufferPool reset()   {
        creates=gets=0; return this;}


    /** Only 1 thread should call this at a time */
    public Buf getBuffer() {
        Buf buf;
        gets++;
        for(int i=0; i < pool.length(); i++) {
            if((buf=pool.get(index)) != null && pool.compareAndSet(index, buf, null)) {
                incrIndex();
                return buf;
            }
            incrIndex();
        }
        creates++;
        return new Buf(this, -1, new byte[size]);
    }

    public boolean releaseBuffer(int index, Buf buf) {
        return index < 0 || pool.compareAndSet(index, null, buf);
    }

    public int capacity() {return pool.length();}

    /** Number of available buffers */
    public int size() {
        int available=0;
        for(int i=0; i < pool.length(); i++) {
            if(pool.get(i) != null)
                available++;
        }
        return available;
    }



    public String toString() {
        return String.format("[cap %d available %d]", capacity(), size());
    }

    protected final void incrIndex() {
        index=(index+1) & (pool.length() -1);
    }

}
