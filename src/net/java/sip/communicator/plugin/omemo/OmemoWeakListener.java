/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
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
package net.java.sip.communicator.plugin.omemo;

import java.lang.ref.*;

import net.java.sip.communicator.plugin.omemo.OmemoContactManager.OmemoContact;
import net.java.sip.communicator.service.protocol.*;

/**
 * Implements a <tt>ScOmemoEngineListener</tt> and
 * <tt>ScOmemoKeyManagerListener</tt> listener for the purposes of
 * <tt>OmemoContactMenu</tt> and <tt>OtrMetaContactButton</tt> which listen to
 * <tt>ScOmemoEngine</tt> and <tt>ScOmemoKeyManager</tt> while weakly referencing
 * them. Fixes a memory leak of <tt>OmemoContactMenu</tt> and
 * <tt>OmemoMetaContactButton</tt> instances because these cannot determine when
 * they are to be explicitly disposed.
 *
 * @author Lyubomir Marinov
 */
public class OmemoWeakListener
    <T extends ScOmemoEngineListener &
               ScOmemoKeyManagerListener>
    implements ScOmemoEngineListener,
               ScOmemoKeyManagerListener
{
    /**
     * The <tt>ScOmemoEngine</tt> the <tt>T</tt> associated with
     * this instance is to listen to.
     */
    private final ScOmemoEngine engine;

    /**
     * The <tt>ScOmemoKeyManager</tt> the <tt>T</tt> associated
     * with this instance is to listen to.
     */
    private final ScOmemoKeyManager keyManager;

    /**
     * The <tt>T</tt> which is associated with this instance
     * and which is to listen to {@link #engine} and {@link #keyManager}.
     */
    private final WeakReference<T> listener;

    /**
     * Initializes a new <tt>OmemoWeakListener</tt> instance which is to allow
     * a specific <tt>T</tt> to listener to a specific
     * <tt>ScOmemoEngine</tt> and a specific <tt>ScOmemoKeyManager</tt> without
     * being retained by them forever (because they live forever).
     *
     * @param listener the <tt>T</tt> which is to listen to the
     * specified <tt>engine</tt> and <tt>keyManager</tt>
     * @param engine the <tt>ScOmemoEngine</tt> which is to be listened to by
     * the specified <tt>T</tt>
     * @param keyManager the <tt>ScOmemoKeyManager</tt> which is to be
     * listened to by the specified <tt>T</tt>
     */
    public OmemoWeakListener(
            T listener,
            ScOmemoEngine engine, ScOmemoKeyManager keyManager)
    {
        if (listener == null)
            throw new NullPointerException("listener");

        this.listener = new WeakReference<T>(listener);
        this.engine = engine;
        this.keyManager = keyManager;

        this.engine.addListener(this);
        this.keyManager.addListener(this);
    }

    /**
     * {@inheritDoc}
     *
     * Forwards the event/notification to the associated
     * <tt>T</tt> if it is still needed by the application.
     */
    public void contactPolicyChanged(Contact contact)
    {
        ScOmemoEngineListener l = getListener();

        if (l != null)
            l.contactPolicyChanged(contact);
    }

    /**
     * {@inheritDoc}
     *
     * Forwards the event/notification to the associated
     * <tt>T</tt> if it is still needed by the application.
     */
    public void contactVerificationStatusChanged(OmemoContact contact)
    {
        ScOmemoKeyManagerListener l = getListener();

        if (l != null)
            l.contactVerificationStatusChanged(contact);
    }

    /**
     * Gets the <tt>T</tt> which is listening to {@link #engine}
     * and {@link #keyManager}. If the <tt>T</tt> is no longer needed by
     * the application, this instance seizes listening to <tt>engine</tt> and
     * <tt>keyManager</tt> and allows the memory used by this instance to be
     * reclaimed by the Java virtual machine.
     *
     * @return the <tt>T</tt> which is listening to
     * <tt>engine</tt> and <tt>keyManager</tt> if it is still needed by the
     * application; otherwise, <tt>null</tt>
     */
    private T getListener()
    {
        T l = this.listener.get();

        if (l == null)
        {
            engine.removeListener(this);
            keyManager.removeListener(this);
        }

        return l;
    }

    /**
     * {@inheritDoc}
     *
     * Forwards the event/notification to the associated
     * <tt>T</tt> if it is still needed by the application.
     */
    public void globalPolicyChanged()
    {
        ScOmemoEngineListener l = getListener();

        if (l != null)
            l.globalPolicyChanged();
    }

    /**
     * {@inheritDoc}
     *
     * Forwards the event/notification to the associated
     * <tt>T</tt> if it is still needed by the application.
     */
    public void sessionStatusChanged(OmemoContact contact)
    {
        ScOmemoEngineListener l = getListener();

        if (l != null)
            l.sessionStatusChanged(contact);
    }

    /**
     * Forwards the event/notification to the associated
     * <tt>T</tt> if it is still needed by the application.
     */
    public void multipleInstancesDetected(OmemoContact contact)
    {
        ScOmemoEngineListener l = getListener();

        if (l != null)
            l.multipleInstancesDetected(contact);
    }

    /**
     * Forwards the event/notification to the associated
     * <tt>T</tt> if it is still needed by the application.
     */
    public void outgoingSessionChanged(OmemoContact contact)
    {
        ScOmemoEngineListener l = getListener();

        if (l != null)
            l.outgoingSessionChanged(contact);
    }
}
