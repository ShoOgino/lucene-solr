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

package org.apache.solr.common.util;

import org.apache.solr.SolrTestCaseJ4;
import org.junit.Test;

/**
 *
 */
public class TestTimeSource extends SolrTestCaseJ4 {

  @Test
  public void testEpochTime() throws Exception {
    doTestEpochTime(TimeSource.CURRENT_TIME);
    doTestEpochTime(TimeSource.NANO_TIME);
    doTestEpochTime(TimeSource.get("simTime:50"));
  }

  private void doTestEpochTime(TimeSource ts) throws Exception {
    long prevTime = ts.getTime();
    long prevEpochTime = ts.getEpochTime();
    long delta = 500000000; // 500 ms
    for (int i = 0; i < 10; i++) {
      ts.sleep(500);
      long curTime = ts.getTime();
      long curEpochTime = ts.getEpochTime();
      long diff = prevTime + delta - curTime;
      assertTrue(ts + " time diff=" + diff, diff < 100000);
      diff = prevEpochTime + delta - curEpochTime;
      assertTrue(ts + " epochTime diff=" + diff, diff < 100000);
      prevTime = curTime;
      prevEpochTime = curEpochTime;
    }
  }
}
