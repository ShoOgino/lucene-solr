package org.apache.lucene.analysis.util;

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

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.ServiceLoader;

/**
 * Helper class for loading named SPIs from classpath (e.g. Tokenizers, TokenStreams).
 * @lucene.internal
 */
public final class AnalysisSPILoader<S extends AbstractAnalysisFactory> {

  private final Map<String,Class<S>> services;

  /** This field is a hack for LuceneTestCase to get access
   * to the modifiable map (to work around bugs in IBM J9) */
  @SuppressWarnings("unused")
  @Deprecated
  // Hackidy-Häck-Hack for bugs in IBM J9 ServiceLoader
  private final Map<String,Class<S>> modifiableServices;
  
  private final Class<S> clazz;

  public AnalysisSPILoader(Class<S> clazz) {
    this.clazz = clazz;
    final ServiceLoader<S> loader = ServiceLoader.load(clazz);
    final LinkedHashMap<String,Class<S>> services = new LinkedHashMap<String,Class<S>>();
    final String suffix = clazz.getSimpleName();
    for (final S service : loader) {
      final String clazzName = service.getClass().getSimpleName();
      final int suffixIndex = clazzName.lastIndexOf(suffix);
      final String name = clazzName.substring(0, suffixIndex).toLowerCase(Locale.ROOT);
      // only add the first one for each name, later services will be ignored
      // this allows to place services before others in classpath to make 
      // them used instead of others
      if (!services.containsKey(name)) {
        assert checkServiceName(name);
        services.put(name, (Class<S>) service.getClass());
      }
    }
    this.modifiableServices = services; // hack, remove when IBM J9 is fixed!
    this.services = Collections.unmodifiableMap(services);
  }
  
  /**
   * Validates that a service name meets the requirements of {@link NamedSPI}
   */
  public static boolean checkServiceName(String name) {
    // based on harmony charset.java
    if (name.length() >= 128) {
      throw new IllegalArgumentException("Illegal service name: '" + name + "' is too long (must be < 128 chars).");
    }
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (!isLetter(c) && !isDigit(c)) {
        throw new IllegalArgumentException("Illegal service name: '" + name + "' must be simple ascii alphanumeric.");
      }
    }
    return true;
  }
  
  /*
   * Checks whether a character is a letter (ascii) which are defined in the spec.
   */
  private static boolean isLetter(char c) {
      return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z');
  }

  /*
   * Checks whether a character is a digit (ascii) which are defined in the spec.
   */
  private static boolean isDigit(char c) {
      return ('0' <= c && c <= '9');
  }
  
  public S newInstance(String name) {
    final Class<S> service = services.get(name.toLowerCase(Locale.ROOT));
    if (service != null) {
      try {
        return service.newInstance();
      } catch (Exception e) {
        throw new IllegalArgumentException("SPI class of type "+clazz.getName()+" with name '"+name+"' cannot be instantiated. " +
        		"This is likely due to a misconfiguration of the java class '" + service.getName() + "': ", e);
      }
    } else {
      throw new IllegalArgumentException("A SPI class of type "+clazz.getName()+" with name '"+name+"' does not exist. "+
            "You need to add the corresponding JAR file supporting this SPI to your classpath."+
            "The current classpath supports the following names: "+availableServices());
    }
  }

  public Set<String> availableServices() {
    return services.keySet();
  }  
}
