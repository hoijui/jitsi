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

import java.util.*;
import java.util.concurrent.*;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.*;

import org.osgi.framework.*;

/**
 * The OmemoContactManager is used for accessing <tt>OtrContact</tt>s in a static
 * way.
 *
 * The <tt>OmemoContact</tt> class is just a wrapper of [Contact, ContactResource]
 * pairs. Its purpose is for the otr plugin to be able to create different
 * <tt>Session</tt>s for every ContactResource that a Contact has.
 *
 * Currently, only the Jabber protocol supports ContactResources.
 *
 * @author Marin Dzhigarov
 *
 */
public class OmemoContactManager implements ServiceListener
{

    /**
     * The logger
     */
    private final Logger logger = Logger.getLogger(OmemoContactManager.class);

    /**
     * A map that caches OmemoContacts to minimize memory usage.
     */
    private static final Map<Contact, List<OmemoContact>> contactsMap =
        new ConcurrentHashMap<Contact, List<OmemoContact>>();

    /**
     * The <tt>OmemoContact</tt> class is just a wrapper of
     * [Contact, ContactResource] pairs. Its purpose is for the otr plugin to be
     * able to create different <tt>Session</tt>s for every ContactResource that
     * a Contact has.
     *
     * @author Marin Dzhigarov
     *
     */
    public static class OmemoContact
    {
        public final Contact contact;

        public final ContactResource resource;

        private OmemoContact(Contact contact, ContactResource resource)
        {
            this.contact = contact;
            this.resource = resource;
        }

        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;

            if (!(obj instanceof OmemoContact))
                return false;

            OmemoContact other = (OmemoContact) obj;

            if (this.contact != null && this.contact.equals(other.contact))
            {
                if (this.resource != null && resource.equals(other.resource))
                    return true;
                if (this.resource == null && other.resource == null)
                    return true;
                return false;
            }
            return false;
        }

        public int hashCode()
        {
            int result = 17;

            result = 31 * result + (contact == null ? 0 : contact.hashCode());
            result = 31 * result + (resource == null ? 0 : resource.hashCode());

            return result;
        }
    }

    /**
     * Gets the <tt>OmemoContact</tt> that represents this
     * [Contact, ContactResource] pair from the cache. If such pair does not
     * still exist it is then created and cached for further usage.
     *
     * @param contact the <tt>Contact</tt> that the returned OmemoContact
     *                  represents.
     * @param resource the <tt>ContactResource</tt> that the returned OmemoContact
     *                  represents.
     * @return The <tt>OmemoContact</tt> that represents this
     *                  [Contact, ContactResource] pair.
     */
    public static OmemoContact getOtrContact(
        Contact contact, ContactResource resource)
    {
        if (contact == null)
            return null;

        List<OmemoContact> otrContactsList = contactsMap.get(contact);
        if (otrContactsList != null)
        {
            for (OmemoContact otrContact : otrContactsList)
            {
                if (resource != null && resource.equals(otrContact.resource))
                    return otrContact;
            }
            OmemoContact otrContact = new OmemoContact(contact, resource);
            synchronized (otrContactsList)
            {
                while (!otrContactsList.contains(otrContact))
                    otrContactsList.add(otrContact);
            }
            return otrContact;
        }
        else
        {
            synchronized (contactsMap)
            {
                while (!contactsMap.containsKey(contact))
                {
                    otrContactsList = new ArrayList<OmemoContact>();
                    contactsMap.put(contact, otrContactsList);
                }
            }
            return getOmemoContact(contact, resource);
        }
    }

    /**
     * Cleans up unused cached up Contacts.
     */
    public void serviceChanged(ServiceEvent event)
    {
        Object service
            = OmemoActivator.bundleContext.getService(event.getServiceReference());

        if (!(service instanceof ProtocolProviderService))
            return;

        if (event.getType() == ServiceEvent.UNREGISTERING)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug(
                        "Unregistering a ProtocolProviderService, cleaning"
                            + " OTR's Contact to OmemoContact map");
            }

            ProtocolProviderService provider
                = (ProtocolProviderService) service;

            synchronized(contactsMap)
            {
                Iterator<Contact> i = contactsMap.keySet().iterator();

                while (i.hasNext())
                {
                    if (provider.equals(i.next().getProtocolProvider()))
                        i.remove();
                }
            }
        }
    }
}
