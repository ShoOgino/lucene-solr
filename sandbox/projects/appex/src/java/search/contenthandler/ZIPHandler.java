package search.contenthandler;

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
 *    "Apache Turbine" must not be used to endorse or promote products
 *    derived from this software without prior written permission. For
 *    written permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    "Apache Turbine", nor may "Apache" appear in their name, without
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

import search.util.IOUtils;
import org.apache.log4j.Category;
import org.apache.lucene.document.Document;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Handles Zip files.
 *
 * @author <a href="mailto:kelvin@relevanz.com">Kelvin Tan</a>
 */
public class ZIPHandler extends NestedFileContentHandlerAdapter
{
    static Category cat = Category.getInstance(ZIPHandler.class.getName());

    public void parse(Document doc, File f)
    {
        if (!f.exists())
            return;
        try
        {
            ZipFile zFile = new ZipFile(f);
            for (Enumeration e = zFile.entries(); e.hasMoreElements();)
            {
                ZipEntry entry = (ZipEntry) e.nextElement();
                String entryName = entry.getName();
                IOUtils.writeToTempFile(zFile.getInputStream(entry),
                                        TEMP_FOLDER + entryName);
                if (!entry.isDirectory())
                {
                    // create a new DataMap for each zip entry
                    Map dataMap = new HashMap();
                    dataMap.put("filePath", TEMP_FOLDER + entryName);
                    dataMapList.add(dataMap);
                }
            }
            zFile.close();
        }
        catch (ZipException ze)
        {
            cat.error("ZipException parsing zip:" + ze.getMessage(), ze);
        }
        catch (IOException ioe)
        {
            cat.error("IOException parsing zip:" + ioe.getMessage(), ioe);
        }
    }

    public Object clone()
    {
        return new ZIPHandler();
    }
}