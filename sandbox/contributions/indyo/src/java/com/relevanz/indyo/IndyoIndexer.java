package com.relevanz.indyo;

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
 *    "Apache POI" must not be used to endorse or promote products
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

import com.relevanz.indyo.contenthandler.FileContentHandlerFactory;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Entry point for search engine indexing.
 * <p>
 * SearchIndexer is responsible for creating the IndexWriter
 * {@see org.apache.lucene.index.IndexWriter} and passing it to
 *  DocumentHandlers {@link DocumentHandler} to index individual documents.
 * </p>
 *
 * @version $Id$
 */
public class IndyoIndexer
{
    private static Logger log = Logger.getLogger(IndyoIndexer.class);
    private IndexWriter fsWriter;
    private SearchConfiguration config;

    public IndyoIndexer(String indexDirectory, String configFile)
            throws IOException, IllegalConfigurationException
    {
        Analyzer a = new StandardAnalyzer();
        fsWriter = new IndexWriter(indexDirectory, a, true);
        fsWriter.maxFieldLength = 1000000;
        loadConfig(configFile);
    }

    /**
     * Indexes documents.
     */
    public synchronized void index(IndexDataSource ds) throws IOException, Exception
    {
        log.debug("Initiating search engine indexing...");
        long start = System.currentTimeMillis();
        // temporarily use an empty map whilst custom fields get implemented
        indexDataSource(ds, Collections.EMPTY_MAP);
        fsWriter.optimize();
        fsWriter.close();
        long stop = System.currentTimeMillis();
        log.debug("Indexing took " + (stop - start) + " milliseconds");
    }

    private void loadConfig(String configFile) throws IllegalConfigurationException
    {
        config = new SearchConfiguration(configFile);
        FileContentHandlerFactory.setHandlerRegistry(config.getContentHandlers());
    }

    private void indexDataSource(IndexDataSource source, Map customFields)
            throws Exception
    {
        Map[] data = source.getData();
        // here's a good place to spawn a couple of threads for indexing
        for (int i = 0; i < data.length; i++)
        {
            DocumentHandler docHandler =
                    new DocumentHandler(data[i], customFields, fsWriter);
            docHandler.process();
        }
    }
}
