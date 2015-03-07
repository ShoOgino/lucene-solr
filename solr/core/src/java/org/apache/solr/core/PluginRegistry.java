package org.apache.solr.core;

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


import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.util.ResourceLoader;
import org.apache.lucene.analysis.util.ResourceLoaderAware;
import org.apache.solr.common.SolrException;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.request.SolrRequestHandler;
import org.apache.solr.util.plugin.NamedListInitializedPlugin;
import org.apache.solr.util.plugin.PluginInfoInitialized;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Collections.singletonList;

/**
 * This manages the lifecycle of a set of plugin of the same type .
 */
public class PluginRegistry<T> implements AutoCloseable {
  public static Logger log = LoggerFactory.getLogger(PluginRegistry.class);

  private Map<String, PluginHolder<T>> registry = new HashMap<>();
  private Map<String, PluginHolder<T>> immutableRegistry = Collections.unmodifiableMap(registry);
  private String def;
  private Class klass;
  private SolrCore core;
  private SolrConfig.SolrPluginInfo meta;

  public PluginRegistry(Class<T> klass, SolrCore core) {
    this.core = core;
    this.klass = klass;
    meta = SolrConfig.classVsSolrPluginInfo.get(klass.getName());
    if (meta == null) {
      throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "Unknown Plugin : " + klass.getName());
    }
  }

  static void initInstance(Object inst, PluginInfo info, SolrCore core) {
    if (inst instanceof PluginInfoInitialized) {
      ((PluginInfoInitialized) inst).init(info);
    } else if (inst instanceof NamedListInitializedPlugin) {
      ((NamedListInitializedPlugin) inst).init(info.initArgs);
    } else if (inst instanceof SolrRequestHandler) {
      ((SolrRequestHandler) inst).init(info.initArgs);
    }
    if (inst instanceof SearchComponent) {
      ((SearchComponent) inst).setName(info.name);
    }
    if (inst instanceof RequestHandlerBase) {
      ((RequestHandlerBase) inst).setPluginInfo(info);
    }

  }

  PluginHolder<T> createPlugin(PluginInfo info, SolrCore core) {
    if ("true".equals(String.valueOf(info.attributes.get("runtimeLib")))) {
      log.info(" {} : '{}'  created with runtimeLib=true ", meta.tag, info.name);
      return new LazyPluginHolder<>(meta, info, core, core.getMemClassLoader());
    } else if ("lazy".equals(info.attributes.get("startup")) && meta.options.contains(SolrConfig.PluginOpts.LAZY)) {
      log.info("{} : '{}' created with startup=lazy ", meta.tag, info.name);
      return new LazyPluginHolder<T>(meta, info, core, core.getResourceLoader());
    } else {
      T inst = core.createInstance(info.className, (Class<T>) meta.clazz, meta.tag, null, core.getResourceLoader());
      initInstance(inst, info, core);
      return new PluginHolder<>(info, inst);
    }
  }

  boolean alias(String src, String target) {
    PluginHolder<T> a = registry.get(src);
    if (a == null) return false;
    PluginHolder<T> b = registry.get(target);
    if (b != null) return false;
    registry.put(target, a);
    return true;
  }

  /**
   * Get a plugin by name. If the plugin is not already instantiated, it is
   * done here
   */
  public T get(String name) {
    PluginHolder<T> result = registry.get(name);
    return result == null ? null : result.get();
  }

  /**
   * Fetches a plugin by name , or the default
   *
   * @param name       name using which it is registered
   * @param useDefault Return the default , if a plugin by that name does not exist
   */
  public T get(String name, boolean useDefault) {
    T result = get(name);
    if (useDefault && result == null) return get(def);
    return result;
  }

  public Set<String> keySet() {
    return immutableRegistry.keySet();
  }

  /**
   * register a plugin by a name
   */
  public T put(String name, T plugin) {
    if (plugin == null) return null;
    PluginHolder<T> old = put(name, new PluginHolder<T>(null, plugin));
    return old == null ? null : old.get();
  }


  PluginHolder<T> put(String name, PluginHolder<T> plugin) {
    PluginHolder<T> old = registry.put(name, plugin);
    if (plugin.pluginInfo != null && plugin.pluginInfo.isDefault()) {
      setDefault(name);
    }
    if (plugin.isLoaded()) registerMBean(plugin.get(), core, name);
    return old;
  }

  void setDefault(String def) {
    if (!registry.containsKey(def)) return;
    if (this.def != null) log.warn("Multiple defaults for : " + meta.tag);
    this.def = def;
  }

  public Map<String, PluginHolder<T>> getRegistry() {
    return immutableRegistry;
  }

  public boolean contains(String name) {
    return registry.containsKey(name);
  }

  String getDefault() {
    return def;
  }

  T remove(String name) {
    PluginHolder<T> removed = registry.remove(name);
    return removed == null ? null : removed.get();
  }

  void init(Map<String, T> defaults, SolrCore solrCore) {
    init(defaults, solrCore, solrCore.getSolrConfig().getPluginInfos(klass.getName()));
  }

  /**
   * Initializes the plugins after reading the meta data from {@link org.apache.solr.core.SolrConfig}.
   *
   * @param defaults These will be registered if not explicitly specified
   */
  void init(Map<String, T> defaults, SolrCore solrCore, List<PluginInfo> infos) {
    core = solrCore;
    for (PluginInfo info : infos) {
      PluginHolder<T> o = createPlugin(info, solrCore);
      String name = info.name;
      if (meta.clazz.equals(SolrRequestHandler.class)) name = RequestHandlers.normalize(info.name);
      PluginHolder<T> old = put(name, o);
      if (old != null) log.warn("Multiple entries of {} with name {}", meta.tag, name);
    }
    for (Map.Entry<String, T> e : defaults.entrySet()) {
      if (!contains(e.getKey())) {
        put(e.getKey(), new PluginHolder<T>(null, e.getValue()));
      }
    }
  }

  /**
   * To check if a plugin by a specified name is already loaded
   */
  public boolean isLoaded(String name) {
    PluginHolder<T> result = registry.get(name);
    if (result == null) return false;
    return result.isLoaded();
  }

  private static void registerMBean(Object inst, SolrCore core, String pluginKey) {
    if (core == null) return;
    if (inst instanceof SolrInfoMBean) {
      SolrInfoMBean mBean = (SolrInfoMBean) inst;
      String name = (inst instanceof SolrRequestHandler) ? pluginKey : mBean.getName();
      core.registerInfoBean(name, mBean);
    }
  }


  /**
   * Close this registry. This will in turn call a close on all the contained plugins
   */
  @Override
  public void close() {
    for (Map.Entry<String, PluginHolder<T>> e : registry.entrySet()) {
      try {
        e.getValue().close();
      } catch (Exception exp) {
        log.error("Error closing plugin " + e.getKey() + " of type : " + meta.tag, exp);
      }
    }
  }

  /**
   * An indirect reference to a plugin. It just wraps a plugin instance.
   * subclasses may choose to lazily load the plugin
   */
  public static class PluginHolder<T> implements AutoCloseable {
    protected T inst;
    protected final PluginInfo pluginInfo;

    public PluginHolder(PluginInfo info) {
      this.pluginInfo = info;
    }

    public PluginHolder(PluginInfo info, T inst) {
      this.inst = inst;
      this.pluginInfo = info;
    }

    public T get() {
      return inst;
    }

    public boolean isLoaded() {
      return inst != null;
    }

    @Override
    public void close() throws Exception {
      if (inst != null && inst instanceof AutoCloseable) ((AutoCloseable) inst).close();

    }
  }

  /**
   * A class that loads plugins Lazily. When the get() method is invoked
   * the Plugin is initialized and returned.
   */
  public static class LazyPluginHolder<T> extends PluginHolder<T> {
    private final SolrConfig.SolrPluginInfo pluginMeta;
    protected SolrException solrException;
    private final SolrCore core;
    protected ResourceLoader resourceLoader;


    LazyPluginHolder(SolrConfig.SolrPluginInfo pluginMeta, PluginInfo pluginInfo, SolrCore core, ResourceLoader loader) {
      super(pluginInfo);
      this.pluginMeta = pluginMeta;
      this.core = core;
      this.resourceLoader = loader;
      if (loader instanceof MemClassLoader) {
        if (!"true".equals(System.getProperty("enable.runtime.lib"))) {
          String s = "runtime library loading is not enabled, start Solr with -Denable.runtime.lib=true";
          log.warn(s);
          solrException = new SolrException(SolrException.ErrorCode.SERVER_ERROR, s);
        }
      }
    }

    @Override
    public T get() {
      if (inst != null) return inst;
      if (solrException != null) throw solrException;
      createInst();
      registerMBean(inst, core, pluginInfo.name);
      return inst;
    }

    protected synchronized void createInst() {
      if (inst != null) return;
      log.info("Going to create a new {} with {} ", pluginMeta.tag, pluginInfo.toString());
      if (resourceLoader instanceof MemClassLoader) {
        MemClassLoader loader = (MemClassLoader) resourceLoader;
        loader.loadJars();
      }
      Class<T> clazz = (Class<T>) pluginMeta.clazz;
      inst = core.createInstance(pluginInfo.className, clazz, pluginMeta.tag, null, resourceLoader);
      initInstance(inst, pluginInfo, core);
      if (inst instanceof SolrCoreAware) {
        SolrResourceLoader.assertAwareCompatibility(SolrCoreAware.class, inst);
        ((SolrCoreAware) inst).inform(core);
      }
      if (inst instanceof ResourceLoaderAware) {
        SolrResourceLoader.assertAwareCompatibility(ResourceLoaderAware.class, inst);
        try {
          ((ResourceLoaderAware) inst).inform(core.getResourceLoader());
        } catch (IOException e) {
          throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "error initializing component", e);
        }
      }
    }


  }

  /**
   * This represents a Runtime Jar. A jar requires two details , name and version
   */
  public static class RuntimeLib implements PluginInfoInitialized, AutoCloseable {
    String name;
    String version;
    private JarRepository.JarContentRef jarContent;
    private final JarRepository jarRepository;

    @Override
    public void init(PluginInfo info) {
      name = info.attributes.get("name");
      Object v = info.attributes.get("version");
      if (name == null || v == null) {
        throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "runtimeLib must have name and version");
      }
      version = String.valueOf(v);
    }

    public RuntimeLib(SolrCore core) {
      jarRepository = core.getCoreDescriptor().getCoreContainer().getJarRepository();
    }


    void loadJar() {
      if (jarContent != null) return;
      synchronized (this) {
        if (jarContent != null) return;
        jarContent = jarRepository.getJarIncRef(name + "/" + version);
      }
    }

    public ByteBuffer getFileContent(String entryName) throws IOException {
      if (jarContent == null)
        throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "jar not available: " + name + "/" + version);
      return jarContent.jar.getFileContent(entryName);

    }

    @Override
    public void close() throws Exception {
      if (jarContent != null) jarRepository.decrementJarRefCount(jarContent);
    }

    public static List<RuntimeLib> getLibObjects(SolrCore core, List<PluginInfo> libs) {
      List<RuntimeLib> l = new ArrayList<>(libs.size());
      for (PluginInfo lib : libs) {
        RuntimeLib rtl = new RuntimeLib(core);
        rtl.init(lib);
        l.add(rtl);
      }
      return l;
    }
  }
}
