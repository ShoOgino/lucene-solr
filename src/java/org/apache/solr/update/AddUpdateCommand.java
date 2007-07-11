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

package org.apache.solr.update;

import org.apache.lucene.document.Document;
/**
 * @version $Id$
 */
public class AddUpdateCommand extends UpdateCommand {
   // optional id in "internal" indexed form... if it is needed and not supplied,
   // it will be obtained from the doc.
   public String indexedId;

   public Document doc;
   public boolean allowDups;
   public boolean overwritePending;
   public boolean overwriteCommitted;

   public AddUpdateCommand() {
     super("add");
   }

   public String toString() {
     StringBuilder sb = new StringBuilder(commandName);
     sb.append(':');
     if (indexedId !=null) sb.append("id=").append(indexedId);
     sb.append(",allowDups=").append(allowDups);
     sb.append(",overwritePending=").append(overwritePending);
     sb.append(",overwriteCommitted=").append(overwriteCommitted);
     return sb.toString();
   }
 }
