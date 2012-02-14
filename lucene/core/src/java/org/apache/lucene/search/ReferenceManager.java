package org.apache.lucene.search;

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

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Semaphore;

import org.apache.lucene.store.AlreadyClosedException;

/**
 * Utility class to safely share instances of a certain type across multiple
 * threads, while periodically refreshing them. This class ensures each
 * reference is closed only once all threads have finished using it. It is
 * recommended to consult the documentation of {@link ReferenceManager}
 * implementations for their {@link #maybeRefresh()} semantics.
 * 
 * @param <G>
 *          the concrete type that will be {@link #acquire() acquired} and
 *          {@link #release(Object) released}.
 * 
 * @lucene.experimental
 */
public abstract class ReferenceManager<G> implements Closeable {

  private static final String REFERENCE_MANAGER_IS_CLOSED_MSG = "this ReferenceManager is closed";
  
  protected volatile G current;
  
  private final Semaphore reopenLock = new Semaphore(1);
  
  private void ensureOpen() {
    if (current == null) {
      throw new AlreadyClosedException(REFERENCE_MANAGER_IS_CLOSED_MSG);
    }
  }
  
  private synchronized void swapReference(G newReference) throws IOException {
    ensureOpen();
    final G oldReference = current;
    current = newReference;
    release(oldReference);
  }
  
  /** Decrement reference counting on the given reference. */
  protected abstract void decRef(G reference) throws IOException;
  
  /**
   * Refresh the given reference if needed. Returns {@code null} if no refresh
   * was needed, otherwise a new refreshed reference.
   */
  protected abstract G refreshIfNeeded(G referenceToRefresh) throws IOException;

  /**
   * Try to increment reference counting on the given reference. Return true if
   * the operation was successful.
   */
  protected abstract boolean tryIncRef(G reference);

  /**
   * Obtain the current reference. You must match every call to acquire with one
   * call to {@link #release}; it's best to do so in a finally clause, and set
   * the reference to {@code null} to prevent accidental usage after it has been
   * released.
   */
  public final G acquire() {
    G ref;
    do {
      if ((ref = current) == null) {
        throw new AlreadyClosedException(REFERENCE_MANAGER_IS_CLOSED_MSG);
      }
    } while (!tryIncRef(ref));
    return ref;
  }

  /**
   * Close this ReferenceManager to future {@link #acquire() acquiring}. Any
   * references that were previously {@link #acquire() acquired} won't be
   * affected, and they should still be {@link #release released} when they are
   * not needed anymore.
   */
  public final synchronized void close() throws IOException {
    if (current != null) {
      // make sure we can call this more than once
      // closeable javadoc says:
      // if this is already closed then invoking this method has no effect.
      swapReference(null);
    }
  }

  /**
   * You must call this, periodically, if you want that {@link #acquire()} will
   * return refreshed instances.
   * 
   * <p>
   * <b>Threads</b>: it's fine for more than one thread to call this at once.
   * Only the first thread will attempt the refresh; subsequent threads will see
   * that another thread is already handling refresh and will return
   * immediately. Note that this means if another thread is already refreshing
   * then subsequent threads will return right away without waiting for the
   * refresh to complete.
   * 
   * <p>
   * This method returns true if the reference was in fact refreshed, or if the
   * current reference has no pending changes.
   */
  public final boolean maybeRefresh() throws IOException {
    ensureOpen();
    // Ensure only 1 thread does reopen at once; other threads just return immediately:
    if (reopenLock.tryAcquire()) {
      try {
        final G reference = acquire();
        try {
          G newReference = refreshIfNeeded(reference);
          if (newReference != null) {
            assert newReference != reference : "refreshIfNeeded should return null if refresh wasn't needed";
            boolean success = false;
            try {
              swapReference(newReference);
              success = true;
            } finally {
              if (!success) {
                release(newReference);
              }
            }
          }
        } finally {
          release(reference);
        }
        return true;
      } finally {
        reopenLock.release();
      }
    } else {
      return false;
    }
  }

  /**
   * Release the refernce previously obtained via {@link #acquire()}.
   * <p>
   * <b>NOTE:</b> it's safe to call this after {@link #close()}.
   */
  public final void release(G reference) throws IOException {
    assert reference != null;
    decRef(reference);
  }
  
}
