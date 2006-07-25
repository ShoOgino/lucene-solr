/** 
 * Copyright 2004 The Apache Software Foundation 
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License. 
 */
package org.apache.lucene.gdata.server.registry;

import java.lang.reflect.Constructor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.gdata.utils.Pool;
import org.apache.lucene.gdata.utils.PoolObjectFactory;
import org.apache.lucene.gdata.utils.SimpleObjectPool;

import com.google.gdata.data.ExtensionProfile;

/**
 * Standart implementation of
 * {@link org.apache.lucene.gdata.server.registry.ProvidedService} to be used
 * inside the
 * {@link org.apache.lucene.gdata.server.registry.GDataServerRegistry}
 * <p>
 * ExtensionProfiles are used to generate and parse xml by the gdata api. For
 * that case all methodes are synchronized. This will slow down the application
 * when performing lots of xml generation concurrently. For that case the
 * extensionProfile for a specific service will be pooled and reused.
 * </p>
 * 
 * 
 * @author Simon Willnauer
 * 
 */
@Scope(scope = Scope.ScopeType.REQUEST)
public class ProvidedServiceConfig implements ProvidedService, ScopeVisitor {
    private final static Log LOG = LogFactory
            .getLog(ProvidedServiceConfig.class);

    private static final int DEFAULT_POOL_SIZE = 5;

    /*
     * To ensure a extensionprofile instance will not be shared within multiple
     * threads each thread requesting a config will have one instance for the
     * entire request.
     */
    private final ThreadLocal<ExtensionProfile> extProfThreadLocal = new ThreadLocal<ExtensionProfile>();

    /*
     * ExtensionProfiles are used to generate and parse xml by the gdata api.
     * For that case all methodes are synchronized. This will slow down the
     * application when performing lots of xml generation concurrently. for that
     * case the extensionProfile for a specific service will be pooled and
     * reused.
     */
    private Pool<ExtensionProfile> profilPool;

    private String serviceName;

    private Class entryType;

    private Class feedType;

    private ExtensionProfile extensionProfile;

    private int poolSize = DEFAULT_POOL_SIZE;

    /**
     * @return Returns the poolSize.
     */
    public int getPoolSize() {
        return this.poolSize;
    }

    /**
     * @param poolSize
     *            The poolSize to set.
     */
    public void setPoolSize(int poolSize) {
        
        this.poolSize = poolSize >= DEFAULT_POOL_SIZE ? poolSize
                : DEFAULT_POOL_SIZE;
    }

    /**
     * Default constructor to instanciate via reflection
     */
    public ProvidedServiceConfig() {
        try {
            GDataServerRegistry.getRegistry().registerScopeVisitor(this);
        } catch (RegistryException e) {
            throw new RuntimeException("Can not register ScopeVisitor -- "
                    + e.getMessage(), e);
        }
    }

    /**
     * @see org.apache.lucene.gdata.server.registry.ProvidedService#getFeedType()
     */
    public Class getFeedType() {
        return this.feedType;
    }

    /**
     * @param feedType
     *            The feedType to set.
     */
    public void setFeedType(Class feedType) {
        this.feedType = feedType;
    }

    /**
     * @see org.apache.lucene.gdata.server.registry.ProvidedService#getExtensionProfile()
     */
    public ExtensionProfile getExtensionProfile() {
        ExtensionProfile ext = this.extProfThreadLocal.get();
        if (ext != null) {
            return ext;
        }
        if (this.profilPool == null)
            createProfilePool();
        ext = this.profilPool.aquire();
        this.extProfThreadLocal.set(ext);
        return ext;
    }

    /**
     * @param extensionProfil -
     *            the extensionprofile for this feed configuration
     */
    @SuppressWarnings("unchecked")
    public void setExtensionProfile(ExtensionProfile extensionProfil) {
        if (extensionProfil == null)
            throw new IllegalArgumentException(
                    "ExtensionProfile  must not be null");
        if (this.extensionProfile != null)
            return;
        this.extensionProfile = extensionProfil;

    }

    private void createProfilePool() {
        if (LOG.isInfoEnabled())
            LOG.info("Create ExtensionProfile pool with poolsize:"
                    + this.poolSize + " for service " + this.serviceName);
        this.profilPool = new SimpleObjectPool<ExtensionProfile>(this.poolSize,
                new ExtensionProfileFactory<ExtensionProfile>(
                        this.extensionProfile.getClass()));
    }

    /**
     * TODO add comment
     * 
     * @param <E>
     * @param extensionProfileClass
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    public <E extends ExtensionProfile> void setExtensionProfileClass(
            Class<E> extensionProfileClass) throws InstantiationException,
            IllegalAccessException {
        if (extensionProfileClass == null)
            throw new IllegalArgumentException(
                    "ExtensionProfile class must not be null");

        setExtensionProfile(extensionProfileClass.newInstance());

    }

    /**
     * @see org.apache.lucene.gdata.server.registry.ProvidedService#getEntryType()
     */
    public Class getEntryType() {
        return this.entryType;
    }

    /**
     * @param entryType
     */
    public void setEntryType(Class entryType) {
        this.entryType = entryType;
    }

    /**
     * @see org.apache.lucene.gdata.server.registry.ProvidedService#getName()
     */
    public String getName() {
        return this.serviceName;
    }

    /**
     * @param serviceName
     */
    public void setName(String serviceName) {
        this.serviceName = serviceName;
    }

    /**
     * @see org.apache.lucene.gdata.server.registry.ProvidedService#destroy()
     */
    public void destroy() {
        if (this.profilPool != null)
            this.profilPool.destroy();
        if (LOG.isInfoEnabled())
            LOG.info("Destroy Service " + this.serviceName
                    + " -- release all resources");
        this.feedType = null;
        this.entryType = null;
        this.extensionProfile = null;
    }

    private static class ExtensionProfileFactory<Type extends ExtensionProfile>
            implements PoolObjectFactory<Type> {
        private final Class<? extends ExtensionProfile> clazz;

        private final Constructor<? extends ExtensionProfile> constructor;

        private static final Object[] constArray = new Object[0];

        ExtensionProfileFactory(Class<? extends ExtensionProfile> clazz) {
            this.clazz = clazz;
            try {
                this.constructor = clazz.getConstructor(new Class[0]);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "The given class has no defaul constructor -- can not use as a ExtensionProfile -- "
                                + this.clazz.getName(), e);
            }
        }

        /**
         * @see org.apache.lucene.gdata.utils.PoolObjectFactory#getInstance()
         */
        @SuppressWarnings("unchecked")
        public Type getInstance() {

            try {
                return (Type) this.constructor.newInstance(constArray);
            } catch (Exception e) {
                throw new RuntimeException(
                        "Can not instanciate new ExtensionProfile -- ", e);

            }
        }

        /**
         * @param type -
         *            the ExtensionProfile to destroy
         * @see org.apache.lucene.gdata.utils.PoolObjectFactory#destroyInstance(Object)
         */
        public void destroyInstance(Type type) {
            //
        }

    }

    /**
     * @see org.apache.lucene.gdata.server.registry.ScopeVisitor#visiteInitialize()
     */
    public void visiteInitialize() {
        if(this.profilPool == null)
            createProfilePool();
        /*
         * don't set a extension profile for each thread. The current thread
         * might use another service and does not need the extensionprofile of
         * this service
         */
    }

    /**
     * @see org.apache.lucene.gdata.server.registry.ScopeVisitor#visiteDestroy()
     */
    public void visiteDestroy() {
        /*
         * Check every thread after request destroyed to release all profiles to
         * the pool
         */
        ExtensionProfile ext = this.extProfThreadLocal.get();
        if (ext == null) {
            if(LOG.isDebugEnabled())
            LOG.debug("ThreadLocal owns no ExtensionProfile in requestDestroy for service "
                            + this.serviceName);
            return;
        }
        this.extProfThreadLocal.set(null);
        this.profilPool.release(ext);
    }

}
