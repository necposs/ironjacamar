/*
 * IronJacamar, a Java EE Connector Architecture implementation
 * Copyright 2014, Red Hat Inc, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.jca.core.connectionmanager.ccm;

import org.jboss.jca.arquillian.embedded.Configuration;
import org.jboss.jca.arquillian.embedded.Inject;
import org.jboss.jca.core.api.connectionmanager.ccm.CachedConnectionManager;
import org.jboss.jca.core.tx.rars.txlog.TxLogConnection;
import org.jboss.jca.core.tx.rars.txlog.TxLogConnectionFactory;
import org.jboss.jca.core.tx.rars.txlog.TxLogConnectionFactoryImpl;
import org.jboss.jca.core.tx.rars.txlog.TxLogConnectionImpl;
import org.jboss.jca.core.tx.rars.txlog.TxLogManagedConnectionFactory;
import org.jboss.jca.embedded.dsl.resourceadapters11.api.ConnectionDefinitionsType;
import org.jboss.jca.embedded.dsl.resourceadapters11.api.ResourceAdapterType;
import org.jboss.jca.embedded.dsl.resourceadapters11.api.ResourceAdaptersDescriptor;

import java.util.HashSet;
import java.util.Set;

import jakarta.annotation.Resource;
import jakarta.transaction.UserTransaction;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.ResourceAdapterArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;
import org.jboss.shrinkwrap.descriptor.api.connector15.ConnectorDescriptor;
import org.jboss.shrinkwrap.descriptor.api.connector15.OutboundResourceadapterType;
import org.jboss.shrinkwrap.descriptor.api.connector15.ResourceadapterType;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Two connections test case for CCM (XATransaction/Interleaving)
 *
 * @author <a href="jesper.pedersen@ironjacamar.org">Jesper Pedersen</a>
 */
@RunWith(Arquillian.class)
@Configuration(autoActivate = false)
public class TwoConnectionsXAICCMTestCase
{
   private static Logger log = Logger.getLogger(TwoConnectionsXAICCMTestCase.class);

   /**
    * Create .rar
    * @return The resource adapter archive
    */
   @Deployment(order = 1)
   public static ResourceAdapterArchive createRar()
   {
      ConnectorDescriptor raXml = Descriptors.create(ConnectorDescriptor.class, "ra.xml")
         .version("1.5");
      ResourceadapterType rt = raXml.getOrCreateResourceadapter();
      OutboundResourceadapterType ort = rt.getOrCreateOutboundResourceadapter()
         .transactionSupport("XATransaction").reauthenticationSupport(false);
      org.jboss.shrinkwrap.descriptor.api.connector15.ConnectionDefinitionType cdt =
         ort.createConnectionDefinition()
            .managedconnectionfactoryClass(TxLogManagedConnectionFactory.class.getName())
            .connectionfactoryInterface(TxLogConnectionFactory.class.getName())
            .connectionfactoryImplClass(TxLogConnectionFactoryImpl.class.getName())
            .connectionInterface(TxLogConnection.class.getName())
            .connectionImplClass(TxLogConnectionImpl.class.getName());

      ResourceAdapterArchive raa =
         ShrinkWrap.create(ResourceAdapterArchive.class, "txlog.rar");
      
      JavaArchive ja = ShrinkWrap.create(JavaArchive.class, "txlog.jar");
      ja.addPackage(TxLogConnection.class.getPackage());
      
      raa.addAsLibrary(ja);
      raa.addAsManifestResource(new StringAsset(raXml.exportAsString()), "ra.xml");

      return raa;
   }

   /**
    * Create deployment
    * @return The resource adapter descriptor
    */
   @Deployment(order = 2)
   public static ResourceAdaptersDescriptor createDeployment()
   {
      ResourceAdaptersDescriptor dashRaXml = Descriptors.create(ResourceAdaptersDescriptor.class, "txlog-ra.xml");

      ResourceAdapterType dashRaXmlRt = dashRaXml.createResourceAdapter().archive("txlog.rar");
      dashRaXmlRt.transactionSupport("XATransaction");

      ConnectionDefinitionsType dashRaXmlCdst = dashRaXmlRt.getOrCreateConnectionDefinitions();
      org.jboss.jca.embedded.dsl.resourceadapters11.api.ConnectionDefinitionType dashRaXmlCdt =
         dashRaXmlCdst.createConnectionDefinition()
            .className(TxLogManagedConnectionFactory.class.getName())
            .jndiName("java:/eis/TxLogConnectionFactory").poolName("TxLog");

      org.jboss.jca.embedded.dsl.resourceadapters11.api.XaPoolType dashRaXmlPt = dashRaXmlCdt.getOrCreateXaPool()
         .minPoolSize(0).initialPoolSize(0).maxPoolSize(10).interleaving();

      org.jboss.jca.embedded.dsl.resourceadapters11.api.RecoverType dashRaXmlRyt = dashRaXmlCdt.getOrCreateRecovery()
         .noRecovery(Boolean.TRUE);

      return dashRaXml;
   }

   //-------------------------------------------------------------------------------------||
   // Tests ------------------------------------------------------------------------------||
   //-------------------------------------------------------------------------------------||

   @Resource(mappedName = "java:/eis/TxLogConnectionFactory")
   private TxLogConnectionFactory cf;
 
   @Inject(name = "CCM")
   private CachedConnectionManager ccm;
  
   @Inject(name = "UserTransaction")
   private UserTransaction ut;
  
   /**
    * Two connections
    * @exception Throwable Thrown if case of an error
    */
   @Test
   public void testTwoConnections() throws Throwable
   {
      assertNotNull(cf);
      assertNotNull(ccm);
      assertNotNull(ut);

      Set unsharableResources = new HashSet();
      Object layer = new Object();

      ccm.pushMetaAwareObject(layer, unsharableResources);

      TxLogConnection c1 = cf.getConnection();
      TxLogConnection c2 = cf.getConnection();

      assertFalse(c1.isInPool());
      assertFalse(c2.isInPool());

      assertEquals("", c1.getState());
      assertEquals("", c2.getState());

      log.infof("Before begin");

      ut.begin();

      log.infof("After begin");

      assertEquals("3", c1.getState());
      assertEquals("3", c2.getState());

      c1.close();
      c2.close();

      assertTrue(c1.isInPool());
      assertTrue(c2.isInPool());

      log.infof("Before commit");

      ut.commit();

      log.infof("After commit");

      assertEquals("3DB78", c1.getState());
      assertEquals("3DB78", c2.getState());

      assertTrue(c1.isInPool());
      assertTrue(c2.isInPool());

      ccm.popMetaAwareObject(unsharableResources);
   }
}
