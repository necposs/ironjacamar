/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat Middleware LLC, and individual contributors
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

package org.jboss.jca.core.workmanager.transport.remote.jgroups;

import org.jboss.jca.core.CoreBundle;
import org.jboss.jca.core.workmanager.DistributedWorkManagerImpl;
import org.jboss.jca.core.workmanager.transport.remote.AbstractRemoteTransport;
import org.jboss.jca.core.workmanager.transport.remote.ProtocolMessages.Request;
import org.jboss.jca.core.workmanager.transport.remote.ProtocolMessages.ResponseValues;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.resource.spi.work.DistributableWork;
import javax.resource.spi.work.WorkException;

import org.jboss.logging.Messages;

import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.MembershipListener;
import org.jgroups.View;
import org.jgroups.blocks.MethodCall;
import org.jgroups.blocks.MethodLookup;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.ResponseMode;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.util.Rsp;
import org.jgroups.util.RspList;

/**
 * The socket transport
 *
 * @author <a href="mailto:stefano.maestri@redhat.com">Stefano Maestri</a>
 */
public class JGroupsTransport extends AbstractRemoteTransport<Address> implements MembershipListener
{
   /** The bundle */
   private static CoreBundle bundle = Messages.getBundle(CoreBundle.class);

   /** The JChannel used by this transport **/
   private JChannel channel;

   /** the cluster name to join **/
   private String clusterName;

   private RpcDispatcher disp;

   private static final short JOIN_METHOD = 1;

   private static final short LEAVE_METHOD = 2;

   private static final short PING_METHOD = 3;

   private static final short DO_WORK_METHOD = 4;

   private static final short START_WORK_METHOD = 5;

   private static final short SCHEDULE_WORK_METHOD = 6;

   private static final short GET_SHORTRUNNING_FREE_METHOD = 7;

   private static final short GET_LONGRUNNING_FREE_METHOD = 8;

   private static final short UPDATE_SHORTRUNNING_FREE_METHOD = 9;

   private static final short UPDATE_LONGRUNNING_FREE_METHOD = 10;

   private static final short DELTA_DOWORK_ACCEPTED_METHOD = 11;

   private static final short DELTA_DOWORK_REJECTED_METHOD = 12;

   private static final short DELTA_STARTWORK_ACCEPTED_METHOD = 13;

   private static final short DELTA_STARTWORK_REJECTED_METHOD = 14;

   private static final short DELTA_SCHEDULEWORK_ACCEPTED_METHOD = 15;

   private static final short DELTA_SCHEDULEWORK_REJECTED_METHOD = 16;

   private static final short DELTA_WORK_SUCCESSFUL_METHOD = 17;

   private static final short DELTA_WORK_FAILED_METHOD = 18;

   private static Map<Short, Method> methods = new HashMap<Short, Method>();

   static
   {
      try
      {
         methods.put(JOIN_METHOD, JGroupsTransport.class.getMethod("join", String.class, Address.class));

         methods.put(LEAVE_METHOD, AbstractRemoteTransport.class.getMethod("leave", String.class));

         methods.put(PING_METHOD, AbstractRemoteTransport.class.getMethod("localPing"));

         methods.put(DO_WORK_METHOD, AbstractRemoteTransport.class.getMethod("localDoWork", DistributableWork.class));

         methods.put(START_WORK_METHOD,
            AbstractRemoteTransport.class.getMethod("localStartWork", DistributableWork.class));

         methods.put(SCHEDULE_WORK_METHOD,
            AbstractRemoteTransport.class.getMethod("localScheduleWork", DistributableWork.class));

         methods.put(GET_SHORTRUNNING_FREE_METHOD,
            AbstractRemoteTransport.class.getMethod("localGetShortRunningFree"));

         methods.put(GET_LONGRUNNING_FREE_METHOD, AbstractRemoteTransport.class.getMethod("localGetLongRunningFree"));

         methods.put(UPDATE_SHORTRUNNING_FREE_METHOD,
            AbstractRemoteTransport.class.getMethod("localUpdateShortRunningFree", String.class, Long.class));

         methods.put(UPDATE_LONGRUNNING_FREE_METHOD,
            AbstractRemoteTransport.class.getMethod("localUpdateLongRunningFree", String.class, Long.class));

         methods.put(DELTA_DOWORK_ACCEPTED_METHOD, AbstractRemoteTransport.class.getMethod("localDeltaDoWorkAccepted"));
         methods.put(DELTA_DOWORK_REJECTED_METHOD, AbstractRemoteTransport.class.getMethod("localDeltaDoWorkRejected"));

         methods.put(DELTA_STARTWORK_ACCEPTED_METHOD,
                     AbstractRemoteTransport.class.getMethod("localDeltaStartWorkAccepted"));
         methods.put(DELTA_STARTWORK_REJECTED_METHOD,
                     AbstractRemoteTransport.class.getMethod("localDeltaStartWorkRejected"));

         methods.put(DELTA_SCHEDULEWORK_ACCEPTED_METHOD,
                     AbstractRemoteTransport.class.getMethod("localDeltaScheduleWorkAccepted"));
         methods.put(DELTA_SCHEDULEWORK_REJECTED_METHOD,
                     AbstractRemoteTransport.class.getMethod("localDeltaScheduleWorkRejected"));

         methods.put(DELTA_WORK_SUCCESSFUL_METHOD, AbstractRemoteTransport.class.getMethod("localDeltaWorkSuccessful"));
         methods.put(DELTA_WORK_FAILED_METHOD, AbstractRemoteTransport.class.getMethod("localDeltaWorkFailed"));
      }
      catch (NoSuchMethodException e)
      {
         throw new RuntimeException(e);
      }
   }

   /**
    * Constructor
    */
   public JGroupsTransport()
   {
      super();
      this.channel = null;
      this.clusterName = null;
      this.disp = null;
   }

   @Override
   protected void init()
   {
      try
      {
         ((DistributedWorkManagerImpl) dwm).setId(channel.getAddressAsString());
         disp = new RpcDispatcher(channel, null, this, this);

         disp.setMethodLookup(new MethodLookup()
         {
            @Override
            public Method findMethod(short key)
            {
               return methods.get(key);
            }
         });

         channel.connect(clusterName);
      }
      catch (Throwable t)
      {
         log.errorf("Error during init: %s", t.getMessage(), t);
      }
   }

   /**
    * Delegator
    * @param id The id
    * @param address The address
    */
   public void join(String id, Address address)
   {
      super.join(id, address);
   }

   /**
    * Start method for bean lifecycle
    *
    * @throws Throwable in case of error
    */
   public void start() throws Throwable
   {
   }

   /**
    * Stop method for bean lifecycle
    *
    * @throws Throwable in case of error
    */
   public void stop() throws Throwable
   {
      //sendMessage(null, Request.LEAVE, dwm.getId());

      disp.stop();

      channel.close();
   }

   @Override
   public Long sendMessage(Address destAddress, Request request, Serializable... parameters)
      throws WorkException
   {
      Long returnValue = -1L;

      if (trace)
         log.tracef("%s: sending message2=%s to %s", channel.getAddressAsString(), request, destAddress);

      RequestOptions opts = new RequestOptions(ResponseMode.GET_ALL, 10000);
      try
      {
         switch (request)
         {
            case JOIN : {
               String id = (String) parameters[0];
               Address joiningAddress = (Address) parameters[1];
               List<Address> dests = destAddress == null ? null : Arrays.asList(destAddress);

               RspList<ResponseValues> rspList = disp.callRemoteMethods(dests, new MethodCall(JOIN_METHOD,
                                                                                              id,
                                                                                              joiningAddress),
                  opts);
               throwWorkExceptionIfHasExption(rspList);
               returnValue = 0L;
               break;
            }
            case LEAVE : {
               String id = (String) parameters[0];

               List<Address> dests = destAddress == null ? null : Arrays.asList(destAddress);

               RspList<ResponseValues> rspList = disp.callRemoteMethods(dests, new MethodCall(LEAVE_METHOD,
                                                                                              id), opts);
               throwWorkExceptionIfHasExption(rspList);
               returnValue = 0L;
               break;
            }
            case PING : {
               try
               {
                  returnValue = (Long) disp.callRemoteMethod(destAddress, new MethodCall(PING_METHOD),
                     opts);
               }
               catch (Exception e)
               {
                  throw new WorkException(e);
               }

               break;
            }
            case DO_WORK : {
               DistributableWork work = (DistributableWork) parameters[0];
               try
               {
                  disp.callRemoteMethod(destAddress, new MethodCall(DO_WORK_METHOD, work), opts);
                  returnValue = 0L;
               }
               catch (Exception e)
               {
                  throw new WorkException(e);
               }
               break;
            }
            case START_WORK : {
               DistributableWork work = (DistributableWork) parameters[0];

               returnValue = (Long) disp.callRemoteMethod(destAddress, new MethodCall(START_WORK_METHOD,
                                                                                      work), opts);

               break;
            }
            case SCHEDULE_WORK : {
               DistributableWork work = (DistributableWork) parameters[0];

               disp.callRemoteMethod(destAddress, new MethodCall(SCHEDULE_WORK_METHOD, work), opts);
               returnValue = 0L;

               break;
            }
            case GET_SHORTRUNNING_FREE : {
               returnValue = (Long) disp.callRemoteMethod(destAddress,
                  new MethodCall(GET_SHORTRUNNING_FREE_METHOD), opts);

               break;
            }
            case GET_LONGRUNNING_FREE : {
               returnValue = (Long) disp.callRemoteMethod(destAddress,
                  new MethodCall(GET_LONGRUNNING_FREE_METHOD),
                  opts);
               break;
            }
            case UPDATE_SHORTRUNNING_FREE : {
               String id = (String) parameters[0];
               Long freeCount = (Long) parameters[1];

               disp.callRemoteMethod(destAddress,
                  new MethodCall(UPDATE_SHORTRUNNING_FREE_METHOD, id, freeCount), opts);

               returnValue = 0L;

               break;
            }
            case UPDATE_LONGRUNNING_FREE : {
               String id = (String) parameters[0];
               Long freeCount = (Long) parameters[1];
               disp.callRemoteMethod(destAddress,
                  new MethodCall(UPDATE_LONGRUNNING_FREE_METHOD, id, freeCount), opts);
               break;
            }
            case DELTA_DOWORK_ACCEPTED : {
               disp.callRemoteMethod(destAddress, new MethodCall(DELTA_DOWORK_ACCEPTED_METHOD),
                                     opts);

               returnValue = 0L;

               break;
            }
            case DELTA_DOWORK_REJECTED : {
               disp.callRemoteMethod(destAddress, new MethodCall(DELTA_DOWORK_REJECTED_METHOD),
                                     opts);

               returnValue = 0L;

               break;
            }
            case DELTA_STARTWORK_ACCEPTED : {
               disp.callRemoteMethod(destAddress, new MethodCall(DELTA_STARTWORK_ACCEPTED_METHOD),
                                     opts);
               
               returnValue = 0L;

               break;
            }
            case DELTA_STARTWORK_REJECTED : {
               disp.callRemoteMethod(destAddress, new MethodCall(DELTA_STARTWORK_REJECTED_METHOD),
                                     opts);

               returnValue = 0L;

               break;
            }
            case DELTA_SCHEDULEWORK_ACCEPTED : {
               disp.callRemoteMethod(destAddress, new MethodCall(DELTA_SCHEDULEWORK_ACCEPTED_METHOD),
                                     opts);
               
               returnValue = 0L;

               break;
            }
            case DELTA_SCHEDULEWORK_REJECTED : {
               disp.callRemoteMethod(destAddress, new MethodCall(DELTA_SCHEDULEWORK_REJECTED_METHOD),
                                     opts);
               
               returnValue = 0L;

               break;
            }
            case DELTA_WORK_SUCCESSFUL : {
               disp.callRemoteMethod(destAddress, new MethodCall(DELTA_WORK_SUCCESSFUL_METHOD),
                                     opts);

               returnValue = 0L;

               break;
            }
            case DELTA_WORK_FAILED : {
               disp.callRemoteMethod(destAddress, new MethodCall(DELTA_WORK_FAILED_METHOD),
                                     opts);

               returnValue = 0L;

               break;
            }
            default :
               if (log.isDebugEnabled())
               {
                  log.debug("Unknown command received on socket Transport");
               }
               break;
         }
      }
      catch (Throwable t)
      {
         WorkException we = new WorkException(t.getMessage());
         we.initCause(t);
         throw we;
      }

      return returnValue;
   }

   private void throwWorkExceptionIfHasExption(RspList<ResponseValues> rspList) throws WorkException
   {
      if (rspList != null && rspList.getFirst() != null)
      {
         for (Rsp<ResponseValues> rsp : rspList)
         {
            if (rsp.hasException())
            {
               WorkException we = new WorkException(rsp.getException().getMessage());
               we.initCause(rsp.getException());
               throw we;
            }
         }
      }
   }


   @Override
   public String toString()
   {
      return "JGroupsTransport [channel=" + channel + ", clustername=" + clusterName + "]";
   }

   /**
    * Get the channel.
    *
    * @return the channel.
    */
   public JChannel getChannel()
   {
      return channel;
   }

   /**
    * Set the channel.
    *
    * @param channel The channel to set.
    */
   public void setChannel(JChannel channel)
   {
      this.channel = channel;
   }

   /**
    * Get the clustername.
    *
    * @return the clustername.
    */
   public String getClusterName()
   {
      return clusterName;
   }

   /**
    * Set the clustername.
    *
    * @param clustername The clustername to set.
    */
   public void setClusterName(String clustername)
   {
      this.clusterName = clustername;
   }

   @Override
   public void viewAccepted(View view)
   {
      if (trace)
      {
         log.tracef("java.net.preferIPv4Stack=%s", System.getProperty("java.net.preferIPv4Stack"));
         log.tracef("viewAccepted called w/ View=%s", view);
      }

      synchronized (this)
      {
         for (Entry<String, Address> entry : workManagers.entrySet())
         {
            if (!view.containsMember(entry.getValue()))
            {
               leave(entry.getKey());
            }
         }
         for (Address address : view.getMembers())
         {
            if (!workManagers.containsKey(address.toString()))
            {
               join(address.toString(), address);
               Long shortRunning = getShortRunningFree(address.toString());
               Long longRunning = getShortRunningFree(address.toString());

               localUpdateShortRunningFree(address.toString(), shortRunning);
               localUpdateLongRunningFree(address.toString(), longRunning);
            }
         }
      }
   }

   @Override
   public void block()
   {
      if (trace)
         log.tracef("block called");
   }

   @Override
   public void suspect(Address address)
   {
      if (trace)
         log.tracef("suspect called w/ Adress=%s", address);
   }

   @Override
   public void unblock()
   {
      if (trace)
         log.tracef("unblock called");
   }
}
