package org.apache.solr.handler.component;
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

import org.mortbay.log.Log;
import org.apache.solr.util.AbstractSolrTestCase;
import org.apache.solr.core.SolrCore;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.TermsParams;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrRequestHandler;
import org.apache.solr.request.LocalSolrQueryRequest;
import org.apache.solr.request.SolrQueryResponse;

import java.util.Iterator;


/**
 *
 *
 **/
public class TermsComponentTest extends AbstractSolrTestCase {
  public String getSchemaFile() {
    return "schema.xml";
  }

  public String getSolrConfigFile() {
    return "solrconfig.xml";
  }

  public void setUp() throws Exception {
    super.setUp();

    assertU(adoc("id", "0", "lowerfilt", "a", "standardfilt", "a"));
    assertU(adoc("id", "1", "lowerfilt", "a", "standardfilt", "aa"));
    assertU(adoc("id", "2", "lowerfilt", "aa", "standardfilt", "aaa"));
    assertU(adoc("id", "3", "lowerfilt", "aaa", "standardfilt", "abbb"));
    assertU(adoc("id", "4", "lowerfilt", "ab", "standardfilt", "b"));
    assertU(adoc("id", "5", "lowerfilt", "abb", "standardfilt", "bb"));
    assertU(adoc("id", "6", "lowerfilt", "abc", "standardfilt", "bbbb"));
    assertU(adoc("id", "7", "lowerfilt", "b", "standardfilt", "c"));
    assertU(adoc("id", "8", "lowerfilt", "baa", "standardfilt", "cccc"));
    assertU(adoc("id", "9", "lowerfilt", "bbb", "standardfilt", "ccccc"));

    assertU("commit", commit());
  }

  public void testEmptyLower() throws Exception {
    SolrCore core = h.getCore();
    TermsComponent tc = (TermsComponent) core.getSearchComponent("termsComp");
    assertTrue("tc is null and it shouldn't be", tc != null);

    ModifiableSolrParams params = new ModifiableSolrParams();
    params.add(TermsParams.TERMS, "true");
    params.add(TermsParams.TERMS_FIELD, "lowerfilt");
    //no lower bound
    params.add(TermsParams.TERMS_UPPER, "b");
    params.add(TermsParams.TERMS_ROWS, String.valueOf(50));
    SolrRequestHandler handler;
    SolrQueryResponse rsp;
    NamedList values;
    NamedList terms;
    handler = core.getRequestHandler("/terms");
    assertTrue("handler is null and it shouldn't be", handler != null);
    rsp = new SolrQueryResponse();
    rsp.add("responseHeader", new SimpleOrderedMap());
    handler.handleRequest(new LocalSolrQueryRequest(core, params), rsp);
    values = rsp.getValues();
    terms = (NamedList) ((NamedList) values.get("terms")).get("lowerfilt");

    assertTrue("terms Size: " + terms.size() + " is not: " + 6, terms.size() == 6);
    assertTrue("a is null and it shouldn't be", terms.get("a") != null);
    assertTrue("aa is null and it shouldn't be", terms.get("aa") != null);
    assertTrue("aaa is null and it shouldn't be", terms.get("aaa") != null);
    assertTrue("ab is null and it shouldn't be", terms.get("ab") != null);
    assertTrue("abb is null and it shouldn't be", terms.get("abb") != null);
    assertTrue("abc is null and it shouldn't be", terms.get("abc") != null);
  }

  public void testNoField() throws Exception {
    SolrCore core = h.getCore();
    TermsComponent tc = (TermsComponent) core.getSearchComponent("termsComp");
    assertTrue("tc is null and it shouldn't be", tc != null);

    ModifiableSolrParams params = new ModifiableSolrParams();
    params.add(TermsParams.TERMS, "true");
    //no lower bound
    params.add(TermsParams.TERMS_LOWER, "d");
    params.add(TermsParams.TERMS_ROWS, String.valueOf(50));
    SolrRequestHandler handler;
    SolrQueryResponse rsp;

    handler = core.getRequestHandler("/terms");
    assertTrue("handler is null and it shouldn't be", handler != null);
    rsp = new SolrQueryResponse();
    rsp.add("responseHeader", new SimpleOrderedMap());
    handler.handleRequest(new LocalSolrQueryRequest(core, params), rsp);
    Exception exception = rsp.getException();
    assertTrue("exception is null and it shouldn't be", exception != null);
  }


  public void testMultipleFields() throws Exception {
    SolrCore core = h.getCore();
    TermsComponent tc = (TermsComponent) core.getSearchComponent("termsComp");
    assertTrue("tc is null and it shouldn't be", tc != null);

    ModifiableSolrParams params = new ModifiableSolrParams();
    params.add(TermsParams.TERMS, "true");
    params.add(TermsParams.TERMS_FIELD, "lowerfilt", "standardfilt");
    //no lower bound
    params.add(TermsParams.TERMS_UPPER, "b");
    params.add(TermsParams.TERMS_ROWS, String.valueOf(50));
    SolrRequestHandler handler;
    SolrQueryResponse rsp;
    NamedList values;
    NamedList terms;
    handler = core.getRequestHandler("/terms");
    assertTrue("handler is null and it shouldn't be", handler != null);
    rsp = new SolrQueryResponse();
    rsp.add("responseHeader", new SimpleOrderedMap());
    handler.handleRequest(new LocalSolrQueryRequest(core, params), rsp);
    values = rsp.getValues();
    NamedList tmp = (NamedList) values.get("terms");
    assertTrue("tmp Size: " + tmp.size() + " is not: " + 2, tmp.size() == 2);
    terms = (NamedList) tmp.get("lowerfilt");
    assertTrue("terms Size: " + terms.size() + " is not: " + 6, terms.size() == 6);
    terms = (NamedList) tmp.get("standardfilt");
    assertTrue("terms Size: " + terms.size() + " is not: " + 4, terms.size() == 4);
  }

  public void testPastUpper() throws Exception {
    SolrCore core = h.getCore();
    TermsComponent tc = (TermsComponent) core.getSearchComponent("termsComp");
    assertTrue("tc is null and it shouldn't be", tc != null);

    ModifiableSolrParams params = new ModifiableSolrParams();
    params.add(TermsParams.TERMS, "true");
    params.add(TermsParams.TERMS_FIELD, "lowerfilt");
    //no lower bound
    params.add(TermsParams.TERMS_LOWER, "d");
    params.add(TermsParams.TERMS_ROWS, String.valueOf(50));
    SolrRequestHandler handler;
    SolrQueryResponse rsp;
    NamedList values;
    NamedList terms;
    handler = core.getRequestHandler("/terms");
    assertTrue("handler is null and it shouldn't be", handler != null);
    rsp = new SolrQueryResponse();
    rsp.add("responseHeader", new SimpleOrderedMap());
    handler.handleRequest(new LocalSolrQueryRequest(core, params), rsp);
    values = rsp.getValues();
    terms = (NamedList) ((NamedList) values.get("terms")).get("lowerfilt");
    assertTrue("terms Size: " + terms.size() + " is not: " + 0, terms.size() == 0);
  }

  public void test() throws Exception {
    SolrCore core = h.getCore();
    TermsComponent tc = (TermsComponent) core.getSearchComponent("termsComp");
    assertTrue("tc is null and it shouldn't be", tc != null);

    ModifiableSolrParams params = new ModifiableSolrParams();
    params.add(TermsParams.TERMS, "true");
    params.add(TermsParams.TERMS_FIELD, "lowerfilt");
    params.add(TermsParams.TERMS_LOWER, "a");
    params.add(TermsParams.TERMS_UPPER, "b");
    params.add(TermsParams.TERMS_ROWS, String.valueOf(50));
    SolrRequestHandler handler;
    SolrQueryResponse rsp;
    NamedList values;
    NamedList terms;
    handler = core.getRequestHandler("/terms");
    assertTrue("handler is null and it shouldn't be", handler != null);
    rsp = new SolrQueryResponse();
    rsp.add("responseHeader", new SimpleOrderedMap());
    handler.handleRequest(new LocalSolrQueryRequest(core, params), rsp);
    values = rsp.getValues();
    terms = (NamedList) ((NamedList) values.get("terms")).get("lowerfilt");
    assertTrue("terms Size: " + terms.size() + " is not: " + 6, terms.size() == 6);
    assertTrue("aa is null and it shouldn't be", terms.get("aa") != null);
    assertTrue("aaa is null and it shouldn't be", terms.get("aaa") != null);
    assertTrue("ab is null and it shouldn't be", terms.get("ab") != null);
    assertTrue("abb is null and it shouldn't be", terms.get("abb") != null);
    assertTrue("abc is null and it shouldn't be", terms.get("abc") != null);
    assertTrue("a is null", terms.get("a") != null);
    assertTrue("b is not null and it should be", terms.get("b") == null);

    params.add(TermsParams.TERMS_UPPER_INCLUSIVE, "true");
    rsp = new SolrQueryResponse();
    rsp.add("responseHeader", new SimpleOrderedMap());
    handler.handleRequest(new LocalSolrQueryRequest(core, params), rsp);
    values = rsp.getValues();
    terms = (NamedList) ((NamedList) values.get("terms")).get("lowerfilt");
    assertTrue("terms Size: " + terms.size() + " is not: " + 7, terms.size() == 7);
    assertTrue("aa is null and it shouldn't be", terms.get("aa") != null);
    assertTrue("ab is null and it shouldn't be", terms.get("ab") != null);
    assertTrue("aaa is null and it shouldn't be", terms.get("aaa") != null);
    assertTrue("abb is null and it shouldn't be", terms.get("abb") != null);
    assertTrue("abc is null and it shouldn't be", terms.get("abc") != null);
    assertTrue("b is null and it shouldn't be", terms.get("b") != null);
    assertTrue("a is null", terms.get("a") != null);
    assertTrue("baa is not null", terms.get("baa") == null);

    params.add(TermsParams.TERMS_LOWER_INCLUSIVE, "false");
    rsp = new SolrQueryResponse();
    rsp.add("responseHeader", new SimpleOrderedMap());
    handler.handleRequest(new LocalSolrQueryRequest(core, params), rsp);
    values = rsp.getValues();
    terms = (NamedList) ((NamedList) values.get("terms")).get("lowerfilt");
    assertTrue("terms Size: " + terms.size() + " is not: " + 6, terms.size() == 6);
    assertTrue("aa is null and it shouldn't be", terms.get("aa") != null);
    assertTrue("ab is null and it shouldn't be", terms.get("ab") != null);
    assertTrue("aaa is null and it shouldn't be", terms.get("aaa") != null);
    assertTrue("abb is null and it shouldn't be", terms.get("abb") != null);
    assertTrue("abc is null and it shouldn't be", terms.get("abc") != null);
    assertTrue("b is null and it shouldn't be", terms.get("b") != null);
    assertTrue("a is not null", terms.get("a") == null);
    assertTrue("baa is not null", terms.get("baa") == null);



    params = new ModifiableSolrParams();
    params.add(TermsParams.TERMS, "true");
    params.add(TermsParams.TERMS_FIELD, "lowerfilt");
    params.add(TermsParams.TERMS_LOWER, "a");
    params.add(TermsParams.TERMS_UPPER, "b");
    params.add(TermsParams.TERMS_ROWS, String.valueOf(2));
    rsp = new SolrQueryResponse();
    rsp.add("responseHeader", new SimpleOrderedMap());
    handler.handleRequest(new LocalSolrQueryRequest(core, params), rsp);
    values = rsp.getValues();
    terms = (NamedList) ((NamedList) values.get("terms")).get("lowerfilt");
    assertTrue("terms Size: " + terms.size() + " is not: " + 2, terms.size() == 2);
    assertTrue("aa is null and it shouldn't be", terms.get("a") != null);
    assertTrue("aaa is null and it shouldn't be", terms.get("aa") != null);
    assertTrue("abb is not null", terms.get("abb") == null);
    assertTrue("abc is not null", terms.get("abc") == null);
    assertTrue("b is null and it shouldn't be", terms.get("b") == null);
    assertTrue("baa is not null", terms.get("baa") == null);
  }
}
