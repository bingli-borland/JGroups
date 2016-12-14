package org.jgroups.util;

import java.io.Closeable;
import java.io.IOException;

/**
 * Buffer with an offset and length. Will be replaced with NIO equivalent once JDK 1.4 becomes baseline. This class is
 * immutable. Note that the underlying byte[] buffer must <em>not</em> be changed as long as this Buffer instance is in use !
 * @author Bela Ban
 */
public class Buf implements Closeable {
    private final BufferPool pool;
    private final int        index;
    private final byte[]     buf;
    private final int        offset;
    private final int        length;

    public Buf(BufferPool p, int idx, byte[] buf, int offset, int length) {
        this.pool=p;
        this.index=idx;
        this.buf=buf;
        this.offset=offset;
        this.length=length;
    }

    public Buf(BufferPool p, int index, byte[] buf) {
        this(p, index, buf, 0, buf.length);
    }

    public byte[] getBuf() {
        return buf;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public int getIndex() {return index;}

    public void close() throws IOException {
        release();
    }

    public void release() {
        pool.releaseBuffer(index, this);
    }

    public String toString() {
        StringBuilder sb=new StringBuilder("idx=" + index + ", ");
        sb.append(length).append(" bytes");
        if(offset > 0)
            sb.append(" (offset=").append(offset).append(")");
        return sb.toString();
    }

}
