package a8.common.logging;

/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software
 * License version 1.1, a copy of which has been included with this
 * distribution in the LICENSE.APL file.
 */

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import a8.common.logging.Level;
import a8.common.logging.Logger;

/**
 * An OutputStream that flushes out to a Log.
 * <p>
 * 
 * Note that no data is written out to the Log until the stream is flushed or
 * closed.
 * <p>
 * 
 * Example:
 * 
 * <pre>
 * 
 * // make sure everything sent to System.err is logged
 * System.setErr(new PrintStream(new LoggingOutputStream(LogFactory
 * 		.getLog(&quot;system-err&quot;), Level.WARN), true));
 * 
 * // make sure everything sent to System.out is also logged
 * System.setOut(new PrintStream(new LoggingOutputStream(LogFactory
 * 		.getLog(&quot;system-err&quot;), Level.INFO), true));
 * </pre>
 * 
 * @author <a href="mailto://Jim.Moore@rocketmail.com">Jim Moore </a>
 * @see Category
 */
public class LoggedOutputStream extends PrintStream {

	static Pos pos = Pos.fromJava("LoggedOutputStream.java", 187);

	protected static final String LINE_SEPERATOR = System.getProperty("line.separator");

	/**
	 * Used to maintain the contract of {@link #close()}.
	 */
	protected boolean closed_ = false;

	/**
	 * The internal buffer where data is stored.
	 */
	protected byte[] buffer_;

	protected final byte[] originalBuffer_;

	/**
	 * The number of valid bytes in the buffer. This value is always in the
	 * range <tt>0</tt> through <tt>buf.length</tt>; elements
	 * <tt>buf[0]</tt> through <tt>buf[count-1]</tt> contain valid byte
	 * data.
	 */
	protected int bufferIndex_;

	/**
	 * The default number of bytes in the buffer. =2048
	 */
	public static final int DEFAULT_BUFFER_LENGTH = 2048;

	/**
	 * The category to write to.
	 */
	protected Logger logger_;

	/**
	 * The priority to use when writing to the Category.
	 */
	protected Level level_;

	/**
	 * Creates the LoggingOutputStream to flush to the given logger.
	 * 
	 * @param logger
	 *            the Logger to write to
	 * 
	 * @param level
	 *            the Level to use when writing to the Category
	 * 
	 * @exception IllegalArgumentException
	 *                if cat == null or priority == null
	 */
	public LoggedOutputStream( Level level, Logger logger ) throws IllegalArgumentException {
		super( new NullOutputStream() );
		if (logger == null) {
			throw new IllegalArgumentException("log == null");
		}
		if (level == null) {
			throw new IllegalArgumentException("level == null");
		}

		this.level_ = level;
		logger_ = logger;
		buffer_ = new byte[DEFAULT_BUFFER_LENGTH];
		originalBuffer_ = buffer_;
		bufferIndex_ = 0;
	}

	/**
	 * Closes this output stream and releases any system resources associated
	 * with this stream. The general contract of <code>close</code> is that it
	 * closes the output stream. A closed stream cannot perform output
	 * operations and cannot be reopened.
	 */
	@Override
	public void close() {
		flush();
		closed_ = true;
	}

	/**
	 * Writes the specified byte to this output stream. The general contract for
	 * <code>write</code> is that one byte is written to the output stream.
	 * The byte to be written is the eight low-order bits of the argument
	 * <code>b</code>. The 24 high-order bits of <code>b</code> are
	 * ignored.
	 * 
	 * @param b
	 *            the <code>byte</code> to write
	 */
	@Override
	public void write(final int b) {
		if (closed_) {
			throw new RuntimeException("The stream has been closed.");
		}

		// don't log nulls
		if (b == 0) {
			return;
		}

		// would this be writing past the buffer?
		if (bufferIndex_ >= buffer_.length) {
			// grow the buffer
			final byte[] newBuf = new byte[buffer_.length*2];
			System.arraycopy(buffer_, 0, newBuf, 0, buffer_.length);
			buffer_ = newBuf;
		}

		if ( b == '\n' ) {
			flush();
		} else {
			buffer_[bufferIndex_] = (byte) b;
			bufferIndex_++;
		}
		
	}

	/**
	 * Flushes this output stream and forces any buffered output bytes to be
	 * written out. The general contract of <code>flush</code> is that calling
	 * it is an indication that, if any bytes previously written have been
	 * buffered by the implementation of the output stream, such bytes should
	 * immediately be written to their intended destination.
	 */
	@Override
	public void flush() {
		if (bufferIndex_ == 0) {
			return;
		}

		// don't print out blank lines; flushing from PrintStream puts out these
		if (bufferIndex_ == LINE_SEPERATOR.length()) {
			if (((char) buffer_[0]) == LINE_SEPERATOR.charAt(0)
					&& ((bufferIndex_ == 1) || // <- Unix & Mac, -> Windows
					((bufferIndex_ == 2) && ((char) buffer_[1]) == LINE_SEPERATOR
							.charAt(1)))) {
				reset();
				return;
			}
		}

		final byte[] theBytes = new byte[bufferIndex_];

		System.arraycopy(buffer_, 0, theBytes, 0, bufferIndex_);

		logger_.log(level_, new String(theBytes), null, pos);

		reset();
	}

	private void reset() {
		// reset to the original buffer to reclaim memory in case
		// of streams that output a large number of bytes before flushing		
		bufferIndex_ = 0;		
		buffer_ = originalBuffer_;
	}
	
    @Override
	public void write(byte buf[], int off, int len) {
    	for( int i=0; i<len; i++ ) {
    		write( buf[ i+off ] );
    	}
    }

	static class NullOutputStream extends OutputStream {
		@Override
		public void write(int b) throws IOException {
		}
	}
}

