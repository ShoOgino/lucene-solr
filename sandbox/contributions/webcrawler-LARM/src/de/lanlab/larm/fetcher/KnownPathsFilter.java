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

package de.lanlab.larm.fetcher;

import java.net.*;

/**
 * this can be considered a hack
 * @TODO implement this as a fast way to filter out different URL endings or beginnings
 * @version $Id$
 */
public class KnownPathsFilter extends Filter implements MessageListener
{

    MessageHandler messageHandler;

    String[] pathsToFilter =
    {
        "/robots.txt"
    };

    String[] hostFilter =
    {
        "www.nm.informatik.uni-muenchen.de",
        "cgi.cip.informatik.uni-muenchen.de"
    };

    String[] filesToFilter =
    {
            // exclude Apache directory files
            "/?D=D",
            "/?S=D",
            "/?M=D",
            "/?N=D",
            "/?D=A",
            "/?S=A",
            "/?M=A",
            "/?N=A",
    };

    int pathLength;
    int fileLength;
    int hostLength;


    /**
     * Constructor for the KnownPathsFilter object
     */
    public KnownPathsFilter()
    {
        pathLength = pathsToFilter.length;
        fileLength = filesToFilter.length;
        hostLength = hostFilter.length;
    }


    /**
     * Description of the Method
     *
     * @param message  Description of the Parameter
     * @return         Description of the Return Value
     */
    public Message handleRequest(Message message)
    {
        URL url = ((URLMessage)message).getUrl();
        String file = url.getFile();
        String host = url.getHost();
        int i;
        for (i = 0; i < pathLength; i++)
        {
            if (file.startsWith(pathsToFilter[i]))
            {
                filtered++;
                return null;
            }
        }
        for (i = 0; i < fileLength; i++)
        {
            if (file.endsWith(filesToFilter[i]))
            {
                filtered++;
                return null;
            }
        }
        for (i = 0; i<hostLength; i++)
        {
            if(hostFilter[i].equals(host))
            {
                filtered++;
                return null;
            }
        }
        return message;
    }


    /**
     * will be called as soon as the Listener is added to the Message Queue
     *
     * @param handler  the Message Handler
     */
    public void notifyAddedToMessageHandler(MessageHandler handler)
    {
        this.messageHandler = messageHandler;
    }
}
