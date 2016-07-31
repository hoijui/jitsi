package net.java.sip.communicator.plugin.omemo.elements;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.jabber.*;
import net.jomemo.elements.*;

/**
 *
 * @author hoijui
 */
public class OmemoContactImpl implements OmemoContact
{
    private final ContactJabberImpl internal;

    public OmemoContactImpl(final ContactJabberImpl internal)
    {
        this.internal = internal;
    }

    @Override
    public OmemoJid getJid()
    {
        return OmemoJid.fromString(internal.getAddress());
    }

}
