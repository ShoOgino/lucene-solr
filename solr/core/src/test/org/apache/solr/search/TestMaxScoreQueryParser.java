package org.apache.solr.search;

/*
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

import org.apache.lucene.search.*;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.util.AbstractSolrTestCase;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

public class TestMaxScoreQueryParser extends AbstractSolrTestCase {
  Query q;
  BooleanClause[] clauses;

  @BeforeClass
  public static void beforeClass() throws Exception {
    initCore("solrconfig.xml", "schema.xml");
  }

  @Test
  public void testFallbackToLucene() {
    q = parse("foo");
    assertTrue(q instanceof TermQuery);

    q = parse("price:[0 TO 10]");
    assertTrue(q instanceof NumericRangeQuery);
  }

  @Test
  public void testNoShouldClauses() {
    q = parse("+foo +bar");
    clauses = clauses(q);
    assertEquals(2, clauses.length);
    assertTrue(clauses[0].isRequired());
    assertTrue(clauses[1].isRequired());

    q = parse("+foo -bar");
    clauses = clauses(q);
    assertEquals(2, clauses.length);
    assertTrue(clauses[0].isRequired());
    assertTrue(clauses[1].isProhibited());
  }

  @Test
  public void testPureMax() {
    q = parse("foo bar");
    clauses = clauses(q);
    assertEquals(1, clauses.length);
    assertTrue(clauses[0].getQuery() instanceof DisjunctionMaxQuery);
    assertEquals(0.0, ((DisjunctionMaxQuery) clauses[0].getQuery()).getTieBreakerMultiplier(), 1e-15);
    ArrayList<Query> qa = ((DisjunctionMaxQuery) clauses[0].getQuery()).getDisjuncts();
    assertEquals(2, qa.size());
    assertEquals("text:foo", qa.get(0).toString());
  }

  @Test
  public void testMaxAndProhibited() {
    q = parse("foo bar -baz");
    clauses = clauses(q);
    assertEquals(2, clauses.length);
    assertTrue(clauses[0].getQuery() instanceof DisjunctionMaxQuery);
    assertTrue(clauses[1].getQuery() instanceof TermQuery);
    assertEquals("text:baz", clauses[1].getQuery().toString());
    assertTrue(clauses[1].isProhibited());
  }

  @Test
  public void testTie() {
    q = parse("foo bar", "tie", "0.5");
    clauses = clauses(q);
    assertEquals(1, clauses.length);
    assertTrue(clauses[0].getQuery() instanceof DisjunctionMaxQuery);
    assertEquals(0.5, ((DisjunctionMaxQuery) clauses[0].getQuery()).getTieBreakerMultiplier(), 1e-15);
  }

  //
  // Helper methods
  //

  private Query parse(String q, String... params) {
    try {
      ModifiableSolrParams p = new ModifiableSolrParams();
      ArrayList<String> al = new ArrayList<String>(Arrays.asList(params));
      while(al.size() >= 2) {
        p.add(al.remove(0), al.remove(0));
      }
      return new MaxScoreQParser(q, p, new ModifiableSolrParams(), req(q)).parse();
    } catch (SyntaxError syntaxError) {
      fail("Failed with exception "+syntaxError.getMessage());
    }
    fail("Parse failed");
    return null;
  }

  private BooleanClause[] clauses(Query q) {
    return ((BooleanQuery) q).getClauses();
  }
}
