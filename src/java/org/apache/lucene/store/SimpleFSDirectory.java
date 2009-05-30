package org.apache.lucene.store;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** A straighforward implementation of {@link FSDirectory}
 *  using java.io.RandomAccessFile.  However, this class has
 *  poor concurrent performance (multiple threads will
 *  bottleneck) as it synchronizes when multiple threads
 *  read from the same file.  It's usually better to use
 *  {@link NIOFSDirectory} or {@link MMapDirectory} instead. */
public class SimpleFSDirectory extends FSDirectory {
    
  /** Create a new SimpleFSDirectory for the named location.
   *
   * @param path the path of the directory
   * @param lockFactory the lock factory to use, or null for the default.
   * @throws IOException
   */
  public SimpleFSDirectory(File path, LockFactory lockFactory) throws IOException {
    super(path, lockFactory);
  }

  // Inherit javadoc
  public IndexOutput createOutput(String name) throws IOException {
    ensureOpen();
    createDir();
    File file = new File(directory, name);
    if (file.exists() && !file.delete())          // delete existing, if any
      throw new IOException("Cannot overwrite: " + file);

    return new SimpleFSIndexOutput(file);
  }

  // Inherit javadoc
  public IndexInput openInput(String name, int bufferSize) throws IOException {
    ensureOpen();
    return new SimpleFSIndexInput(new File(directory, name), bufferSize);
  }

  protected static class SimpleFSIndexInput extends BufferedIndexInput {
  
    protected static class Descriptor extends RandomAccessFile {
      // remember if the file is open, so that we don't try to close it
      // more than once
      protected volatile boolean isOpen;
      long position;
      final long length;
      
      public Descriptor(File file, String mode) throws IOException {
        super(file, mode);
        isOpen=true;
        length=length();
      }
  
      public void close() throws IOException {
        if (isOpen) {
          isOpen=false;
          super.close();
        }
      }
  
      protected void finalize() throws Throwable {
        try {
          close();
        } finally {
          super.finalize();
        }
      }
    }
  
    protected final Descriptor file;
    boolean isClone;
  
    public SimpleFSIndexInput(File path) throws IOException {
      this(path, BufferedIndexInput.BUFFER_SIZE);
    }
  
    public SimpleFSIndexInput(File path, int bufferSize) throws IOException {
      super(bufferSize);
      file = new Descriptor(path, "r");
    }
  
    /** IndexInput methods */
    protected void readInternal(byte[] b, int offset, int len)
         throws IOException {
      synchronized (file) {
        long position = getFilePointer();
        if (position != file.position) {
          file.seek(position);
          file.position = position;
        }
        int total = 0;
        do {
          int i = file.read(b, offset+total, len-total);
          if (i == -1)
            throw new IOException("read past EOF");
          file.position += i;
          total += i;
        } while (total < len);
      }
    }
  
    public void close() throws IOException {
      // only close the file if this is not a clone
      if (!isClone) file.close();
    }
  
    protected void seekInternal(long position) {
    }
  
    public long length() {
      return file.length;
    }
  
    public Object clone() {
      FSIndexInput clone = (FSIndexInput)super.clone();
      clone.isClone = true;
      return clone;
    }
  
    /** Method used for testing. Returns true if the underlying
     *  file descriptor is valid.
     */
    boolean isFDValid() throws IOException {
      return file.getFD().valid();
    }
  }

  protected static class SimpleFSIndexOutput extends BufferedIndexOutput {
    RandomAccessFile file = null;
  
    // remember if the file is open, so that we don't try to close it
    // more than once
    private volatile boolean isOpen;

    public SimpleFSIndexOutput(File path) throws IOException {
      file = new RandomAccessFile(path, "rw");
      isOpen = true;
    }
  
    /** output methods: */
    public void flushBuffer(byte[] b, int offset, int size) throws IOException {
      file.write(b, offset, size);
    }
    public void close() throws IOException {
      // only close the file if it has not been closed yet
      if (isOpen) {
        boolean success = false;
        try {
          super.close();
          success = true;
        } finally {
          isOpen = false;
          if (!success) {
            try {
              file.close();
            } catch (Throwable t) {
              // Suppress so we don't mask original exception
            }
          } else
            file.close();
        }
      }
    }
  
    /** Random-access methods */
    public void seek(long pos) throws IOException {
      super.seek(pos);
      file.seek(pos);
    }
    public long length() throws IOException {
      return file.length();
    }
    public void setLength(long length) throws IOException {
      file.setLength(length);
    }
  }
}
