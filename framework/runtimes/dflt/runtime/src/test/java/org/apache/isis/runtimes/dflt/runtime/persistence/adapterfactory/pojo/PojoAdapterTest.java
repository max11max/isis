/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.isis.runtimes.dflt.runtime.persistence.adapterfactory.pojo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.Date;

import org.jmock.Expectations;
import org.jmock.auto.Mock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.apache.isis.applib.profiles.Localization;
import org.apache.isis.core.metamodel.adapter.ObjectAdapter;
import org.apache.isis.core.metamodel.adapter.ObjectAdapterLookup;
import org.apache.isis.core.metamodel.adapter.ResolveState;
import org.apache.isis.core.metamodel.adapter.oid.RootOidDefault;
import org.apache.isis.core.metamodel.adapter.version.Version;
import org.apache.isis.core.metamodel.spec.ObjectSpecId;
import org.apache.isis.core.metamodel.spec.SpecificationLoaderSpi;
import org.apache.isis.core.testsupport.jmock.JUnitRuleMockery2;
import org.apache.isis.core.testsupport.jmock.JUnitRuleMockery2.Mode;
import org.apache.isis.runtimes.dflt.runtime.persistence.ConcurrencyException;
import org.apache.isis.runtimes.dflt.runtime.persistence.adapter.PojoAdapter;
import org.apache.isis.runtimes.dflt.runtime.persistence.objectstore.transaction.PojoAdapterBuilder;

public class PojoAdapterTest {

    @Rule
    public JUnitRuleMockery2 context = JUnitRuleMockery2.createFor(Mode.INTERFACES_ONLY);
    
    private ObjectAdapter adapter;
    private RuntimeTestPojo domainObject;

    @Mock
    private Version mockVersion;
    @Mock
    private Version mockVersion2;
    @Mock
    private SpecificationLoaderSpi mockSpecificationLoader;
    @Mock
    private ObjectAdapterLookup mockObjectAdapterLookup;
    @Mock
    private Localization mockLocalization;
    
    @Before
    public void setUp() throws Exception {
        domainObject = new RuntimeTestPojo();
        
        adapter = new PojoAdapter(domainObject, RootOidDefault.create(ObjectSpecId.of("CUS"), "1"), mockSpecificationLoader, mockObjectAdapterLookup, mockLocalization);
        adapter.setVersion(mockVersion);
        
        allowUnimportantMethodCallsOn(mockVersion);
        allowUnimportantMethodCallsOn(mockVersion2);
    }

    private void allowUnimportantMethodCallsOn(final Version version) {
        context.checking(new Expectations() {
            {
                allowing(version).sequence();
                allowing(version).getUser();
                allowing(version).getTime();
                will(returnValue(new Date()));
            }
        });
    }

    @Test
    public void getOid_initially() {
        assertEquals(RootOidDefault.create(ObjectSpecId.of("CUS"), "1"), adapter.getOid());
    }

    @Test
    public void getObject_initially() {
        assertEquals(domainObject, adapter.getObject());
    }

    @Test
    public void getResolveState_initially() {
        assertEquals(ResolveState.NEW, adapter.getResolveState());
    }

    @Test
    public void changeState_newToTransient() {
        adapter.changeState(ResolveState.TRANSIENT);
        assertEquals(ResolveState.TRANSIENT, adapter.getResolveState());
    }

    @Test
    public void getVersion_initially() throws Exception {
        assertSame(mockVersion, adapter.getVersion());
    }

    @Test
    public void checkLock_whenVersionsSame() throws Exception {

        context.checking(new Expectations() {
            {
                one(mockVersion).different(mockVersion2);
                will(returnValue(false));
            }
        });
        
        adapter.checkLock(mockVersion2);
    }

    @Test(expected=ConcurrencyException.class)
    public void checkLock_whenVersionsDifferent() throws Exception {

        adapter = PojoAdapterBuilder.create().with(mockSpecificationLoader).withTitleString("some pojo").with(mockVersion).build();
        
        context.checking(new Expectations() {
            {
                one(mockVersion).different(mockVersion2);
                will(returnValue(true));
            }
        });
        
        adapter.checkLock(mockVersion2);
    }

}
