/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2001 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache" and "Apache Software Foundation" and
 *    "Apache Lucene" must not be used to endorse or promote products
 *    derived from this software without prior written permission. For
 *    written permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    "Apache Lucene", nor may "Apache" appear in their name, without
 *    prior written permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

package de.lanlab.larm.util;

import java.io.*;
import java.util.*;


class StoreException extends RuntimeException
{
    Exception origException;


    /**
     * Constructor for the StoreException object
     *
     * @param e  Description of the Parameter
     */
    public StoreException(Exception e)
    {
        origException = e;
    }


    /**
     * Gets the message attribute of the StoreException object
     *
     * @return   The message value
     */
    public String getMessage()
    {
        return origException.getMessage();
    }


    /**
     * Description of the Method
     */
    public void printStackTrace()
    {
        System.err.println("StoreException occured with reason: " + origException.getMessage());
        origException.printStackTrace();
    }
}

/**
 * internal class that represents one block within a queue
 *
 * @author    Clemens Marschner
 * @created   3. Januar 2002
 */
class QueueBlock
{


    /**
     * the elements section will be set to null if it is on disk Vector elements
     * must be Serializable
     */
    LinkedList elements;

    /**
     * Anzahl Elemente im Block. Kopie von elements.size()
     */
    int size;

    /**
     * maximale Blockgr��e
     */
    int maxSize;

    /**
     * if set, elements is null and block was written to file
     */
    boolean onDisk;

    /**
     * Blockname
     */
    String name;


    /**
     * initialisiert den Block
     *
     * @param name     Der Blockname (muss eindeutig sein, sonst Kollision auf
     *      Dateiebene)
     * @param maxSize  maximale Blockgr��e. �ber- und Unterl�ufe werden durch
     *      Exceptions behandelt
     */
    public QueueBlock(String name, int maxSize)
    {
        this.name = name;
        this.onDisk = false;
        this.elements = new LinkedList();
        this.maxSize = maxSize;
    }


    /**
     * serialisiert und speichert den Block auf Platte
     *
     * @exception StoreException  Description of the Exception
     */
    public void store()
        throws StoreException
    {
        try
        {
            ObjectOutputStream o = new ObjectOutputStream(new FileOutputStream(getFileName()));
            o.writeObject(elements);
            elements = null;
            o.close();
            onDisk = true;
            //System.out.println("CachingQueue.store: Block stored");
        }
        catch (IOException e)
        {
            System.err.println("CachingQueue.store: IOException");
            throw new StoreException(e);
        }
    }


    /**
     * @return   the filename of the block
     */
    String getFileName()
    {
        // package protected!

        return "cachingqueue/" + name + ".cqb";
    }


    /**
     * load the block from disk
     *
     * @exception StoreException  Description of the Exception
     */
    public void load()
        throws StoreException
    {
        try
        {
            ObjectInputStream i = new ObjectInputStream(new FileInputStream(getFileName()));
            elements = (LinkedList) i.readObject();
            i.close();
            onDisk = false;
            size = elements.size();
            if (!(new File(getFileName()).delete()))
            {
                System.err.println("CachingQueue.load: file could not be deleted");
            }
            //System.out.println("CachingQueue.load: Block loaded");
        }
        catch (Exception e)
        {
            System.err.println("CachingQueue.load: Exception " + e.getClass().getName() + " occured");
            throw new StoreException(e);
        }
    }


    /**
     * inserts an object at the start of the queue must be synchronized by
     * calling class to be thread safe
     *
     * @param o                   Description of the Parameter
     * @exception StoreException  Description of the Exception
     */
    public void insert(Object o)
        throws StoreException
    {
        if (onDisk)
        {
            load();
        }
        if (size >= maxSize)
        {
            throw new OverflowException();
        }
        elements.addFirst(o);
        size++;
    }


    /**
     * gibt das letzte Element aus der Queue zur�ck und l�scht dieses must be
     * made synchronized by calling class to be thread safe
     *
     * @return                        Description of the Return Value
     * @exception UnderflowException  Description of the Exception
     * @exception StoreException      Description of the Exception
     */
    public Object remove()
        throws UnderflowException, StoreException
    {
        if (onDisk)
        {
            load();
        }
        if (size <= 0)
        {
            throw new UnderflowException();
        }
        size--;
        return elements.removeLast();
    }


    /**
     * @return   the number of elements in the block
     */
    public int size()
    {
        return size;
    }


    /**
     * destructor. Assures that all files are deleted, even if the queue was not
     * empty at the time when the program ended
     */
    public void finalize()
    {
        // System.err.println("finalize von " + name + " called");
        if (onDisk)
        {
            // temp-Datei l�schen. Passiert, wenn z.B. eine Exception aufgetreten ist
            // System.err.println("CachingQueue.finalize von Block " + name + ": l�sche Datei");
            if (!(new File(getFileName()).delete()))
            {
                // Dateifehler m�glich durch Exception: ignorieren

                // System.err.println("CachingQueue.finalize: file could not be deleted although onDisk was true");
            }
        }
    }
}


/**
 * this class holds a queue whose data is kept on disk whenever possible.
 * It's a single ended queue, meaning data can only be added at the front and
 * taken from the back. the queue itself is divided into blocks. Only the first
 * and last blocks are kept in main memory, the rest is stored on disk. Only a
 * LinkedList entry is kept in memory then.
 * Blocks are swapped if an overflow (in case of insertions) or underflow (in case
 * of removals) occur.<br>
 *
 * <pre>
 *         +---+---+---+---+-+
 *  put -> | M | S | S | S |M| -> remove
 *         +---+---+---+---+-+
 * </pre>
 * the maximum number of entries can be specified with the blockSize parameter. Thus,
 * the queue actually holds a maximum number of 2 x blockSize objects in main memory,
 * plus a few bytes for each block.<br>
 * The objects contained in the blocks are stored with the standard Java
 * serialization mechanism
 * The files are named "cachingqueue\\Queuename_BlockNumber.cqb"
 * note that the class is not synchronized
 * @author    Clemens Marschner
 * @created   3. Januar 2002
 */

public class CachingQueue implements Queue
{


    /**
     * the Blocks
     */
    LinkedList queueBlocks;

    /**
     * fast access to the first block
     */
    QueueBlock first = null;

    /**
     * fast access to the last block
     */
    QueueBlock last = null;

    /**
     * maximum block size
     */
    int blockSize;

    /**
     * "primary key" identity count for each block
     */
    int blockCount = 0;

    /**
     * active blocks
     */
    int numBlocks = 0;

    /**
     * queue name
     */
    String name;

    /**
     * total number of objects
     */
    int size;


    /**
     * init
     *
     * @param name the name of the queue, used in files names
     * @param blockSize maximum number of objects stored in one block
     */
    public CachingQueue(String name, int blockSize)
    {
        queueBlocks = new LinkedList();
        this.name = name;
        this.blockSize = blockSize;
        File cq = new File("cachingqueue");
        cq.mkdir();
    }


    /**
     * inserts an object to the front of the queue
     *
     * @param o                   the object to be inserted. must implement Serializable
     * @exception StoreException  encapsulates Exceptions that occur when writing to hard disk
     */
    public synchronized void insert(Object o)
        throws StoreException
    {
        if (last == null && first == null)
        {
            first = last = newBlock();
            queueBlocks.addFirst(first);
            numBlocks++;
        }
        if (last == null && first != null)
        {
            // affirm((last==null && first==null) || (last!= null && first!=null));
            System.err.println("Error in CachingQueue: last!=first==null");
        }

        if (first.size() >= blockSize)
        {
            // save block and create a new one
            QueueBlock newBlock = newBlock();
            numBlocks++;
            if (last != first)
            {
                first.store();
            }
            queueBlocks.addFirst(newBlock);
            first = newBlock;
        }
        first.insert(o);
        size++;
    }


    /**
     * returns the last object from the queue
     *
     * @return                     the object returned
     *
     * @exception StoreException   Description of the Exception
     * @exception UnderflowException if the queue was empty
     */
    public synchronized Object remove()
        throws StoreException, UnderflowException
    {
        if (last == null)
        {
            throw new UnderflowException();
        }
        if (last.size() <= 0)
        {
            queueBlocks.removeLast();
            numBlocks--;
            if (numBlocks == 1)
            {
                last = first;
            }
            else if (numBlocks == 0)
            {
                first = last = null;
                throw new UnderflowException();
            }
            else if (numBlocks < 0)
            {
                // affirm(numBlocks >= 0)
                System.err.println("CachingQueue.remove: numBlocks<0!");
                throw new UnderflowException();
            }
            else
            {
                last = (QueueBlock) queueBlocks.getLast();
            }
        }
        --size;
        return last.remove();
    }


    /**
     * not supported
     *
     * @param c  Description of the Parameter
     */
    public void insertMultiple(java.util.Collection c)
    {
        throw new UnsupportedOperationException();
    }


    /**
     * creates a new block
     *
     * @return   Description of the Return Value
     */
    private QueueBlock newBlock()
    {
        return new QueueBlock(name + "_" + blockCount++, blockSize);
    }


    /**
     * total number of objects contained in the queue
     *
     * @return   Description of the Return Value
     */
    public int size()
    {
        return size;
    }


    /**
     * testing
     *
     * @param args  The command line arguments
     */
    public static void main(String[] args)
    {
        System.out.println("Test1: " + CachingQueueTester.testUnderflow());
        System.out.println("Test2: " + CachingQueueTester.testInsert());
        System.out.println("Test3: " + CachingQueueTester.testBufReadWrite());
        System.out.println("Test4: " + CachingQueueTester.testBufReadWrite2());
        System.out.println("Test5: " + CachingQueueTester.testUnderflow2());
        System.out.println("Test6: " + CachingQueueTester.testBufReadWrite3());
        System.out.println("Test7: " + CachingQueueTester.testExceptions());
    }
}

/**
 * Testklasse TODO: auslagern und per JUnit handhaben
 *
 * @author    Administrator
 * @created   3. Januar 2002
 */
class AssertionFailedException extends RuntimeException
{
}

/**
 * Testklasse. Enth�lt einige Tests f�r die Funktionalit�t der CachingQueue
 *
 * @author    Administrator
 * @created   3. Januar 2002
 */
class CachingQueueTester
{


    /**
     * A unit test for JUnit
     *
     * @return   Description of the Return Value
     */
    public static boolean testUnderflow()
    {
        CachingQueue cq = new CachingQueue("testQueue1", 10);
        try
        {
            cq.remove();
        }
        catch (UnderflowException e)
        {
            return true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return false;
    }


    /**
     * A unit test for JUnit
     *
     * @return   Description of the Return Value
     */
    public static boolean testInsert()
    {
        CachingQueue cq = new CachingQueue("testQueue2", 10);
        String test = "Test1";
        affirm(cq.size() == 0);
        cq.insert(test);
        affirm(cq.size() == 1);
        return (cq.remove() == test);
    }


    /**
     * A unit test for JUnit
     *
     * @return   Description of the Return Value
     */
    public static boolean testBufReadWrite()
    {
        CachingQueue cq = new CachingQueue("testQueue3", 2);
        String test1 = "Test1";
        String test2 = "Test2";
        String test3 = "Test3";
        cq.insert(test1);
        cq.insert(test2);
        cq.insert(test3);
        affirm(cq.size() == 3);
        cq.remove();
        cq.remove();
        affirm(cq.size() == 1);
        return (cq.remove() == test3);
    }


    /**
     * A unit test for JUnit
     *
     * @return   Description of the Return Value
     */
    public static boolean testBufReadWrite2()
    {
        CachingQueue cq = new CachingQueue("testQueue4", 2);
        String test1 = "Test1";
        String test2 = "Test2";
        String test3 = "Test3";
        String test4 = "Test4";
        String test5 = "Test5";
        cq.insert(test1);
        cq.insert(test2);
        cq.insert(test3);
        cq.insert(test4);
        cq.insert(test5);
        affirm(cq.size() == 5);
        String t = (String) cq.remove();
        affirm(t.equals(test1));
        t = (String) cq.remove();
        affirm(t.equals(test2));
        t = (String) cq.remove();
        affirm(t.equals(test3));
        t = (String) cq.remove();
        affirm(t.equals(test4));
        t = (String) cq.remove();
        affirm(cq.size() == 0);
        return (t.equals(test5));
    }


    /**
     * Description of the Method
     *
     * @param expr  Description of the Parameter
     */
    public static void affirm(boolean expr)
    {
        if (!expr)
        {
            throw new AssertionFailedException();
        }
    }


    /**
     * A unit test for JUnit
     *
     * @return   Description of the Return Value
     */
    public static boolean testUnderflow2()
    {
        CachingQueue cq = new CachingQueue("testQueue5", 2);
        String test1 = "Test1";
        String test2 = "Test2";
        String test3 = "Test3";
        String test4 = "Test4";
        String test5 = "Test5";
        cq.insert(test1);
        cq.insert(test2);
        cq.insert(test3);
        cq.insert(test4);
        cq.insert(test5);
        affirm(cq.remove().equals(test1));
        affirm(cq.remove().equals(test2));
        affirm(cq.remove().equals(test3));
        affirm(cq.remove().equals(test4));
        affirm(cq.remove().equals(test5));
        try
        {
            cq.remove();
        }
        catch (UnderflowException e)
        {
            return true;
        }
        return false;
    }


    /**
     * A unit test for JUnit
     *
     * @return   Description of the Return Value
     */
    public static boolean testBufReadWrite3()
    {
        CachingQueue cq = new CachingQueue("testQueue4", 1);
        String test1 = "Test1";
        String test2 = "Test2";
        String test3 = "Test3";
        String test4 = "Test4";
        String test5 = "Test5";
        cq.insert(test1);
        cq.insert(test2);
        cq.insert(test3);
        cq.insert(test4);
        cq.insert(test5);
        String t = (String) cq.remove();
        affirm(t.equals(test1));
        t = (String) cq.remove();
        affirm(t.equals(test2));
        t = (String) cq.remove();
        affirm(t.equals(test3));
        t = (String) cq.remove();
        affirm(t.equals(test4));
        t = (String) cq.remove();
        return (t.equals(test5));
    }


    /**
     * A unit test for JUnit
     *
     * @return   Description of the Return Value
     */
    public static boolean testExceptions()
    {
        System.gc();
        CachingQueue cq = new CachingQueue("testQueue5", 1);
        String test1 = "Test1";
        String test2 = "Test2";
        String test3 = "Test3";
        String test4 = "Test4";
        String test5 = "Test5";
        cq.insert(test1);
        cq.insert(test2);
        cq.insert(test3);
        cq.insert(test4);
        cq.insert(test5);
        try
        {
            if (!(new File("testQueue5_1.cqb").delete()))
            {
                System.err.println("CachingQueueTester.textExceptions: Store 1 nicht vorhanden. Filename ge�ndert?");
            }
            if (!(new File("testQueue5_2.cqb").delete()))
            {
                System.err.println("CachingQueueTester.textExceptions: Store 2 nicht vorhanden. Filename ge�ndert?");
            }
            String t = (String) cq.remove();
            affirm(t.equals(test1));
            t = (String) cq.remove();
            affirm(t.equals(test2));
            t = (String) cq.remove();
            affirm(t.equals(test3));
            t = (String) cq.remove();
            affirm(t.equals(test4));
            t = (String) cq.remove();
            affirm(t.equals(test5));
        }
        catch (StoreException e)
        {
            return true;
        }
        finally
        {
            cq = null;
            System.gc();
            // finalizer m�ssten aufgerufen werden
        }
        return false;
    }

}
